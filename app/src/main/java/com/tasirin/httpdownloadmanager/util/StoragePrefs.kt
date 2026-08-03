package com.tasirin.httpdownloadmanager.util

import android.content.Context
import android.net.Uri

object StoragePrefs {

    private const val PREFS = "storage_settings"
    private const val KEY_FOLDER_URI = "folder_uri"
    private const val KEY_FOLDER_NAME = "folder_name"

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
}
