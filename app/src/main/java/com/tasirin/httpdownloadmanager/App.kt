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
        runCatching { engine.cleanupOrphans() }
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

        fun appendCrash(context: android.content.Context, tag: String, t: Throwable) {
            runCatching {
                val file = File(context.filesDir, CRASH_LOG_FILE)
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val text = buildString {
                    appendLine("=== $stamp [$tag] ===")
                    appendLine(Log.getStackTraceString(t))
                    appendLine()
                }
                val existing = if (file.exists()) file.readText() else ""
                file.writeText((existing + text).takeLast(100_000))
            }
        }
    }
}
