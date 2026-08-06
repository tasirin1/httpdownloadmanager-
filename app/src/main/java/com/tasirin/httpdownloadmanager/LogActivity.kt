package com.tasirin.httpdownloadmanager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.material.snackbar.Snackbar
import com.tasirin.httpdownloadmanager.databinding.ActivityLogBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Halaman khusus log server realtime: layar penuh, auto-scroll default mati. */
class LogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogBinding
    private var logAutoScroll = false

    override fun onCreate(savedInstanceState: Bundle?) {
        runCatching { installSplashScreen() }
        super.onCreate(savedInstanceState)
        binding = ActivityLogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.logAutoscroll.isChecked = logAutoScroll
        binding.logAutoscroll.setOnCheckedChangeListener { _, checked ->
            logAutoScroll = checked
        }
        binding.logCopy.setOnClickListener {
            val text = App.httpServer.snapshotLog()
                .ifEmpty { getString(R.string.remote_log_empty) }
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("server log", text))
            Snackbar.make(binding.root, R.string.remote_log_copied, Snackbar.LENGTH_SHORT).show()
        }
        binding.logClear.setOnClickListener {
            App.httpServer.clearLog()
            refreshLog()
        }
        binding.logExport.setOnClickListener { exportLogTxt() }

        refreshLog()
        val pollLog = object : Runnable {
            override fun run() {
                if (isDestroyed || isFinishing) return
                refreshLog()
                binding.log.postDelayed(this, 1000)
            }
        }
        binding.log.postDelayed(pollLog, 1000)
    }

    private fun exportLogTxt() {
        val log = App.httpServer.snapshotLog()
        val header = buildString {
            appendLine("=== HTTP Download Manager - Log Server (realtime) ===")
            appendLine("Waktu: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine(
                "Versi app: " + runCatching {
                    packageManager.getPackageInfo(packageName, 0).versionName
                }.getOrDefault("?")
            )
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Perangkat: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
            append(if (log.isBlank()) "(Belum ada aktivitas server)\n" else log)
            appendLine()
        }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val ok = runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val resolver = contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "httpdm-serverlog-$stamp.txt")
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching false
                runCatching {
                    resolver.openOutputStream(uri)?.use { it.write(header.toByteArray()) }
                }.onFailure { resolver.delete(uri, null, null) }
                val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                resolver.update(uri, done, null, null) > 0
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                if (dir == null) return@runCatching false
                if (!dir.isDirectory && !dir.mkdirs()) return@runCatching false
                File(dir, "httpdm-serverlog-$stamp.txt").writeText(header)
                true
            }
        }.getOrDefault(false)
        Toast.makeText(
            this,
            if (ok) R.string.log_exported else R.string.log_export_failed,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun refreshLog() {
        val prevScroll = binding.logScroll.scrollY
        binding.log.text = App.httpServer.snapshotLog()
            .ifEmpty { getString(R.string.remote_log_empty) }
        binding.logScroll.post {
            if (logAutoScroll) {
                binding.logScroll.fullScroll(View.FOCUS_DOWN)
            } else {
                val max = binding.logScroll.getChildAt(0)?.height
                    ?.minus(binding.logScroll.height) ?: 0
                binding.logScroll.scrollTo(0, prevScroll.coerceIn(0, max.coerceAtLeast(0)))
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
