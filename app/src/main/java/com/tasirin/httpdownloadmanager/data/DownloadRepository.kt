package com.tasirin.httpdownloadmanager.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class DownloadRepository(context: Context) {

    private val prefs = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)

    fun load(): List<DownloadItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val items = mutableListOf<DownloadItem>()
        for (i in 0 until arr.length()) {
            // Satu entry korup tidak boleh menghapus seluruh daftar.
            parseItem(arr.optJSONObject(i))?.let { items.add(it) }
        }
        return items
    }

    private fun parseItem(o: JSONObject?): DownloadItem? {
        if (o == null) return null
        return runCatching {
            val rawState = DownloadState.valueOf(o.getString("state"))
            // Setelah proses di-restart, download yang tadi berjalan dianggap dijeda.
            val state = if (rawState == DownloadState.DOWNLOADING || rawState == DownloadState.PENDING) {
                DownloadState.PAUSED
            } else {
                rawState
            }
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
                nameIsCustom = o.optBoolean("nameIsCustom", false),
                autoResume = o.optBoolean("autoResume", false),
                username = o.optString("username"),
                password = o.optString("password"),
                headers = o.optString("headers"),
                destination = o.optString("destination"),
                folderPath = o.optString("folderPath"),
                speedLimitKbps = o.optInt("speedLimitKbps", 0),
                priority = o.optInt("priority", 0),
                checksum = o.optString("checksum"),
                checksumVerified = o.optBoolean("checksumVerified", false),
                segments = parseSegments(o)
            )
        }.getOrNull()
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
            o.put("autoResume", item.autoResume)
            o.put("username", item.username)
            o.put("password", item.password)
            o.put("headers", item.headers)
            o.put("destination", item.destination)
            o.put("folderPath", item.folderPath)
            o.put("speedLimitKbps", item.speedLimitKbps)
            o.put("priority", item.priority)
            o.put("checksum", item.checksum)
            o.put("checksumVerified", item.checksumVerified)
            val segArr = JSONArray()
            item.segments.forEach { seg ->
                val so = JSONObject()
                so.put("index", seg.index)
                so.put("start", seg.start)
                so.put("end", seg.end)
                so.put("downloaded", seg.downloaded)
                segArr.put(so)
            }
            o.put("segments", segArr)
            arr.put(o)
        }
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    private fun parseSegments(o: JSONObject): List<DownloadSegment> {
        return runCatching {
            val segArr = o.getJSONArray("segments")
            buildList {
                for (j in 0 until segArr.length()) {
                    val so = segArr.getJSONObject(j)
                    add(
                        DownloadSegment(
                            index = so.getInt("index"),
                            start = so.getLong("start"),
                            end = so.getLong("end"),
                            downloaded = so.getLong("downloaded")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val KEY_ITEMS = "items"
    }
}
