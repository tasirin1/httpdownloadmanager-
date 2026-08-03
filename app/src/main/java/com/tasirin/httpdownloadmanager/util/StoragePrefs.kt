package com.tasirin.httpdownloadmanager.util

import android.content.Context
import android.net.Uri

object StoragePrefs {

    private const val PREFS = "storage_settings"
    private const val KEY_FOLDER_URI = "folder_uri"
    private const val KEY_FOLDER_NAME = "folder_name"
    private const val KEY_BACKGROUND = "background_download"
    private const val KEY_AUTOSTART = "auto_start_boot"
    private const val KEY_MAX_CONCURRENT = "max_concurrent"
    private const val KEY_SPEED_LIMIT = "speed_limit_kbps"
    private const val KEY_MAX_RETRIES = "max_retries"
    private const val KEY_RECENT_URLS = "recent_urls"

    fun getFolderUri(context: Context): Uri? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FOLDER_URI, null)
        return raw?.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }
    }

    fun getFolderName(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FOLDER_NAME, null)

    fun saveFolder(context: Context, uri: Uri?, name: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_FOLDER_URI, uri?.toString())
            .putString(KEY_FOLDER_NAME, name)
            .apply()
    }

    fun isBackgroundEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_BACKGROUND, true)

    fun setBackgroundEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_BACKGROUND, enabled)
            .apply()
    }

    fun isAutoStartEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTOSTART, true)

    fun setAutoStartEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTOSTART, enabled)
            .apply()
    }

    fun maxConcurrent(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_MAX_CONCURRENT, 2).coerceIn(1, 5)

    fun setMaxConcurrent(context: Context, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_MAX_CONCURRENT, value.coerceIn(1, 5))
            .apply()
    }

    fun speedLimitKbps(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_SPEED_LIMIT, 0).coerceIn(0, 100_000)

    fun setSpeedLimitKbps(context: Context, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_SPEED_LIMIT, value.coerceIn(0, 100_000))
            .apply()
    }

    fun maxRetries(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_MAX_RETRIES, 2).coerceIn(0, 5)

    fun setMaxRetries(context: Context, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_MAX_RETRIES, value.coerceIn(0, 5))
            .apply()
    }

    fun recentUrls(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RECENT_URLS, "")
            .orEmpty()
            .split("\n".toRegex())
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun addRecentUrl(context: Context, url: String) {
        val clean = url.trim()
        if (clean.isEmpty()) return
        val current = recentUrls(context).filter { it != clean }
        val updated = (listOf(clean) + current).take(20)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_RECENT_URLS, updated.joinToString("\n"))
            .apply()
    }
}
