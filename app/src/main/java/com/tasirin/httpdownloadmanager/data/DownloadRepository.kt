package com.tasirin.httpdownloadmanager.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class DownloadRepository(context: Context) {

    private val prefs = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)

    fun load(): List<DownloadItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val rawState = DownloadState.valueOf(o.getString("state"))
                    // Setelah proses di-restart, download yang tadi berjalan dianggap dijeda.
                    val state = if (rawState == DownloadState.DOWNLOADING || rawState == DownloadState.PENDING) {
                        DownloadState.PAUSED
                    } else {
                        rawState
                    }
                    add(
                        DownloadItem(
                            id = o.getString("id"),
                            url = o.getString("url"),
                            fileName = o.getString("fileName"),
                            state = state,
                            bytesDownloaded = o.optLong("bytesDownloaded", 0),
                            totalBytes = o.optLong("totalBytes", 0),
                            error = o.optString("error").ifEmpty { null },
                            contentUri = o.optString("contentUri").ifEmpty { null },
                            filePath = o.optString("filePath").ifEmpty { null },
                            addedAt = o.optLong("addedAt", 0),
                            nameIsCustom = o.optBoolean("nameIsCustom", false)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(items: List<DownloadItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            val o = JSONObject()
            o.put("id", item.id)
            o.put("url", item.url)
            o.put("fileName", item.fileName)
            o.put("state", item.state.name)
            o.put("bytesDownloaded", item.bytesDownloaded)
            o.put("totalBytes", item.totalBytes)
            item.error?.let { o.put("error", it) }
            item.contentUri?.let { o.put("contentUri", it) }
            item.filePath?.let { o.put("filePath", it) }
            o.put("addedAt", item.addedAt)
            o.put("nameIsCustom", item.nameIsCustom)
            arr.put(o)
        }
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    companion object {
        private const val KEY_ITEMS = "items"
    }
}
