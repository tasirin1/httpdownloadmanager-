package com.tasirin.httpdownloadmanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.tasirin.httpdownloadmanager.data.DownloadItem
import com.tasirin.httpdownloadmanager.data.DownloadState
import com.tasirin.httpdownloadmanager.databinding.ActivityMainBinding
import com.tasirin.httpdownloadmanager.remote.HttpControlServer
import com.tasirin.httpdownloadmanager.ui.DownloadAdapter
import com.tasirin.httpdownloadmanager.util.MimeTypes
import com.tasirin.httpdownloadmanager.util.StoragePrefs
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity(), DownloadAdapter.Listener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: DownloadAdapter
    private var pendingMoveId: String? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* hasil izin tidak wajib untuk fungsi inti */ }

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val name = DocumentFile.fromTreeUri(this, uri)?.name
            StoragePrefs.saveFolder(this, uri, name)
            val label = name ?: getString(R.string.storage_custom_folder)
            Snackbar.make(
                binding.root,
                getString(R.string.storage_folder_selected, label),
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private val movePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val id = pendingMoveId
        pendingMoveId = null
        if (uri != null && id != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            App.engine.move(id, uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        adapter = DownloadAdapter(this)
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.fabAdd.setOnClickListener { showAddDialog() }

        lifecycleScope.launch {
            App.engine.items.collect { items ->
                adapter.submitList(items)
                binding.emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        requestPermissionsIfNeeded()
        if (StoragePrefs.isBackgroundEnabled(this)) {
            App.engine.resumeInterrupted()
        }
        handleIncomingIntent(intent)
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
        if (Build.VERSION.SDK_INT in 23..28 &&
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
        val recentTitle = view.findViewById<TextView>(R.id.recent_title)
        val recentContainer = view.findViewById<LinearLayout>(R.id.recent_container)

        if (!prefillUrl.isNullOrBlank()) {
            urlInput.setText(prefillUrl)
        }

        val recents = StoragePrefs.recentUrls(this)
        if (recents.isNotEmpty()) {
            recentTitle.visibility = View.VISIBLE
            val density = resources.displayMetrics.density
            recents.take(5).forEach { u ->
                val tv = TextView(this)
                tv.text = u
                tv.maxLines = 1
                tv.ellipsize = TextUtils.TruncateAt.MIDDLE
                tv.setTextColor(ContextCompat.getColor(this, R.color.primary))
                tv.setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
                tv.setOnClickListener { urlInput.setText(u) }
                recentContainer.addView(tv)
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_download)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.download) { _, _ ->
                val urls = urlInput.text?.toString()?.trim().orEmpty()
                    .split(Regex("\\s+"))
                    .filter { it.startsWith("http://") || it.startsWith("https://") }
                if (urls.isEmpty()) {
                    Snackbar.make(binding.root, R.string.invalid_url, Snackbar.LENGTH_SHORT).show()
                } else {
                    val name = nameInput.text?.toString()?.trim().orEmpty()
                    val username = usernameInput.text?.toString()?.trim().orEmpty()
                    val password = passwordInput.text?.toString()?.trim().orEmpty()
                    val headers = headersInput.text?.toString()?.trim().orEmpty()
                    urls.forEachIndexed { index, url ->
                        App.engine.addDownload(
                            url,
                            if (index == 0) name else null,
                            username,
                            password,
                            headers
                        )
                    }
                }
            }
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
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
            R.id.action_settings -> {
                showSettingsDialog()
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
        }
    }

    override fun onLongPress(item: DownloadItem) {
        val options = mutableListOf<Pair<String, () -> Unit>>()
        if (item.state == DownloadState.COMPLETED) {
            options.add(getString(R.string.open) to { openDownload(item) })
            options.add(getString(R.string.action_rename) to { showRenameDialog(item) })
            options.add(getString(R.string.action_move) to {
                pendingMoveId = item.id
                movePicker.launch(null)
            })
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

    private fun showStorageDialog() {
        val currentName = StoragePrefs.getFolderName(this)
            ?: getString(R.string.storage_default_folder)
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_storage)
            .setMessage(getString(R.string.storage_current, currentName))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.storage_choose_folder) { _, _ ->
                folderPicker.launch(null)
            }
        if (StoragePrefs.getFolderUri(this) != null) {
            builder.setNeutralButton(R.string.storage_use_default) { _, _ ->
                StoragePrefs.saveFolder(this, null, null)
                Snackbar.make(
                    binding.root,
                    R.string.storage_default_folder,
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
        builder.show()
    }

    private fun showSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        val checkBackground = view.findViewById<CheckBox>(R.id.check_background)
        val checkAutoStart = view.findViewById<CheckBox>(R.id.check_autostart)
        checkBackground.isChecked = StoragePrefs.isBackgroundEnabled(this)
        checkAutoStart.isChecked = StoragePrefs.isAutoStartEnabled(this)

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

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_settings)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                StoragePrefs.setBackgroundEnabled(this, checkBackground.isChecked)
                StoragePrefs.setAutoStartEnabled(this, checkAutoStart.isChecked)
                StoragePrefs.setMaxConcurrent(this, spinnerConcurrent.selectedItemPosition + 1)
                StoragePrefs.setSpeedLimitKbps(this, speedKbps[spinnerSpeed.selectedItemPosition])
                StoragePrefs.setMaxRetries(this, spinnerRetry.selectedItemPosition)
            }
            .show()
    }

    private fun showRemoteDialog() {
        val server = App.httpServer
        val status = if (server.isAlive) {
            getString(R.string.remote_running)
        } else {
            getString(R.string.remote_stopped)
        }
        val urls = if (server.isAlive) {
            HttpControlServer.ipv4Addresses()
                .joinToString("\n") { "http://$it:${server.listeningPort}/" }
                .ifEmpty { getString(R.string.remote_no_url) }
        } else {
            getString(R.string.remote_no_url)
        }
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_remote)
            .setMessage(getString(R.string.remote_message, status, urls))
            .setNegativeButton(R.string.cancel, null)
        if (server.isAlive) {
            builder.setPositiveButton(R.string.remote_stop) { _, _ ->
                server.stopServer()
                Snackbar.make(binding.root, R.string.remote_stopped, Snackbar.LENGTH_SHORT).show()
            }
        } else {
            builder.setPositiveButton(R.string.remote_start) { _, _ ->
                runCatching { server.startServer() }
                    .onSuccess {
                        Snackbar.make(
                            binding.root,
                            getString(R.string.remote_started, server.listeningPort),
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                    .onFailure {
                        Snackbar.make(
                            binding.root,
                            getString(R.string.remote_start_failed, it.message ?: "?"),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
            }
        }
        builder.show()
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
}
