package com.tasirin.httpdownloadmanager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.material.snackbar.Snackbar
import com.tasirin.httpdownloadmanager.databinding.ActivityLogBinding

/** Halaman khusus log server realtime: layar penuh, auto-scroll bisa dimatikan. */
class LogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogBinding
    private var logAutoScroll = true

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
