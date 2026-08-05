package com.tasirin.httpdownloadmanager.util

import android.content.Context
import android.net.Uri

object StoragePrefs {

    const val DEFAULT_PORT = 8080

    private const val PREFS = "storage_settings"
    private const val KEY_FOLDER_URI = "folder_uri"
    private const val KEY_FOLDER_NAME = "folder_name"
    private const val KEY_BACKGROUND = "background_download"
    private const val KEY_AUTOSTART = "auto_start_boot"
    private const val KEY_SERVER_BACKGROUND = "server_background"
    private const val KEY_SERVER_AUTOSTART = "server_autostart_boot"
    private const val KEY_TEXT_FOLDER = "text_folder_path"
    private const val KEY_BATTERY_EXEMPT = "battery_exempt"
    private const val KEY_ONBOARDED = "storage_onboarded"
    private const val KEY_SERVER_PIN = "server_pin"
    private const val KEY_MAX_CONCURRENT = "max_concurrent"
    private const val KEY_SPEED_LIMIT = "speed_limit_kbps"
    private const val KEY_MAX_RETRIES = "max_retries"
    private const val KEY_RECENT_URLS = "recent_urls"
    private const val KEY_SERVER_PORT = "server_port"
    private const val KEY_SEGMENTS = "segments"
    private const val KEY_SORT_MODE = "sort_mode"
    private const val KEY_AUTO_SORT = "auto_sort"

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

    fun isServerBackgroundEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SERVER_BACKGROUND, false)

    fun setServerBackgroundEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SERVER_BACKGROUND, enabled)
            .apply()
    }

    fun getTextFolder(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TEXT_FOLDER, null)?.takeIf { it.isNotBlank() }

    fun setTextFolder(context: Context, path: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TEXT_FOLDER, path?.takeIf { it.isNotBlank() })
            .apply()
    }

    fun isBatteryExemptEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_BATTERY_EXEMPT, true)

    fun setBatteryExemptEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_BATTERY_EXEMPT, enabled)
            .apply()
    }

    fun getServerPin(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SERVER_PIN, null)?.takeIf { it.isNotBlank() }

    fun setServerPin(context: Context, pin: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SERVER_PIN, pin?.trim()?.takeIf { it.isNotEmpty() })
            .apply()
    }

    fun isStorageOnboarded(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ONBOARDED, false)

    fun setStorageOnboarded(context: Context, value: Boolean = true) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ONBOARDED, value)
            .apply()
    }

    fun isServerAutoStartEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SERVER_AUTOSTART, true)

    fun setServerAutoStartEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SERVER_AUTOSTART, enabled)
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

    fun isAutoSortEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_SORT, false)

    fun setAutoSortEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTO_SORT, enabled)
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

    fun serverPort(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_SERVER_PORT, DEFAULT_PORT).coerceIn(1024, 65535)

    fun setServerPort(context: Context, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_SERVER_PORT, value.coerceIn(1024, 65535))
            .apply()
    }

    fun segmentCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_SEGMENTS, 4).coerceIn(1, 8)

    fun setSegmentCount(context: Context, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_SEGMENTS, value.coerceIn(1, 8))
            .apply()
    }

    fun sortMode(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_SORT_MODE, 0).coerceIn(0, 6)

    fun setSortMode(context: Context, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_SORT_MODE, value.coerceIn(0, 6))
            .apply()
    }

    fun clearRecentUrls(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_RECENT_URLS, "")
            .apply()
    }
}
