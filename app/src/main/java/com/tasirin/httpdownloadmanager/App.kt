package com.tasirin.httpdownloadmanager

import android.app.Application
import com.tasirin.httpdownloadmanager.download.DownloadEngine
import com.tasirin.httpdownloadmanager.remote.HttpControlServer

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        engine = DownloadEngine(this)
        httpServer = HttpControlServer(this)
    }

    companion object {
        lateinit var engine: DownloadEngine
        lateinit var httpServer: HttpControlServer
    }
}
