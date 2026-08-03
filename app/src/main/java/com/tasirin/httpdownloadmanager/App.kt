package com.tasirin.httpdownloadmanager

import android.app.Application
import android.util.Log
import com.tasirin.httpdownloadmanager.download.DownloadEngine
import com.tasirin.httpdownloadmanager.remote.HttpControlServer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        engine = DownloadEngine(this)
        httpServer = HttpControlServer(this)
    }

    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val file = File(filesDir, CRASH_LOG_FILE)
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val text = buildString {
                    appendLine("=== $stamp [${thread.name}] ===")
                    appendLine(Log.getStackTraceString(throwable))
                    appendLine()
                }
                val existing = if (file.exists()) file.readText() else ""
                file.writeText((existing + text).takeLast(100_000))
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        const val CRASH_LOG_FILE = "crash.log"
        lateinit var engine: DownloadEngine
        lateinit var httpServer: HttpControlServer
    }
}
