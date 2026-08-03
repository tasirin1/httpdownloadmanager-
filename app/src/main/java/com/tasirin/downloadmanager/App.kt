package com.tasirin.downloadmanager

import android.app.Application
import com.tasirin.downloadmanager.download.DownloadEngine

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        engine = DownloadEngine(this)
    }

    companion object {
        lateinit var engine: DownloadEngine
    }
}
