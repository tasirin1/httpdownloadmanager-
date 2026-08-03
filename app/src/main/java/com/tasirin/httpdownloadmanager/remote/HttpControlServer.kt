package com.tasirin.httpdownloadmanager.remote

import android.content.Context
import com.tasirin.httpdownloadmanager.App
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URLDecoder

class HttpControlServer(private val context: Context) : NanoHTTPD(PORT) {

    @Volatile
    var lastError: String? = null
        private set

    override fun serve(session: IHTTPSession): Response {
        return try {
            when {
                session.method == Method.GET && session.uri == "/" -> htmlPage()
                session.method == Method.GET && session.uri == "/api/downloads" -> downloadsJson()
                session.method == Method.POST && session.uri == "/api/add" -> addDownload(session)
                session.method == Method.POST && session.uri == "/api/action" -> runAction(session)
                else -> newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    "text/plain; charset=utf-8",
                    "Not found"
                )
            }
        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "text/plain; charset=utf-8",
                "Error: ${e.message}"
            )
        }
    }

    fun startServer() {
        try {
            super.start()
            lastError = null
        } catch (e: IOException) {
            lastError = e.message
            throw e
        }
    }

    fun stopServer() {
        super.stop()
    }

    private fun htmlPage(): Response {
        val html = runCatching {
            context.assets.open("remote.html").bufferedReader().use { it.readText() }
        }.getOrDefault("<h1>Halaman remote tidak tersedia</h1>")
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
    }

    private fun downloadsJson(): Response {
        val arr = JSONArray()
        App.engine.items.value.forEach { item ->
            val o = JSONObject()
            o.put("id", item.id)
            o.put("fileName", item.fileName)
            o.put("url", item.url)
            o.put("state", item.state.name)
            o.put("bytesDownloaded", item.bytesDownloaded)
            o.put("totalBytes", item.totalBytes)
            o.put("progress", item.progressPercent)
            item.error?.let { o.put("error", it) }
            arr.put(o)
        }
        return jsonResponse(JSONObject().put("items", arr))
    }

    private fun addDownload(session: IHTTPSession): Response {
        val params = readForm(session)
        val url = params["url"]?.trim().orEmpty()
        if (url.isEmpty()) {
            return jsonResponse(JSONObject().put("ok", false).put("error", "url kosong"))
        }
        App.engine.addDownload(url, params["name"])
        return jsonResponse(JSONObject().put("ok", true))
    }

    private fun runAction(session: IHTTPSession): Response {
        val params = readForm(session)
        val id = params["id"].orEmpty()
        if (id.isEmpty()) return jsonResponse(JSONObject().put("ok", false))
        when (params["action"]) {
            "pause" -> App.engine.pause(id)
            "resume" -> App.engine.resume(id)
            "cancel" -> App.engine.cancel(id)
            "delete" -> App.engine.remove(id)
            else -> return jsonResponse(
                JSONObject().put("ok", false).put("error", "aksi tidak dikenal")
            )
        }
        return jsonResponse(JSONObject().put("ok", true))
    }

    private fun readForm(session: IHTTPSession): Map<String, String> {
        val map = mutableMapOf<String, String>()
        session.parms.forEach { (k, v) -> map[k] = v }
        val length = session.headers["content-length"]?.toIntOrNull() ?: 0
        if (length > 0) {
            val bytes = ByteArray(length)
            var offset = 0
            while (offset < length) {
                val read = session.inputStream.read(bytes, offset, length - offset)
                if (read == -1) break
                offset += read
            }
            val body = String(bytes, 0, offset, Charsets.UTF_8)
            body.split("&").forEach { pair ->
                val idx = pair.indexOf('=')
                if (idx > 0) {
                    val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                    val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                    map[key] = value
                }
            }
        }
        return map
    }

    private fun jsonResponse(obj: JSONObject): Response {
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json; charset=utf-8",
            obj.toString()
        )
    }

    companion object {
        const val PORT = 8080

        fun ipv4Addresses(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().flatMap { ni ->
                ni.inetAddresses.toList()
                    .filter { it is Inet4Address && !it.isLoopbackAddress }
                    .map { it.hostAddress.orEmpty() }
            }
        }.getOrDefault(emptyList())
    }
}
