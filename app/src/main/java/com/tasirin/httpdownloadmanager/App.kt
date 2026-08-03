package com.tasirin.httpdownloadmanager

import android.app.Application
import com.tasirin.httpdownloadmanager.download.DownloadEngine

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        engine = DownloadEngine(this)
    }

    companion object {
        lateinit var engine: DownloadEngine
    }
}
