package com.tasirin.httpdownloadmanager

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.material.snackbar.Snackbar
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.tasirin.httpdownloadmanager.data.DownloadState
import com.tasirin.httpdownloadmanager.databinding.ActivitySettingsBinding
import com.tasirin.httpdownloadmanager.download.DownloadService
import com.tasirin.httpdownloadmanager.remote.HttpControlServer
import com.tasirin.httpdownloadmanager.util.Formats
import com.tasirin.httpdownloadmanager.util.StoragePrefs
import java.io.File

/** Halaman pengaturan: server remote, keamanan, log, unduhan, dan penyimpanan. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private var activeStorageInput: EditText? = null
    private var storagePathEdited = false
    private var updatingStorageInput = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* hasil izin tidak wajib untuk fungsi inti */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        runCatching { installSplashScreen() }
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        renderServer()
        wireServerSwitch()
        wireServerChecks()
        binding.btnLog.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
        binding.btnCleanup.setOnClickListener {
            val (files, bytes) = cleanupJunkFiles()
            binding.cleanupResult.text = if (files > 0) {
                getString(R.string.cleanup_done, files, Formats.bytes(bytes))
            } else {
                getString(R.string.cleanup_empty)
            }
        }
        wireDownloadSettings()
        wireStorageSection()
        wireSave()
    }

    override fun onResume() {
        super.onResume()
        renderServer()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /** Hapus file .part menggantung + cache thumbnail. Tidak menyentuh file
     *  milik download yang masih berjalan/antre/jeda. */
    private fun cleanupJunkFiles(): Pair<Int, Long> {
        var files = 0
        var bytes = 0L
        fun deleteIf(f: File) {
            if (!f.isFile) return
            bytes += f.length()
            if (f.delete()) files++
        }
        val itemNames = App.engine.items.value.map { it.fileName }.toSet()
        val partPattern = Regex("\\.part(\\.\\d+)?$")
        runCatching {
            File(filesDir, "downloads").listFiles()?.forEach { f ->
                val base = f.name.replace(partPattern, "")
                if (partPattern.containsMatchIn(f.name) && base !in itemNames) {
                    deleteIf(f)
                }
            }
        }
        runCatching {
            File(cacheDir, "thumbs").listFiles()?.forEach { deleteIf(it) }
        }
        return files to bytes
    }

    private fun renderServer() {
        val server = App.httpServer
        val needsStorage = when {
            Build.VERSION.SDK_INT >= 30 -> !Environment.isExternalStorageManager()
            Build.VERSION.SDK_INT >= 23 ->
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                    PackageManager.PERMISSION_GRANTED
            else -> false
        }
        binding.storageBtn.visibility = if (needsStorage) View.VISIBLE else View.GONE
        binding.storageBtn.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 30) {
                runCatching {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                }.onFailure {
                    runCatching {
                        startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                }
            } else {
                requestPermissionsIfNeeded()
            }
        }
        binding.serverSwitch.isChecked = server.isAlive
        if (server.isAlive) {
            binding.serverStatus.setText(R.string.remote_running)
            val urls = HttpControlServer.ipv4Addresses()
                .map { "http://$it:${server.listeningPort}/" }
            binding.urls.text = urls.joinToString("\n").ifEmpty {
                getString(R.string.remote_no_url)
            }
            urls.firstOrNull()?.let { address ->
                generateQrCode(address, 640)?.let { binding.qr.setImageBitmap(it) }
            }
            binding.qr.visibility = View.VISIBLE
        } else {
            binding.serverStatus.setText(R.string.remote_stopped)
            binding.urls.text = getString(R.string.remote_no_url)
            binding.qr.visibility = View.GONE
        }
    }

    private fun wireServerSwitch() {
        var updating = false
        binding.serverSwitch.setOnCheckedChangeListener { _, checked ->
            if (updating) return@setOnCheckedChangeListener
            if (checked) {
                if (StoragePrefs.isPinEnforced(this) &&
                    StoragePrefs.getServerPin(this).isNullOrEmpty()
                ) {
                    updating = true
                    binding.serverSwitch.isChecked = false
                    updating = false
                    Snackbar.make(
                        binding.root,
                        R.string.remote_pin_required,
                        Snackbar.LENGTH_LONG
                    ).show()
                    return@setOnCheckedChangeListener
                }
                StoragePrefs.setServerBackgroundEnabled(this, true)
                val result = runCatching { App.httpServer.startServer() }
                if (result.isFailure) {
                    StoragePrefs.setServerBackgroundEnabled(this, false)
                    updating = true
                    binding.serverSwitch.isChecked = false
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
            renderServer()
        }
    }

    private fun wireServerChecks() {
        binding.checkServerAutostart.isChecked = StoragePrefs.isServerAutoStartEnabled(this)
        binding.checkServerAutostart.setOnCheckedChangeListener { _, checked ->
            StoragePrefs.setServerAutoStartEnabled(this, checked)
        }
        binding.checkPinEnforced.isChecked = StoragePrefs.isPinEnforced(this)
        binding.checkPinEnforced.setOnCheckedChangeListener { _, checked ->
            StoragePrefs.setPinEnforced(this, checked)
            if (checked && StoragePrefs.getServerPin(this).isNullOrEmpty()) {
                StoragePrefs.setServerBackgroundEnabled(this, false)
                StoragePrefs.setServerAutoStartEnabled(this, false)
                runCatching { App.httpServer.stopServer() }
                stopServiceIfIdle()
                renderServer()
            }
        }
        binding.checkFsFullAccess.isChecked = StoragePrefs.isFsFullAccessEnabled(this)
        binding.checkFsFullAccess.setOnCheckedChangeListener { _, checked ->
            StoragePrefs.setFsFullAccessEnabled(this, checked)
        }
        binding.inputPin.setText(StoragePrefs.getServerPin(this).orEmpty())
        binding.inputPort.setText(StoragePrefs.serverPort(this).toString())
    }

    private fun wireDownloadSettings() {
        binding.checkBackground.isChecked = StoragePrefs.isBackgroundEnabled(this)
        binding.checkBackground.setOnCheckedChangeListener { _, c ->
            StoragePrefs.setBackgroundEnabled(this, c)
        }
        binding.checkAutostart.isChecked = StoragePrefs.isAutoStartEnabled(this)
        binding.checkAutostart.setOnCheckedChangeListener { _, c ->
            StoragePrefs.setAutoStartEnabled(this, c)
        }
        binding.checkBattery.isChecked = StoragePrefs.isBatteryExemptEnabled(this)
        binding.checkBattery.setOnCheckedChangeListener { _, c ->
            StoragePrefs.setBatteryExemptEnabled(this, c)
            if (c) requestBatteryExemption()
        }
        binding.checkAutoSort.isChecked = StoragePrefs.isAutoSortEnabled(this)
        binding.checkAutoSort.setOnCheckedChangeListener { _, c ->
            StoragePrefs.setAutoSortEnabled(this, c)
        }
        binding.checkSmallFirst.isChecked = StoragePrefs.isSmallFirstEnabled(this)
        binding.checkSmallFirst.setOnCheckedChangeListener { _, c ->
            StoragePrefs.setSmallFirstEnabled(this, c)
        }
        binding.checkDeletePartial.isChecked = StoragePrefs.isDeletePartialOnCancel(this)
        binding.checkDeletePartial.setOnCheckedChangeListener { _, c ->
            StoragePrefs.setDeletePartialOnCancel(this, c)
        }

        val concurrentOptions = resources.getStringArray(R.array.concurrent_options)
        binding.spinnerConcurrent.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, concurrentOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerConcurrent.setSelection(
            (StoragePrefs.maxConcurrent(this) - 1).coerceIn(0, concurrentOptions.size - 1)
        )
        binding.spinnerConcurrent.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                StoragePrefs.setMaxConcurrent(this@SettingsActivity, position + 1)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val currentSpeed = StoragePrefs.speedLimitKbps(this)
        val speedOptions = resources.getStringArray(R.array.speed_limit_options).toMutableList()
        val speedKbps = SPEED_KBPS
        if (currentSpeed !in speedKbps) {
            speedOptions.add(getString(R.string.settings_speed_custom, currentSpeed))
        }
        binding.spinnerSpeed.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, speedOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerSpeed.setSelection(
            if (currentSpeed in speedKbps) speedKbps.indexOf(currentSpeed) else speedOptions.size - 1
        )
        binding.spinnerSpeed.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                StoragePrefs.setSpeedLimitKbps(
                    this@SettingsActivity,
                    if (position < speedKbps.size) speedKbps[position] else currentSpeed
                )
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val retryOptions = resources.getStringArray(R.array.retry_options)
        binding.spinnerRetry.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, retryOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerRetry.setSelection(
            StoragePrefs.maxRetries(this).coerceIn(0, retryOptions.size - 1)
        )
        binding.spinnerRetry.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                StoragePrefs.setMaxRetries(this@SettingsActivity, position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val segmentOptions = resources.getStringArray(R.array.segment_options)
        val segmentValues = intArrayOf(1, 2, 4, 6, 8)
        binding.spinnerSegments.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, segmentOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerSegments.setSelection(
            segmentValues.indexOf(StoragePrefs.segmentCount(this)).coerceAtLeast(0)
        )
        binding.spinnerSegments.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                StoragePrefs.setSegmentCount(this@SettingsActivity, segmentValues[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun wireStorageSection() {
        // View binding tidak mengekspos view dari <include>, jadi pakai
        // findViewById (pola yang sama seperti MainActivity).
        val pathInput = findViewById<EditText>(R.id.input_storage_path)
        activeStorageInput = pathInput
        storagePathEdited = false
        pathInput.setText(StoragePrefs.getTextFolder(this) ?: defaultDownloadsPath())
        pathInput.setSelection(pathInput.text.length)
        pathInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!updatingStorageInput) storagePathEdited = true
            }
        })
        val box = findViewById<LinearLayout>(R.id.extra_folders_container)
        StoragePrefs.getExtraFolders(this).forEach { addExtraFolderRow(box, it) }
        findViewById<Button>(R.id.btn_add_folder).setOnClickListener {
            addExtraFolderRow(box, "")
        }
    }

    private fun addExtraFolderRow(box: LinearLayout, path: String) {
        val row = layoutInflater.inflate(R.layout.row_extra_folder, box, false)
        val input = row.findViewById<EditText>(R.id.extra_folder_path)
        input.setText(path)
        input.setSelection(input.text.length)
        row.findViewById<Button>(R.id.btn_remove_folder).setOnClickListener {
            box.removeView(row)
        }
        box.addView(row)
    }

    private fun applyExtraFolders(view: View) {
        val box = view.findViewById<LinearLayout>(R.id.extra_folders_container)
        val paths = mutableListOf<String>()
        for (i in 0 until box.childCount) {
            val input = box.getChildAt(i).findViewById<EditText>(R.id.extra_folder_path)
            val path = input.text?.toString()?.trim().orEmpty()
            if (path.isEmpty()) continue
            val dir = java.io.File(path)
            if (!dir.isDirectory && !dir.mkdirs()) {
                Toast.makeText(this, R.string.storage_text_folder_invalid, Toast.LENGTH_LONG).show()
                return
            }
            paths.add(path)
        }
        StoragePrefs.setExtraFolders(this, paths)
    }

    private fun wireSave() {
        binding.btnSave.setOnClickListener {
            applyStoragePath(findViewById<EditText>(R.id.input_storage_path))
            applyExtraFolders(binding.root)
            val newPin = binding.inputPin.text?.toString()?.trim().orEmpty()
            val oldPin = StoragePrefs.getServerPin(this).orEmpty()
            if (newPin != oldPin) {
                App.logEvent(if (newPin.isEmpty()) "PIN DIHAPUS" else "PIN DIATUR")
            }
            StoragePrefs.setServerPin(this, newPin)
            if (StoragePrefs.isPinEnforced(this) &&
                StoragePrefs.getServerPin(this).isNullOrEmpty()
            ) {
                StoragePrefs.setServerBackgroundEnabled(this, false)
                StoragePrefs.setServerAutoStartEnabled(this, false)
                runCatching { App.httpServer.stopServer() }
                stopServiceIfIdle()
                renderServer()
            }
            val newPort = binding.inputPort.text?.toString()?.trim()?.toIntOrNull()
            if (newPort == null || newPort !in 1024..65535) {
                Toast.makeText(this, R.string.settings_port_invalid, Toast.LENGTH_LONG).show()
            } else {
                val oldPort = App.httpServer.listeningPort
                StoragePrefs.setServerPort(this, newPort)
                if (newPort != oldPort) {
                    App.logEvent("PORT DIGANTI: $oldPort -> $newPort")
                    App.restartHttpServer(this)
                }
            }
            renderServer()
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshActiveStorageUi() {
        val input = activeStorageInput
        storagePathEdited = false
        if (input != null) {
            updatingStorageInput = true
            input.setText(StoragePrefs.getTextFolder(this) ?: defaultDownloadsPath())
            input.setSelection(input.text.length)
            updatingStorageInput = false
        }
    }

    private fun applyStoragePath(pathInput: EditText) {
        if (!storagePathEdited) return
        val path = pathInput.text?.toString()?.trim().orEmpty()
        if (path.isEmpty()) return
        val dir = java.io.File(path)
        if (!dir.isDirectory && !dir.mkdirs()) {
            Toast.makeText(this, R.string.storage_text_folder_invalid, Toast.LENGTH_LONG).show()
            return
        }
        StoragePrefs.setTextFolder(this, path)
        StoragePrefs.saveFolder(this, null, null)
        refreshActiveStorageUi()
        Toast.makeText(
            this,
            getString(R.string.storage_text_folder_saved, path),
            Toast.LENGTH_SHORT
        ).show()
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


    private fun defaultDownloadsPath(): String {
        if (Build.VERSION.SDK_INT >= 29) return "/storage/emulated/0/Download"
        return runCatching {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .absolutePath
        }.getOrDefault("/storage/emulated/0/Download")
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

    /** Hentikan DownloadService bila tidak ada download aktif (server juga mati). */
    private fun stopServiceIfIdle() {
        val anyActive = App.engine.items.value.any {
            it.state == DownloadState.DOWNLOADING || it.state == DownloadState.PENDING
        }
        if (!anyActive) {
            runCatching { stopService(Intent(this, DownloadService::class.java)) }
        }
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

    companion object {
        private val SPEED_KBPS = intArrayOf(0, 128, 256, 512, 1024, 2048, 5120)
    }
}
