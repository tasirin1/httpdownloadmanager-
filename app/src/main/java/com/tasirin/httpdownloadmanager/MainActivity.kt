package com.tasirin.httpdownloadmanager

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.tasirin.httpdownloadmanager.data.DownloadItem
import com.tasirin.httpdownloadmanager.download.DownloadService
import com.tasirin.httpdownloadmanager.data.DownloadState
import com.tasirin.httpdownloadmanager.databinding.ActivityMainBinding
import com.tasirin.httpdownloadmanager.remote.HttpControlServer
import com.tasirin.httpdownloadmanager.ui.DownloadAdapter
import com.tasirin.httpdownloadmanager.util.MimeTypes
import com.tasirin.httpdownloadmanager.util.StoragePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity(), DownloadAdapter.Listener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: DownloadAdapter
    private var pendingMoveId: String? = null
    private var activeStorageCurrent: TextView? = null
    private var activeStorageInput: EditText? = null
    private var storagePathEdited = false
    private var updatingStorageInput = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* hasil izin tidak wajib untuk fungsi inti */ }

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                takePersistablePermission(uri)
                val name = DocumentFile.fromTreeUri(this, uri)?.name
                StoragePrefs.saveFolder(this, uri, name)
                StoragePrefs.setTextFolder(this, null)
                refreshActiveStorageUi()
                updateStorageInfo()
                Toast.makeText(
                    this,
                    getString(
                        R.string.storage_folder_selected,
                        name ?: getString(R.string.storage_custom_folder)
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure {
                Toast.makeText(
                    this,
                    getString(R.string.storage_picker_error, it.message ?: "?"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private val movePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val id = pendingMoveId
        pendingMoveId = null
        if (uri != null && id != null) {
            runCatching {
                takePersistablePermission(uri)
                App.engine.move(id, uri)
            }.onFailure {
                Snackbar.make(
                    binding.root,
                    R.string.storage_picker_error,
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        runCatching { installSplashScreen() }
        super.onCreate(savedInstanceState)
        showPreviousCrashIfAny()
        try {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        adapter = DownloadAdapter(this)
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.fabAdd.setOnClickListener { showAddDialog() }

        binding.btnChangeStorageHome.setOnClickListener { showStorageDialog() }

        binding.btnPauseAll.setOnClickListener {
            App.engine.pauseAll()
            Snackbar.make(binding.root, R.string.pause_all, Snackbar.LENGTH_SHORT).show()
        }
        binding.btnResumeAll.setOnClickListener {
            App.engine.resumeAll()
            Snackbar.make(binding.root, R.string.resume_all, Snackbar.LENGTH_SHORT).show()
        }
        binding.btnRetryFailed.setOnClickListener {
            App.engine.retryFailed()
            Snackbar.make(binding.root, R.string.retry_failed, Snackbar.LENGTH_SHORT).show()
        }

        lifecycleScope.launch {
            App.engine.items.collect { items ->
                runCatching {
                    val filtered = applyFilter(items)
                    adapter.submitList(filtered)
                    binding.emptyView.visibility =
                        if (filtered.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }

        setupFilterViews()

        requestPermissionsIfNeeded()
        runCatching {
            if (StoragePrefs.isBackgroundEnabled(this)) {
                App.engine.resumeInterrupted()
            }
        }
        if (StoragePrefs.isServerBackgroundEnabled(this) && !App.httpServer.isAlive) {
            runCatching { App.httpServer.startServer() }
        }
        runCatching { App.engine.cleanupOrphans() }
        if (!StoragePrefs.isStorageOnboarded(this)) {
            StoragePrefs.setStorageOnboarded(this)
            showStorageOnboardingDialog()
        }
        if (StoragePrefs.isBatteryExemptEnabled(this)) {
            requestBatteryExemption()
        }
        handleIncomingIntent(intent)
        } catch (t: Throwable) {
            showFatalError(t)
        }
    }

    private fun showPreviousCrashIfAny() {
        runCatching {
            val file = File(filesDir, App.CRASH_LOG_FILE)
            if (!file.exists()) return@runCatching
            val text = file.readText().trim()
            if (text.isEmpty()) return@runCatching
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.previous_crash_title)
                .setMessage(text.take(3000))
                .setPositiveButton(R.string.clear_log) { _, _ -> file.delete() }
                .setNegativeButton(R.string.close, null)
                .show()
        }
    }

    private fun showFatalError(t: Throwable) {
        App.appendCrash(this, "onCreate", t)
        val stack = Log.getStackTraceString(t)
        runCatching {
            val tv = TextView(this)
            tv.setTextIsSelectable(true)
            tv.text = getString(R.string.fatal_error_message) + "\n\n" + stack
            tv.setPadding(24, 24, 24, 24)
            setContentView(tv)
        }
        runCatching {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.fatal_error_title)
                .setMessage(stack)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateServerStatus()
        updateStorageInfo()
    }

    private fun updateStorageInfo() {
        val tv = findViewById<TextView>(R.id.storage_info) ?: return
        val folder = StoragePrefs.getFolderName(this)
            ?: StoragePrefs.getTextFolder(this)
            ?: getString(R.string.storage_default_folder)
        tv.text = getString(
            R.string.storage_home_info,
            folder,
            formatBytes(App.engine.freeSpaceBytes())
        )
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.2f GB".format(mb / 1024.0)
    }

    private fun updateServerStatus() {
        val tv = findViewById<TextView>(R.id.server_status) ?: return
        val alive = App.httpServer.isAlive
        tv.text = getString(
            if (alive) R.string.server_status_running else R.string.server_status_stopped
        )
        tv.setTextColor(
            ContextCompat.getColor(this, if (alive) R.color.status_on else R.color.status_off)
        )
        val urlTv = findViewById<TextView>(R.id.server_url)
        val qrIv = findViewById<ImageView>(R.id.qr_preview)
        if (urlTv != null && qrIv != null) {
            if (alive) {
                val urls = HttpControlServer.ipv4Addresses()
                    .map { "http://$it:${App.httpServer.listeningPort}/" }
                if (urls.isNotEmpty()) {
                    urlTv.text = urls.joinToString("  ")
                    urlTv.visibility = View.VISIBLE
                    qrIv.visibility = View.VISIBLE
                    qrIv.setImageBitmap(generateQrCode(urls[0], 256))
                    qrIv.setOnClickListener { showRemoteDialog() }
                } else {
                    urlTv.visibility = View.GONE
                    qrIv.visibility = View.GONE
                }
            } else {
                urlTv.visibility = View.GONE
                qrIv.visibility = View.GONE
            }
        }
    }

    /** Stop DownloadService bila tidak ada download aktif (server juga mati). */
    private fun stopServiceIfIdle() {
        val anyActive = App.engine.items.value.any {
            it.state == DownloadState.DOWNLOADING || it.state == DownloadState.PENDING
        }
        if (!anyActive) {
            runCatching { stopService(Intent(this, DownloadService::class.java)) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_ADD_DOWNLOAD, false) == true) {
            showAddDialog()
            return
        }
        if (intent == null) return
        val raw = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.data?.toString()
            else -> null
        } ?: return
        val url = raw.trim().split(Regex("\\s+")).firstOrNull {
            it.startsWith("http://") || it.startsWith("https://")
        } ?: return
        showAddDialog(url)
    }

    private fun showAddDialog(prefillUrl: String? = null) {
        val view = layoutInflater.inflate(R.layout.dialog_add_download, null)
        val urlInput = view.findViewById<EditText>(R.id.input_url)
        val nameInput = view.findViewById<EditText>(R.id.input_name)
        val usernameInput = view.findViewById<EditText>(R.id.input_username)
        val passwordInput = view.findViewById<EditText>(R.id.input_password)
        val headersInput = view.findViewById<EditText>(R.id.input_headers)
        val checksumInput = view.findViewById<EditText>(R.id.input_checksum)
        val speedPerOptions = resources.getStringArray(R.array.speed_limit_per_options)
        val speedKbps = intArrayOf(0, 128, 256, 512, 1024, 2048, 5120)
        val spinnerSpeedPer = view.findViewById<Spinner>(R.id.spinner_speed_limit_per)
        spinnerSpeedPer.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, speedPerOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val priorityValues = intArrayOf(-1, 0, 1)
        val spinnerPriority = view.findViewById<Spinner>(R.id.spinner_priority)
        spinnerPriority.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            resources.getStringArray(R.array.priority_options)
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerPriority.setSelection(1)
        val recentTitle = view.findViewById<TextView>(R.id.recent_title)
        val recentScroll = view.findViewById<View>(R.id.recent_scroll)
        val recentSearch = view.findViewById<EditText>(R.id.input_recent_search)
        val recentContainer = view.findViewById<LinearLayout>(R.id.recent_container)
        val clearHistory = view.findViewById<TextView>(R.id.clear_history)

        if (!prefillUrl.isNullOrBlank()) {
            urlInput.setText(prefillUrl)
        }

        fun renderRecents(query: String) {
            recentContainer.removeAllViews()
            val density = resources.displayMetrics.density
            val q = query.trim().lowercase()
            val recents = StoragePrefs.recentUrls(this)
                .filter { q.isEmpty() || it.lowercase().contains(q) }
                .take(10)
            recents.forEach { u ->
                val tv = TextView(this)
                tv.text = u
                tv.maxLines = 1
                tv.ellipsize = TextUtils.TruncateAt.MIDDLE
                tv.setTextColor(ContextCompat.getColor(this, R.color.primary))
                tv.setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
                tv.setOnClickListener {
                    urlInput.setText(u)
                    urlInput.setSelection(urlInput.text?.length ?: 0)
                }
                recentContainer.addView(tv)
            }
        }

        if (StoragePrefs.recentUrls(this).isNotEmpty()) {
            recentTitle.visibility = View.VISIBLE
            recentScroll.visibility = View.VISIBLE
            recentSearch.visibility = View.VISIBLE
            clearHistory.visibility = View.VISIBLE
            renderRecents("")
            recentSearch.addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                        renderRecents(s?.toString().orEmpty())
                    }
                    override fun afterTextChanged(s: Editable?) {}
                }
            )
            clearHistory.setOnClickListener {
                StoragePrefs.clearRecentUrls(this)
                recentTitle.visibility = View.GONE
                recentScroll.visibility = View.GONE
                recentSearch.visibility = View.GONE
                clearHistory.visibility = View.GONE
                recentContainer.removeAllViews()
            }
        }

        val storageText = view.findViewById<TextView>(R.id.text_storage_remaining)
        storageText.text = getString(R.string.storage_remaining, formatBytes(App.engine.freeSpaceBytes()))

        fun addAll(
            urls: List<String>,
            name: String,
            username: String,
            password: String,
            headers: String,
            perSpeed: Int,
            priority: Int,
            checksum: String
        ) {
            urls.forEachIndexed { index, url ->
                App.engine.addDownload(
                    url,
                    if (index == 0) name else null,
                    username,
                    password,
                    headers,
                    perSpeed,
                    priority,
                    if (index == 0) checksum else ""
                )
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_download)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.download) { _, _ ->
                val urls = urlInput.text?.toString()?.trim().orEmpty()
                    .split(Regex("[\\s,]+"))
                    .filter { it.startsWith("http://") || it.startsWith("https://") }
                if (urls.isEmpty()) {
                    Snackbar.make(binding.root, R.string.invalid_url, Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val name = nameInput.text?.toString()?.trim().orEmpty()
                val username = usernameInput.text?.toString()?.trim().orEmpty()
                val password = passwordInput.text?.toString()?.trim().orEmpty()
                val headers = headersInput.text?.toString()?.trim().orEmpty()
                val checksum = checksumInput.text?.toString()?.trim().orEmpty()
                val perSpeed = speedKbps[spinnerSpeedPer.selectedItemPosition]
                val priority = priorityValues[spinnerPriority.selectedItemPosition]
                if (urls.size == 1 && urls[0].contains("m3u8", ignoreCase = true)) {
                    lifecycleScope.launch {
                        val variants = withContext(Dispatchers.IO) {
                            runCatching { App.engine.probeHlsVariants(urls[0]) }.getOrNull()
                        }
                        if (variants.isNullOrEmpty()) {
                            addAll(urls, name, username, password, headers, perSpeed, priority, checksum)
                        } else {
                            showHlsPicker(
                                variants = variants,
                                originalUrl = urls[0],
                                name = name,
                                username = username,
                                password = password,
                                headers = headers,
                                perSpeed = perSpeed,
                                priority = priority,
                                checksum = checksum
                            )
                        }
                    }
                } else {
                    addAll(urls, name, username, password, headers, perSpeed, priority, checksum)
                }
            }
            .show()
    }

    private fun showHlsPicker(
        variants: List<com.tasirin.httpdownloadmanager.download.HlsVariant>,
        originalUrl: String,
        name: String,
        username: String,
        password: String,
        headers: String,
        perSpeed: Int,
        priority: Int,
        checksum: String
    ) {
        val labels = variants.map { it.name } + getString(R.string.hls_direct)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.hls_quality_title)
            .setItems(labels.toTypedArray()) { _, which ->
                val target = if (which < variants.size) variants[which].url else originalUrl
                val chosenName = if (which < variants.size) {
                    variants[which].name.replace(' ', '_') + ".m3u8"
                } else {
                    name
                }
                App.engine.addDownload(
                    target,
                    chosenName,
                    username,
                    password,
                    headers,
                    perSpeed,
                    priority,
                    checksum
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_autostart)?.isChecked =
            StoragePrefs.isAutoStartEnabled(this)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_completed -> {
                App.engine.clearCompleted()
                true
            }
            R.id.action_storage -> {
                showStorageDialog()
                true
            }
            R.id.action_remote -> {
                showRemoteDialog()
                true
            }
            R.id.action_gallery -> {
                startActivity(Intent(this, GalleryActivity::class.java))
                true
            }
            R.id.action_settings -> {
                showSettingsDialog()
                true
            }
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            R.id.action_autostart -> {
                val enabled = !StoragePrefs.isAutoStartEnabled(this)
                StoragePrefs.setAutoStartEnabled(this, enabled)
                Snackbar.make(
                    binding.root,
                    if (enabled) R.string.autostart_on else R.string.autostart_off,
                    Snackbar.LENGTH_SHORT
                ).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onAction(item: DownloadItem, action: DownloadAdapter.Action) {
        when (action) {
            DownloadAdapter.Action.PAUSE -> App.engine.pause(item.id)
            DownloadAdapter.Action.RESUME -> App.engine.resume(item.id)
            DownloadAdapter.Action.CANCEL -> App.engine.cancel(item.id)
            DownloadAdapter.Action.DELETE -> App.engine.remove(item.id)
            DownloadAdapter.Action.OPEN -> openDownload(item)
            DownloadAdapter.Action.OPEN_FOLDER -> openFolder(item)
        }
    }

    override fun onLongPress(item: DownloadItem) {
        val options = mutableListOf<Pair<String, () -> Unit>>()
        if (item.state == DownloadState.COMPLETED) {
            options.add(getString(R.string.open) to { openDownload(item) })
            options.add(getString(R.string.open_folder) to { openFolder(item) })
            options.add(getString(R.string.action_rename) to { showRenameDialog(item) })
            options.add(getString(R.string.action_move) to {
                pendingMoveId = item.id
                launchDocumentTree(movePicker)
            })
        }
        if (item.state == DownloadState.PENDING ||
            item.state == DownloadState.PAUSED ||
            item.state == DownloadState.FAILED
        ) {
            options.add(
                getString(R.string.action_limit_priority) to {
                    showLimitPriorityDialog(item)
                }
            )
        }
        options.add(getString(R.string.delete) to { App.engine.remove(item.id) })
        val labels = options.map { it.first }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(item.fileName)
            .setItems(labels) { _, which -> options[which].second.invoke() }
            .show()
    }

    private fun showRenameDialog(item: DownloadItem) {
        val input = EditText(this)
        input.setText(item.fileName)
        input.setSelection(input.text.length)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_rename)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = input.text?.toString()?.trim().orEmpty()
                if (newName.isNotEmpty() && newName != item.fileName) {
                    App.engine.rename(item.id, newName)
                }
            }
            .show()
    }

    private fun takePersistablePermission(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    private fun launchDocumentTree(launcher: ActivityResultLauncher<Uri?>) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
        }
        if (intent.resolveActivity(packageManager) == null) {
            Toast.makeText(this, R.string.storage_picker_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        launcher.launch(downloadsInitialUri())
    }

    private fun downloadsInitialUri(): Uri? {
        if (Build.VERSION.SDK_INT < 26) return null
        return runCatching {
            DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents", "primary:Download"
            )
        }.getOrNull()
    }

    private fun currentStorageLabel(): String {
        val name = StoragePrefs.getFolderName(this)
            ?: StoragePrefs.getTextFolder(this)
            ?: getString(R.string.storage_default_folder)
        return getString(R.string.storage_current, name)
    }

    private fun wireStorageSection(view: View) {
        val currentStorage = view.findViewById<TextView>(R.id.current_storage)
        val pathInput = view.findViewById<EditText>(R.id.input_storage_path)
        activeStorageCurrent = currentStorage
        activeStorageInput = pathInput
        storagePathEdited = false
        currentStorage.text = currentStorageLabel()
        pathInput.setText(StoragePrefs.getTextFolder(this) ?: defaultDownloadsPath())
        pathInput.setSelection(pathInput.text.length)
        pathInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!updatingStorageInput) storagePathEdited = true
            }
        })

        view.findViewById<Button>(R.id.btn_pick_storage).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 23) {
                requestPermissionsIfNeeded()
            }
            launchDocumentTree(folderPicker)
        }
        view.findViewById<Button>(R.id.btn_reset_storage).setOnClickListener {
            StoragePrefs.saveFolder(this, null, null)
            StoragePrefs.setTextFolder(this, null)
            refreshActiveStorageUi()
            updateStorageInfo()
            Toast.makeText(this, R.string.storage_default_folder, Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshActiveStorageUi() {
        val input = activeStorageInput
        activeStorageCurrent?.text = currentStorageLabel()
        storagePathEdited = false
        if (input != null) {
            updatingStorageInput = true
            input.setText(StoragePrefs.getTextFolder(this) ?: defaultDownloadsPath())
            input.setSelection(input.text.length)
            updatingStorageInput = false
        }
    }

    private fun clearActiveStorageUi() {
        activeStorageCurrent = null
        activeStorageInput = null
        storagePathEdited = false
        updatingStorageInput = false
    }

    private fun applyStoragePath(pathInput: EditText) {
        if (!storagePathEdited) return
        val path = pathInput.text?.toString()?.trim().orEmpty()
        if (path.isEmpty()) return
        val dir = File(path)
        if (!dir.isDirectory && !dir.mkdirs()) {
            Toast.makeText(this, R.string.storage_text_folder_invalid, Toast.LENGTH_LONG).show()
            return
        }
        StoragePrefs.setTextFolder(this, path)
        StoragePrefs.saveFolder(this, null, null)
        refreshActiveStorageUi()
        updateStorageInfo()
        Toast.makeText(
            this,
            getString(R.string.storage_text_folder_saved, path),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showStorageDialog() {
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionsIfNeeded()
        }
        val view = layoutInflater.inflate(R.layout.dialog_storage, null)
        wireStorageSection(view)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_storage)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                applyStoragePath(view.findViewById(R.id.input_storage_path))
            }
            .show()
        dialog.setOnDismissListener { clearActiveStorageUi() }
    }

    private fun showTextFolderDialog() {
        val input = EditText(this)
        input.hint = getString(R.string.storage_text_folder_hint)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.setText(StoragePrefs.getTextFolder(this) ?: defaultDownloadsPath())
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.storage_text_folder)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val path = input.text?.toString()?.trim().orEmpty()
                val dir = File(path)
                if (path.isNotEmpty() && (dir.isDirectory || dir.mkdirs())) {
                    StoragePrefs.setTextFolder(this, path)
                    StoragePrefs.saveFolder(this, null, null)
                    Toast.makeText(
                        this,
                        getString(R.string.storage_text_folder_saved, path),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(this, R.string.storage_text_folder_invalid, Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    private fun defaultDownloadsPath(): String {
        if (Build.VERSION.SDK_INT >= 29) return "/storage/emulated/0/Download"
        return runCatching {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .absolutePath
        }.getOrDefault("/storage/emulated/0/Download")
    }

    private fun showStorageOnboardingDialog() {
        if (StoragePrefs.getFolderUri(this) != null ||
            StoragePrefs.getTextFolder(this) != null
        ) {
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.storage_onboarding_title)
            .setMessage(R.string.storage_onboarding_message)
            .setItems(
                arrayOf(
                    getString(R.string.storage_onboarding_default),
                    getString(R.string.storage_onboarding_custom),
                    getString(R.string.storage_onboarding_text)
                )
            ) { _, which ->
                when (which) {
                    0 -> {
                        StoragePrefs.saveFolder(this, null, null)
                        StoragePrefs.setTextFolder(this, null)
                    }
                    1 -> launchDocumentTree(folderPicker)
                    2 -> showTextFolderDialog()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun requestBatteryExemption() {
        if (Build.VERSION.SDK_INT < 23) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        }.onFailure {
            Snackbar.make(
                binding.root,
                R.string.battery_request_failed,
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun showSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        val checkBackground = view.findViewById<CheckBox>(R.id.check_background)
        val checkAutoStart = view.findViewById<CheckBox>(R.id.check_autostart)
        val checkServerBackground = view.findViewById<CheckBox>(R.id.check_server_background)
        val checkServerAutostart = view.findViewById<CheckBox>(R.id.check_server_autostart)
        val checkBattery = view.findViewById<CheckBox>(R.id.check_battery)
        val checkAutoSort = view.findViewById<CheckBox>(R.id.check_auto_sort)
        val pinInput = view.findViewById<EditText>(R.id.input_pin)
        wireStorageSection(view)
        checkBackground.isChecked = StoragePrefs.isBackgroundEnabled(this)
        checkAutoStart.isChecked = StoragePrefs.isAutoStartEnabled(this)
        checkServerBackground.isChecked = StoragePrefs.isServerBackgroundEnabled(this)
        checkServerAutostart.isChecked = StoragePrefs.isServerAutoStartEnabled(this)
        checkBattery.isChecked = StoragePrefs.isBatteryExemptEnabled(this)
        checkAutoSort.isChecked = StoragePrefs.isAutoSortEnabled(this)
        pinInput.setText(StoragePrefs.getServerPin(this).orEmpty())

        val concurrentOptions = resources.getStringArray(R.array.concurrent_options)
        val spinnerConcurrent = view.findViewById<Spinner>(R.id.spinner_concurrent)
        spinnerConcurrent.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, concurrentOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerConcurrent.setSelection(
            (StoragePrefs.maxConcurrent(this) - 1).coerceIn(0, concurrentOptions.size - 1)
        )

        val speedOptions = resources.getStringArray(R.array.speed_limit_options)
        val speedKbps = intArrayOf(0, 128, 256, 512, 1024, 2048, 5120)
        val spinnerSpeed = view.findViewById<Spinner>(R.id.spinner_speed)
        spinnerSpeed.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, speedOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerSpeed.setSelection(
            speedKbps.indexOf(StoragePrefs.speedLimitKbps(this)).coerceAtLeast(0)
        )

        val retryOptions = resources.getStringArray(R.array.retry_options)
        val spinnerRetry = view.findViewById<Spinner>(R.id.spinner_retry)
        spinnerRetry.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, retryOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerRetry.setSelection(
            StoragePrefs.maxRetries(this).coerceIn(0, retryOptions.size - 1)
        )

        val portInput = view.findViewById<EditText>(R.id.input_port)
        portInput.setText(StoragePrefs.serverPort(this).toString())

        val segmentOptions = resources.getStringArray(R.array.segment_options)
        val segmentValues = intArrayOf(1, 2, 4, 6, 8)
        val spinnerSegments = view.findViewById<Spinner>(R.id.spinner_segments)
        spinnerSegments.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, segmentOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerSegments.setSelection(
            segmentValues.indexOf(StoragePrefs.segmentCount(this)).coerceAtLeast(0)
        )

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_settings)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                applyStoragePath(view.findViewById(R.id.input_storage_path))
                StoragePrefs.setBackgroundEnabled(this, checkBackground.isChecked)
                StoragePrefs.setAutoStartEnabled(this, checkAutoStart.isChecked)
                StoragePrefs.setServerBackgroundEnabled(this, checkServerBackground.isChecked)
                StoragePrefs.setServerAutoStartEnabled(this, checkServerAutostart.isChecked)
                StoragePrefs.setBatteryExemptEnabled(this, checkBattery.isChecked)
                StoragePrefs.setAutoSortEnabled(this, checkAutoSort.isChecked)
                StoragePrefs.setServerPin(
                    this,
                    pinInput.text?.toString()?.trim().orEmpty()
                )
                // Sinkronkan server: mati otomatis bila "latar belakang" dimatikan
                val wantServer = checkServerBackground.isChecked
                if (wantServer && !App.httpServer.isAlive) {
                    runCatching { App.httpServer.startServer() }
                } else if (!wantServer && App.httpServer.isAlive) {
                    App.httpServer.stopServer()
                    stopServiceIfIdle()
                }
                if (checkBattery.isChecked) {
                    requestBatteryExemption()
                }
                StoragePrefs.setMaxConcurrent(this, spinnerConcurrent.selectedItemPosition + 1)
                StoragePrefs.setSpeedLimitKbps(this, speedKbps[spinnerSpeed.selectedItemPosition])
                StoragePrefs.setMaxRetries(this, spinnerRetry.selectedItemPosition)
                StoragePrefs.setSegmentCount(
                    this,
                    segmentValues[spinnerSegments.selectedItemPosition]
                )
                val newPort = portInput.text?.toString()?.trim()?.toIntOrNull()
                if (newPort == null || newPort !in 1024..65535) {
                    Toast.makeText(
                        this, R.string.settings_port_invalid, Toast.LENGTH_LONG
                    ).show()
                } else {
                    val oldPort = App.httpServer.listeningPort
                    StoragePrefs.setServerPort(this, newPort)
                    if (newPort != oldPort) {
                        // NanoHTTPD mengunci port saat konstruksi, jadi server
                        // dibuat ulang agar port baru benar-benar terpakai.
                        App.restartHttpServer(this)
                    }
                }
                updateServerStatus()
            }
            .show()
        dialog.setOnDismissListener { clearActiveStorageUi() }
    }

    private fun showRemoteDialog() {
        val server = App.httpServer
        val view = layoutInflater.inflate(R.layout.dialog_remote, null)
        val statusView = view.findViewById<TextView>(R.id.remote_status)
        val urlsView = view.findViewById<TextView>(R.id.remote_urls)
        val qrView = view.findViewById<ImageView>(R.id.remote_qr)
        val switchView = view.findViewById<SwitchCompat>(R.id.remote_switch) ?: return

        fun renderRemote() {
            if (App.httpServer.isAlive) {
                statusView.setText(R.string.remote_running)
                val urls = HttpControlServer.ipv4Addresses()
                    .map { "http://$it:${App.httpServer.listeningPort}/" }
                urlsView.text = urls.joinToString("\n").ifEmpty {
                    getString(R.string.remote_no_url)
                }
                urls.firstOrNull()?.let { address ->
                    generateQrCode(address, 640)?.let { qrView.setImageBitmap(it) }
                }
                qrView.visibility = View.VISIBLE
            } else {
                statusView.setText(R.string.remote_stopped)
                urlsView.text = getString(R.string.remote_no_url)
                qrView.visibility = View.GONE
            }
        }

        renderRemote()
        switchView.isChecked = server.isAlive
        var updating = false
        switchView.setOnCheckedChangeListener { _, checked ->
            if (updating) return@setOnCheckedChangeListener
            if (checked) {
                // Nyalakan: simpan preferensi supaya tetap hidup setelah restart.
                StoragePrefs.setServerBackgroundEnabled(this, true)
                StoragePrefs.setServerAutoStartEnabled(this, true)
                val result = runCatching { App.httpServer.startServer() }
                if (result.isFailure) {
                    StoragePrefs.setServerBackgroundEnabled(this, false)
                    StoragePrefs.setServerAutoStartEnabled(this, false)
                    updating = true
                    switchView.isChecked = false
                    updating = false
                    Snackbar.make(
                        binding.root,
                        getString(
                            R.string.remote_start_failed,
                            App.httpServer.lastError ?: result.exceptionOrNull()?.message ?: "?"
                        ),
                        Snackbar.LENGTH_LONG
                    ).show()
                } else {
                    Snackbar.make(
                        binding.root,
                        getString(R.string.remote_started, App.httpServer.listeningPort),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            } else {
                StoragePrefs.setServerBackgroundEnabled(this, false)
                StoragePrefs.setServerAutoStartEnabled(this, false)
                App.httpServer.stopServer()
                stopServiceIfIdle()
                Snackbar.make(binding.root, R.string.remote_stopped, Snackbar.LENGTH_SHORT).show()
            }
            updateServerStatus()
            renderRemote()
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_remote)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun generateQrCode(content: String, size: Int): Bitmap? {
        return runCatching {
            val hints = mapOf(EncodeHintType.MARGIN to 1)
            val matrix = QRCodeWriter().encode(
                content, BarcodeFormat.QR_CODE, size, size, hints
            )
            val pixels = IntArray(size * size)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    pixels[y * size + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }

    private fun showLimitPriorityDialog(item: DownloadItem) {
        val view = layoutInflater.inflate(R.layout.dialog_limit_priority, null)
        val speedPerOptions = resources.getStringArray(R.array.speed_limit_per_options)
        val speedKbps = intArrayOf(0, 128, 256, 512, 1024, 2048, 5120)
        val spinnerSpeed = view.findViewById<Spinner>(R.id.spinner_speed_limit_per)
        spinnerSpeed.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, speedPerOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val speedIndex = speedKbps.indexOf(item.speedLimitKbps)
        spinnerSpeed.setSelection(if (speedIndex >= 0) speedIndex else 0)

        val priorityValues = intArrayOf(-1, 0, 1)
        val spinnerPriority = view.findViewById<Spinner>(R.id.spinner_priority)
        spinnerPriority.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            resources.getStringArray(R.array.priority_options)
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerPriority.setSelection(
            priorityValues.indexOf(item.priority).coerceAtLeast(0)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(item.fileName)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                App.engine.setLimitAndPriority(
                    item.id,
                    speedKbps[spinnerSpeed.selectedItemPosition],
                    priorityValues[spinnerPriority.selectedItemPosition]
                )
            }
            .show()
    }

    private enum class DownloadFilter { ALL, ACTIVE, COMPLETED, FAILED }

    private var currentFilter = DownloadFilter.ALL
    private var searchQuery = ""
    private var sortMode = 0

    private fun setupFilterViews() {
        // Diinisialisasi di sini, bukan di properti, karena getSharedPreferences
        // belum tersedia saat field Activity dibuat (force close di Android).
        sortMode = StoragePrefs.sortMode(this)
        findViewById<TextView>(R.id.sort_button)?.setOnClickListener { showSortDialog() }
        updateSortButton()

        val map = listOf(
            R.id.filter_all to DownloadFilter.ALL,
            R.id.filter_active to DownloadFilter.ACTIVE,
            R.id.filter_completed to DownloadFilter.COMPLETED,
            R.id.filter_failed to DownloadFilter.FAILED
        )
        map.forEach { (id, filter) ->
            findViewById<TextView>(id)?.setOnClickListener {
                currentFilter = filter
                updateFilterColors()
                refreshList()
            }
        }
        updateFilterColors()
        findViewById<EditText>(R.id.input_search)?.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                    searchQuery = s?.toString().orEmpty()
                    refreshList()
                }
                override fun afterTextChanged(s: Editable?) {}
            }
        )
    }

    private fun updateFilterColors() {
        val map = listOf(
            R.id.filter_all to DownloadFilter.ALL,
            R.id.filter_active to DownloadFilter.ACTIVE,
            R.id.filter_completed to DownloadFilter.COMPLETED,
            R.id.filter_failed to DownloadFilter.FAILED
        )
        map.forEach { (id, filter) ->
            val tv = findViewById<TextView>(id) ?: return@forEach
            val selected = filter == currentFilter
            tv.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (selected) R.color.primary else R.color.text_secondary
                )
            )
            tv.typeface = if (selected) android.graphics.Typeface.DEFAULT_BOLD else null
        }
    }

    private fun refreshList() {
        runCatching {
            val filtered = applyFilter(App.engine.items.value)
            adapter.submitList(filtered)
            binding.emptyView.visibility =
                if (filtered.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun applyFilter(items: List<DownloadItem>): List<DownloadItem> {
        val filtered = when (currentFilter) {
            DownloadFilter.ALL -> items
            DownloadFilter.ACTIVE -> items.filter {
                it.state == DownloadState.DOWNLOADING || it.state == DownloadState.PENDING
            }
            DownloadFilter.COMPLETED -> items.filter { it.state == DownloadState.COMPLETED }
            DownloadFilter.FAILED -> items.filter {
                it.state == DownloadState.FAILED || it.state == DownloadState.CANCELLED
            }
        }
        val q = searchQuery.trim().lowercase()
        val searched = if (q.isEmpty()) {
            filtered
        } else {
            filtered.filter {
                it.fileName.lowercase().contains(q) || it.url.lowercase().contains(q)
            }
        }
        val stateRank = mapOf(
            DownloadState.PENDING to 0,
            DownloadState.DOWNLOADING to 1,
            DownloadState.PAUSED to 2,
            DownloadState.COMPLETED to 3,
            DownloadState.FAILED to 4,
            DownloadState.CANCELLED to 5
        )
        return when (sortMode) {
            0 -> searched.sortedByDescending { it.addedAt }
            1 -> searched.sortedBy { it.addedAt }
            2 -> searched.sortedBy { it.fileName.lowercase() }
            3 -> searched.sortedByDescending { it.fileName.lowercase() }
            4 -> searched.sortedByDescending { it.totalBytes }
            5 -> searched.sortedBy { it.totalBytes }
            else -> searched.sortedWith(
                compareBy<DownloadItem> { stateRank[it.state] ?: 0 }
                    .thenBy { it.fileName.lowercase() }
            )
        }
    }

    private fun showSortDialog() {
        val options = resources.getStringArray(R.array.sort_options)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sort_by)
            .setSingleChoiceItems(options, sortMode) { _, which ->
                sortMode = which
                StoragePrefs.setSortMode(this, which)
                updateSortButton()
                refreshList()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateSortButton() {
        val tv = findViewById<TextView>(R.id.sort_button) ?: return
        val options = resources.getStringArray(R.array.sort_options)
        val label = options.getOrElse(sortMode) { options[0] }
        tv.text = getString(R.string.sort_by) + ": " + label
    }

    private fun showAboutDialog() {
        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrDefault("1.0")
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_title)
            .setMessage(
                getString(R.string.about_version, version) + "\n" +
                    getString(R.string.about_author) + "\n\n" +
                    getString(R.string.about_changelog)
            )
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun openDownload(item: DownloadItem) {
        if (item.state != DownloadState.COMPLETED) return
        val mime = MimeTypes.forFile(item.fileName)
        val intent = when {
            !item.contentUri.isNullOrEmpty() -> {
                Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(item.contentUri), mime)
            }
            !item.filePath.isNullOrEmpty() -> {
                val uri = FileProvider.getUriForFile(
                    this, "$packageName.fileprovider", File(item.filePath)
                )
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            else -> null
        }
        if (intent != null) {
            try {
                startActivity(intent)
            } catch (_: Exception) {
                Snackbar.make(binding.root, R.string.no_app_to_open, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun openFolder(item: DownloadItem) {
        val intent = folderIntent(item)
        if (intent == null) {
            Snackbar.make(binding.root, R.string.open_folder_unavailable, Snackbar.LENGTH_LONG)
                .show()
            return
        }
        runCatching { startActivity(intent) }.onFailure {
            Snackbar.make(binding.root, R.string.open_folder_unavailable, Snackbar.LENGTH_LONG)
                .show()
        }
    }

    private fun folderIntent(item: DownloadItem): Intent? {
        return when {
            !item.filePath.isNullOrEmpty() -> {
                val parent = File(item.filePath).parentFile ?: return null
                val rel = parent.absolutePath.removePrefix("/storage/emulated/0/")
                if (rel != parent.absolutePath) {
                    Intent(Intent.ACTION_VIEW).setDataAndType(
                        DocumentsContract.buildDocumentUri(
                            "com.android.externalstorage.documents", "primary:$rel"
                        ),
                        "vnd.android.document/directory"
                    )
                } else if (Build.VERSION.SDK_INT < 24) {
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(Uri.fromFile(parent), "resource/folder")
                } else {
                    null
                }
            }
            !item.contentUri.isNullOrEmpty() -> {
                Intent(Intent.ACTION_VIEW).setDataAndType(
                    DocumentsContract.buildDocumentUri(
                        "com.android.externalstorage.documents", "primary:Download"
                    ),
                    "vnd.android.document/directory"
                )
            }
            else -> null
        }
    }

    companion object {
        private const val EXTRA_ADD_DOWNLOAD = "com.tasirin.httpdownloadmanager.ADD_DOWNLOAD"
    }
}
