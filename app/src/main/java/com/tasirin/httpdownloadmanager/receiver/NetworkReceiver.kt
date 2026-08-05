package com.tasirin.httpdownloadmanager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import com.tasirin.httpdownloadmanager.App

class NetworkReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ConnectivityManager.CONNECTIVITY_ACTION) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        val info = runCatching { cm.activeNetworkInfo }.getOrNull() ?: return
        if (info.isConnected) {
            // Koneksi baru tersedia: lanjutkan download yang dijeda otomatis
            // karena jaringan hilang sebelumnya.
            runCatching { App.engine.resumeAutoPaused() }
        }
    }
}
