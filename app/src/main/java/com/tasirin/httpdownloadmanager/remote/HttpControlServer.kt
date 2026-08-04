package com.tasirin.httpdownloadmanager.remote

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import android.os.BatteryManager
import android.util.Log
import com.tasirin.httpdownloadmanager.App
import com.tasirin.httpdownloadmanager.data.DownloadState
import com.tasirin.httpdownloadmanager.util.MediaLibrary
import com.tasirin.httpdownloadmanager.util.MimeTypes
import com.tasirin.httpdownloadmanager.util.StoragePrefs
import androidx.documentfile.provider.DocumentFile
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.FileOutputStream
import java.net.Inet4Address
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.net.NetworkInterface
import java.net.URLDecoder

class HttpControlServer(private val context: Context) : NanoHTTPD(StoragePrefs.serverPort(context)) {

    @Volatile
    var lastError: String? = null
        private set

    override fun serve(session: IHTTPSession): Response {
        return try {
            when {
                session.method == Method.POST && session.uri == "/api/login" -> login(session)
                pinOk(session) -> when {
                    session.method == Method.GET && session.uri == "/" -> htmlPage()
                    session.method == Method.GET && session.uri == "/api/downloads" -> downloadsJson()
                    session.method == Method.GET && session.uri == "/api/status" -> statusJson()
                    session.method == Method.GET && session.uri == "/api/gallery" -> galleryJson()
                    session.method == Method.GET && session.uri == "/api/thumb" -> serveThumb(session)
                    session.method == Method.GET && session.uri == "/api/media" -> serveMedia(session)
                    session.method == Method.POST && session.uri == "/api/add" -> addDownload(session)
                    session.method == Method.POST && session.uri == "/api/upload" -> handleUpload(session)
                    session.method == Method.POST && session.uri == "/api/action" -> runAction(session)
                    session.method == Method.GET && session.uri.startsWith("/file/") -> serveFile(session)
                    session.method == Method.GET && session.uri == "/api/log" -> crashLog()
                    else -> newFixedLengthResponse(
                        Response.Status.NOT_FOUND,
                        "text/plain; charset=utf-8",
                        "Not found"
                    )
                }
                session.method == Method.GET && session.uri == "/" -> loginPage("")
                else -> unauthorized()
            }
        } catch (e: Exception) {
            logError(e)
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

    private fun pinEnabled(): Boolean =
        !StoragePrefs.getServerPin(context).isNullOrEmpty()

    private fun pinOk(session: IHTTPSession): Boolean {
        if (!pinEnabled()) return true
        val expected = sha256(StoragePrefs.getServerPin(context).orEmpty())
        val cookie = session.headers["cookie"] ?: return false
        return cookie.split(";").any {
            it.trim().startsWith("dm_pin=$expected")
        }
    }

    private fun sha256(value: String): String = runCatching {
        val md = MessageDigest.getInstance("SHA-256")
        md.digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }.getOrDefault("")

    private fun login(session: IHTTPSession): Response {
        val params = readForm(session)
        val pin = params["pin"].orEmpty()
        val stored = StoragePrefs.getServerPin(context).orEmpty()
        return if (stored.isNotEmpty() && pin == stored) {
            val r = newFixedLengthResponse(
                Response.Status.REDIRECT,
                "text/html",
                "<html><body>OK</body></html>"
            )
            r.addHeader("Set-Cookie", "dm_pin=${sha256(stored)}; Max-Age=2592000; Path=/")
            r.addHeader("Location", "/")
            r
        } else {
            loginPage("PIN salah, coba lagi.")
        }
    }

    private fun loginPage(error: String): Response {
        val html = """<!DOCTYPE html>
<html lang="id">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>PIN Diperlukan</title>
<style>
  * { box-sizing: border-box; }
  body { font-family: system-ui, -apple-system, sans-serif; background: #f2f4f8; margin: 0; padding: 24px; color: #1c1c1c; }
  .box { background: #fff; border-radius: 12px; padding: 24px; max-width: 360px; margin: 40px auto; box-shadow: 0 1px 3px rgba(0,0,0,.1); }
  h1 { font-size: 20px; margin: 0 0 8px; }
  p { margin: 4px 0 12px; font-size: 14px; color: #555; }
  input, button { font-size: 16px; padding: 12px; border-radius: 8px; border: 1px solid #cbd2dd; width: 100%; margin-bottom: 10px; }
  button { background: #0D47A1; color: #fff; border: none; cursor: pointer; }
  .err { color: #b00020; font-size: 13px; }
</style>
</head>
<body>
<div class="box">
  <h1>&#11015; Download Manager</h1>
  <p>Masukkan PIN untuk mengakses server remote.</p>
  <form method="POST" action="/api/login">
    <input type="password" name="pin" placeholder="PIN" autofocus>
    <button type="submit">Masuk</button>
  </form>
  <div class="err">$error</div>
</div>
</body>
</html>"""
        return newFixedLengthResponse(
            Response.Status.OK, "text/html; charset=utf-8", html
        )
    }

    private fun unauthorized(): Response = newFixedLengthResponse(
        Response.Status.UNAUTHORIZED,
        "application/json; charset=utf-8",
        JSONObject().put("ok", false).put("error", "PIN diperlukan").toString()
    )

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
            o.put("speedBps", item.speedBps)
            o.put("etaSeconds", item.etaSeconds)
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
        val speed = params["speedLimitKbps"]?.toIntOrNull()?.coerceIn(0, 100_000) ?: 0
        val priority = params["priority"]?.toIntOrNull()?.coerceIn(-1, 1) ?: 0
        val checksum = params["checksum"]?.trim().orEmpty()
        val storage = params["storage"]?.trim().orEmpty()
        val folderPath = params["path"]?.trim().orEmpty()
        App.engine.addDownload(
            url, params["name"],
            speedLimitKbps = speed,
            priority = priority,
            checksum = checksum,
            destination = storage,
            folderPath = folderPath
        )
        return jsonResponse(JSONObject().put("ok", true))
    }

    private fun handleUpload(session: IHTTPSession): Response {
        val name = session.parms["name"]?.trim()
            ?.replace("/", "_")?.replace("\\", "_")?.replace("\"", "_")
            ?.takeIf { it.isNotEmpty() }
            ?: "upload_${System.currentTimeMillis()}"
        val storage = session.parms["storage"]?.trim().orEmpty()
        val folderPath = session.parms["path"]?.trim().orEmpty()
        val length = (session.headers["content-length"]?.toLongOrNull() ?: 0L)
        if (length <= 0 || length > MAX_UPLOAD_BYTES) {
            return jsonResponse(
                JSONObject().put("ok", false)
                    .put("error", "Ukuran tidak valid (maks ${MAX_UPLOAD_MB} MB)")
            )
        }
        if (App.engine.freeSpaceBytes() < length) {
            return jsonResponse(
                JSONObject().put("ok", false)
                    .put("error", "Penyimpanan tidak cukup untuk upload")
            )
        }
        return runCatching {
            val tmp = File.createTempFile("upload", ".tmp", context.cacheDir)
            session.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var remaining = length
                    while (remaining > 0) {
                        val chunk = minOf(buffer.size.toLong(), remaining).toInt()
                        val read = input.read(buffer, 0, chunk)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        remaining -= read
                    }
                }
            }
            App.engine.importFile(name, tmp, storage, folderPath)
            jsonResponse(JSONObject().put("ok", true).put("name", name))
        }.getOrElse {
            jsonResponse(JSONObject().put("ok", false).put("error", it.message ?: "gagal upload"))
        }
    }

    private fun runAction(session: IHTTPSession): Response {
        val params = readForm(session)
        val id = params["id"].orEmpty()
        when (params["action"]) {
            "pause" -> {
                if (id.isEmpty()) return jsonResponse(JSONObject().put("ok", false))
                App.engine.pause(id)
            }
            "resume" -> {
                if (id.isEmpty()) return jsonResponse(JSONObject().put("ok", false))
                App.engine.resume(id)
            }
            "cancel" -> {
                if (id.isEmpty()) return jsonResponse(JSONObject().put("ok", false))
                App.engine.cancel(id)
            }
            "delete" -> {
                if (id.isEmpty()) return jsonResponse(JSONObject().put("ok", false))
                App.engine.remove(id)
            }
            "pause_all" -> App.engine.pauseAll()
            "resume_all" -> App.engine.resumeAll()
            "retry_failed" -> App.engine.retryFailed()
            "clear_completed" -> App.engine.clearCompleted()
            else -> return jsonResponse(
                JSONObject().put("ok", false).put("error", "aksi tidak dikenal")
            )
        }
        return jsonResponse(JSONObject().put("ok", true))
    }

    private fun serveFile(session: IHTTPSession): Response {
        val id = session.uri.removePrefix("/file/")
        val item = App.engine.items.value.find {
            it.id == id && it.state == DownloadState.COMPLETED
        } ?: return notFound()
        val download = session.parms["dl"] == "1"
        val mime = MimeTypes.forFile(item.fileName)
        val input: InputStream
        val total: Long
        if (!item.filePath.isNullOrEmpty()) {
            val file = File(item.filePath)
            if (!file.exists() || !file.isFile) return notFound()
            input = FileInputStream(file)
            total = file.length()
        } else if (!item.contentUri.isNullOrEmpty()) {
            val uri = Uri.parse(item.contentUri)
            val resolver = context.contentResolver
            val stream = resolver.openInputStream(uri) ?: return notFound()
            val len = resolver.openAssetFileDescriptor(uri, "r")?.length ?: -1L
            input = stream
            total = len
        } else {
            return notFound()
        }

        return streamMedia(
            name = item.fileName,
            mime = mime,
            input = input,
            total = total,
            rangeHeader = session.headers["range"] ?: session.headers["Range"],
            download = download
        )
    }

    private fun serveThumb(session: IHTTPSession): Response {
        val token = session.parms["token"].orEmpty()
        if (token.isEmpty()) return notFound()
        val raw = MediaLibrary.decodeToken(token) ?: return notFound()
        return runCatching {
            val thumb = getOrCreateThumb(raw)
            if (thumb == null) {
                notFound()
            } else {
                newFixedLengthResponse(
                    Response.Status.OK,
                    "image/jpeg",
                    FileInputStream(thumb),
                    thumb.length()
                ).also { it.addHeader("Cache-Control", "public, max-age=86400") }
            }
        }.getOrElse { notFound() }
    }

    private fun getOrCreateThumb(raw: String): File? {
        val key = sha256(raw).take(16)
        val dir = File(context.cacheDir, "thumbs").apply { runCatching { mkdirs() } }
        if (!dir.isDirectory) return null
        val cached = File(dir, "$key.jpg")
        if (cached.isFile && cached.length() > 0) return cached
        val bmp = generateThumb(raw) ?: return null
        return runCatching {
            val out = FileOutputStream(cached)
            bmp.compress(Bitmap.CompressFormat.JPEG, 72, out)
            out.close()
            bmp.recycle()
            cached
        }.getOrNull()
    }

    private fun generateThumb(raw: String): Bitmap? {
        return runCatching {
            when {
                raw.startsWith("f:") -> {
                    val file = File(raw.substring(2))
                    if (!file.isFile) return null
                    if (MediaLibrary.mediaKind(file.name) == "video") {
                        videoThumb(path = file.absolutePath)
                    } else {
                        imageThumb(path = file.absolutePath)
                    }
                }
                raw.startsWith("u:") -> {
                    val uri = Uri.parse(raw.substring(2))
                    val name = DocumentFile.fromSingleUri(context, uri)?.name.orEmpty()
                    if (MediaLibrary.mediaKind(name) == "video") {
                        videoThumb(uri = uri)
                    } else {
                        imageThumb(uri = uri)
                    }
                }
                else -> null
            }
        }.getOrNull()
    }

    private fun videoThumb(path: String? = null, uri: Uri? = null): Bitmap? {
        if (path == null && uri == null) return null
        val mmr = MediaMetadataRetriever()
        return try {
            if (path != null) {
                mmr.setDataSource(path)
            } else {
                mmr.setDataSource(context, uri)
            }
            val frame = mmr.getFrameAtTime(
                1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return null
            scaleDown(frame, 480)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { mmr.release() }
        }
    }

    private fun imageThumb(path: String? = null, uri: Uri? = null): Bitmap? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (path != null) {
                BitmapFactory.decodeFile(path, bounds)
            } else {
                uri?.let {
                    context.contentResolver.openInputStream(it)?.use { s ->
                        BitmapFactory.decodeStream(s, null, bounds)
                    }
                }
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 480 &&
                bounds.outHeight / (sample * 2) >= 480
            ) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = if (path != null) {
                BitmapFactory.decodeFile(path, opts)
            } else {
                uri?.let {
                    context.contentResolver.openInputStream(it)?.use { s ->
                        BitmapFactory.decodeStream(s, null, opts)
                    }
                }
            } ?: return null
            scaleDown(bmp, 480)
        }.getOrNull()
    }

    private fun scaleDown(src: Bitmap, max: Int): Bitmap {
        if (src.width <= max && src.height <= max) return src
        val scale = max.toDouble() / maxOf(src.width, src.height)
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        val out = Bitmap.createScaledBitmap(src, w, h, true)
        if (out !== src) src.recycle()
        return out
    }

    private fun serveMedia(session: IHTTPSession): Response {
        val token = session.parms["token"].orEmpty()
        if (token.isEmpty()) return notFound()
        val raw = MediaLibrary.decodeToken(token) ?: return notFound()
        val download = session.parms["dl"] == "1"
        val input: InputStream
        val total: Long
        val name: String
        when {
            raw.startsWith("f:") -> {
                val file = File(raw.substring(2))
                if (!file.isFile) return notFound()
                input = FileInputStream(file)
                total = file.length()
                name = file.name
            }
            raw.startsWith("u:") -> {
                val uri = Uri.parse(raw.substring(2))
                val resolver = context.contentResolver
                val stream = resolver.openInputStream(uri) ?: return notFound()
                val len = resolver.openAssetFileDescriptor(uri, "r")?.length ?: -1L
                input = stream
                total = len
                name = DocumentFile.fromSingleUri(context, uri)?.name ?: "media"
            }
            else -> return notFound()
        }
        return streamMedia(
            name = name,
            mime = MimeTypes.forFile(name),
            input = input,
            total = total,
            rangeHeader = session.headers["range"] ?: session.headers["Range"],
            download = download
        )
    }

    private fun streamMedia(
        name: String,
        mime: String,
        input: InputStream,
        total: Long,
        rangeHeader: String?,
        download: Boolean
    ): Response {
        val safeName = name.replace("\"", "_").replace("\\", "_")
        val disposition = if (download) {
            "attachment; filename=\"$safeName\""
        } else {
            "inline; filename=\"$safeName\""
        }
        val response = runCatching {
            val range = if (total > 0) parseRange(rangeHeader, total) else null
            when {
                range != null -> {
                    val (start, end) = range
                    val partLen = end - start + 1
                    if (start > 0) skipFully(input, start)
                    newFixedLengthResponse(
                        Response.Status.PARTIAL_CONTENT, mime, input, partLen
                    ).also {
                        it.addHeader("Content-Range", "bytes $start-$end/$total")
                    }
                }
                total > 0 -> newFixedLengthResponse(Response.Status.OK, mime, input, total)
                else -> newChunkedResponse(Response.Status.OK, mime, input)
            }
        }.getOrElse {
            runCatching { input.close() }
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "text/plain; charset=utf-8",
                "Error: ${it.message}"
            )
        }
        response.addHeader("Accept-Ranges", "bytes")
        response.addHeader("Content-Disposition", disposition)
        return response
    }

    private fun notFound(): Response = newFixedLengthResponse(
        Response.Status.NOT_FOUND,
        "text/plain; charset=utf-8",
        "File tidak ditemukan"
    )

    private fun parseRange(header: String?, total: Long): Pair<Long, Long>? {
        if (header.isNullOrBlank() || total <= 0) return null
        val m = Regex("bytes=(\\d*)-(\\d*)").find(header) ?: return null
        val start = m.groupValues[1].toLongOrNull()
        val endRaw = m.groupValues[2].toLongOrNull()
        return when {
            start != null -> {
                val s = start.coerceIn(0, total - 1)
                val e = (endRaw ?: (total - 1)).coerceIn(s, total - 1)
                s to e
            }
            endRaw != null -> {
                val n = endRaw.coerceAtLeast(1)
                (total - n).coerceAtLeast(0) to (total - 1)
            }
            else -> null
        }
    }

    private fun skipFully(input: InputStream, n: Long) {
        var remaining = n
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() == -1) return
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private fun readForm(session: IHTTPSession): Map<String, String> {
        val map = mutableMapOf<String, String>()
        session.parms.forEach { (k, v) -> map[k] = v }
        val length = (session.headers["content-length"]?.toLongOrNull() ?: 0L)
            .coerceIn(0L, MAX_BODY_SIZE).toInt()
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

    private fun galleryJson(): Response {
        val arr = JSONArray()
        MediaLibrary.scan(context).forEach { e ->
            val o = JSONObject()
            o.put("name", e.name)
            o.put("size", e.size)
            o.put("modified", e.modified)
            o.put("isVideo", e.isVideo)
            o.put("token", e.token)
            arr.put(o)
        }
        return jsonResponse(JSONObject().put("items", arr))
    }

    private fun statusJson(): Response {
        val obj = JSONObject()
        val (level, charging) = batteryStatus()
        obj.put("batteryPercent", level)
        obj.put("batteryCharging", charging)
        obj.put("storageFree", App.engine.freeSpaceBytes())
        obj.put("port", listeningPort)
        return jsonResponse(obj)
    }

    private fun batteryStatus(): Pair<Int, Boolean> = runCatching {
        val intent = context.registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        pct to charging
    }.getOrDefault(-1 to false)

    private fun crashLog(): Response {
        val text = runCatching {
            val file = File(context.filesDir, App.CRASH_LOG_FILE)
            if (file.exists()) file.readText() else "Belum ada log error."
        }.getOrDefault("Tidak dapat membaca log.")
        return newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", text)
    }

    private fun logError(e: Exception) {
        runCatching {
            val file = File(context.filesDir, App.CRASH_LOG_FILE)
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val text = buildString {
                appendLine("=== $stamp [serve] ===")
                appendLine(Log.getStackTraceString(e))
                appendLine()
            }
            val existing = if (file.exists()) file.readText() else ""
            file.writeText((existing + text).takeLast(100_000))
        }
    }

    private fun jsonResponse(obj: JSONObject): Response {
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json; charset=utf-8",
            obj.toString()
        )
    }

    companion object {
        const val DEFAULT_PORT = StoragePrefs.DEFAULT_PORT
        private const val MAX_BODY_SIZE = 1_048_576L
        private const val MAX_UPLOAD_BYTES = 2L * 1024 * 1024 * 1024
        private const val MAX_UPLOAD_MB = 2048

        fun ipv4Addresses(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList().flatMap { ni ->
                ni.inetAddresses.toList()
                    .filter { it is Inet4Address && !it.isLoopbackAddress }
                    .map { it.hostAddress.orEmpty() }
            }
        }.getOrDefault(emptyList())
    }
}
