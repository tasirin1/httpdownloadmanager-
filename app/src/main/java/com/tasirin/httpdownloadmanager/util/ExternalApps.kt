package com.tasirin.httpdownloadmanager.util

import android.content.Context
import android.content.Intent

/** Peluncuran aplikasi eksternal yang dipakai aplikasi ini. */
object ExternalApps {

    const val TOTAL_COMMANDER = "com.ghisler.android.TotalCommander"

    /** Intent peluncur Total Commander bila terpasang; null bila tidak ada. */
    fun launchIntentForTotalCommander(context: Context): Intent? =
        runCatching { context.packageManager.getLaunchIntentForPackage(TOTAL_COMMANDER) }
            .getOrNull()
}
