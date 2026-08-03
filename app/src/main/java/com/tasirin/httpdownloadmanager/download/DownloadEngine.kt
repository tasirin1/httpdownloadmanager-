package com.tasirin.httpdownloadmanager.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Base64
import com.tasirin.httpdownloadmanager.data.DownloadItem
import com.tasirin.httpdownloadmanager.data.DownloadRepository
import com.tasirin.httpdownloadmanager.data.DownloadSegment
import com.tasirin.httpdownloadmanager.data.DownloadState
import com.tasirin.httpdownloadmanager.util.FileSaver
import com.tasirin.httpdownloadmanager.util.MimeTypes
import com.tasirin.httpdownloadmanager.util.StoragePrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.UUID

class DownloadEngine(private val context: Context) {

    private val repository = DownloadRepository(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    private val retryAttempts = mutableMapOf<String, Int>()
    private val speedTracker = SpeedTracker()

    @Volatile
    private var interruptedResumed = false

    private val _items = MutableStateFlow<List<DownloadItem>>(
        repository.load().sortedByDescending { it.addedAt }
    )
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    fun addDownload(
        url: String,
        fileName: String?,
        username: String = "",
        password: String = "",
        headers: String = "",
        speedLimitKbps: Int = 0,
        priority: Int = 0,
        checksum: String = ""
    ) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) return
        val customName = fileName?.trim().orEmpty()
        val name = customName.ifEmpty { guessFileName(cleanUrl) }
        val item = DownloadItem(
            id = UUID.randomUUID().toString(),
            url = cleanUrl,
            fileName = name,
            state = DownloadState.PENDING,
            bytesDownloaded = 0,
            totalBytes = 0,
            nameIsCustom = customName.isNotEmpty(),
            autoResume = true,
            username = username,
            password = password,
            headers = headers,
            speedLimitKbps = speedLimitKbps,
            priority = priority,
            checksum = checksum
        )
        update(_items.value + item)
        StoragePrefs.addRecentUrl(context, cleanUrl)
        attemptStart(item.id)
    }

    fun pause(id: String) {
        retryAttempts.remove(id)
        speedTracker.reset(id)
        jobs.remove(id)?.cancel()
        updateItem(id) {
            it.copy(state = DownloadState.PAUSED, autoResume = false, speedBps = 0, etaSeconds = 0)
        }
    }

    fun resume(id: String) {
        val item = _items.value.find { it.id == id } ?: return
        if (item.state != DownloadState.PAUSED && item.state != DownloadState.FAILED) return
        retryAttempts.remove(id)
        updateItem(id) { it.copy(state = DownloadState.PENDING, autoResume = true) }
        attemptStart(id)
    }

    fun resumeInterrupted() {
        if (interruptedResumed) return
        interruptedResumed = true
        _items.value.filter {
            it.autoResume && (it.state == DownloadState.PAUSED || it.state == DownloadState.PENDING)
        }.forEach { item ->
            updateItem(item.id) { it.copy(state = DownloadState.PENDING, autoResume = true) }
        }
        startQueued()
    }

    fun cancel(id: String) {
        retryAttempts.remove(id)
        speedTracker.reset(id)
        jobs.remove(id)?.cancel()
        updateItem(id) {
            it.copy(state = DownloadState.CANCELLED, speedBps = 0, etaSeconds = 0)
        }
    }

    fun remove(id: String) {
        retryAttempts.remove(id)
        speedTracker.reset(id)
        jobs.remove(id)?.cancel()
        _items.value.find { it.id == id }?.let { FileSaver(context).deleteFiles(it) }
        update(_items.value.filterNot { it.id == id })
    }

    fun clearCompleted() {
        val completed = _items.value.filter { it.state == DownloadState.COMPLETED }
        completed.forEach { FileSaver(context).deleteFiles(it) }
        val ids = completed.map { it.id }.toSet()
        update(_items.value.filterNot { ids.contains(it.id) })
    }

    fun cleanupOrphans() {
        FileSaver(context).cleanupOrphanPartials(_items.value)
    }

    fun rename(id: String, newName: String) {
        val item = _items.value.find { it.id == id } ?: return
        if (item.state != DownloadState.COMPLETED) return
        if (FileSaver(context).rename(item, newName)) {
            updateItem(id) { it.copy(fileName = newName) }
        }
    }

    fun setLimitAndPriority(id: String, speedLimitKbps: Int, priority: Int) {
        updateItem(id) { it.copy(speedLimitKbps = speedLimitKbps, priority = priority) }
    }

    fun move(id: String, destTreeUri: Uri) {
        val item = _items.value.find { it.id == id } ?: return
        if (item.state != DownloadState.COMPLETED) return
        val result = FileSaver(context).move(item, destTreeUri)
        if (result != null) {
            updateItem(id) {
                it.copy(contentUri = result.contentUri, filePath = result.filePath)
            }
        }
    }

    private fun attemptStart(id: String) {
        val item = _items.value.find { it.id == id } ?: return
        if (item.state != DownloadState.PENDING) return
        if (canStartNow()) {
            ensureServiceRunning()
            launchItem(item)
        }
    }

    private fun canStartNow(): Boolean {
        return jobs.values.count { it.isActive } < StoragePrefs.maxConcurrent(context)
    }

    private fun startQueued() {
        val max = StoragePrefs.maxConcurrent(context)
        val pending = _items.value
            .filter { it.state == DownloadState.PENDING }
            .sortedByDescending { it.priority }
        var active = jobs.values.count { it.isActive }
        if (active < max && pending.isNotEmpty()) {
            ensureServiceRunning()
        }
        for (item in pending) {
            if (active >= max) break
            launchItem(item)
            active++
        }
    }

    private fun launchItem(item: DownloadItem) {
        if (jobs[item.id]?.isActive == true) return
        val job = scope.launch {
            try {
                updateItem(item.id) { it.copy(state = DownloadState.DOWNLOADING) }
                runDownload(item)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Error runtime (mis. NoSuchMethodError) diubah jadi status FAILED,
                // bukan force close.
                handleFailure(item.id, e.message)
            }
        }
        jobs[item.id] = job
        job.invokeOnCompletion {
            jobs.remove(item.id)
            startQueued()
        }
    }

    private fun handleFailure(id: String, message: String?) {
        val item = _items.value.find { it.id == id } ?: return
        speedTracker.reset(id)
        val maxRetries = StoragePrefs.maxRetries(context)
        val attempts = (retryAttempts[id] ?: 0) + 1
        if (maxRetries > 0 && attempts <= maxRetries && item.autoResume) {
            retryAttempts[id] = attempts
            updateItem(id) { it.copy(state = DownloadState.PENDING, error = null) }
            scope.launch {
                delay(RETRY_DELAY_MS * attempts)
                if (_items.value.find { it.id == id }?.state == DownloadState.PENDING) {
                    attemptStart(id)
                }
            }
        } else {
            retryAttempts.remove(id)
            updateItem(id) {
                it.copy(
                    state = DownloadState.FAILED,
                    error = message,
                    autoResume = false,
                    speedBps = 0,
                    etaSeconds = 0
                )
            }
        }
    }

    private suspend fun runDownload(item: DownloadItem) {
        val saver = FileSaver(context)
        val freeNow = saver.freeBytes()
        if (freeNow < MIN_FREE_BYTES) {
            throw IOException(
                "Penyimpanan hampir penuh (sisa ${formatBytes(freeNow)})"
            )
        }
        val globalLimit = StoragePrefs.speedLimitKbps(context)
        val limit = if (item.speedLimitKbps > 0) item.speedLimitKbps else globalLimit
        val throttle = SpeedThrottle(limit)

        if (item.segments.isNotEmpty()) {
            runSegmented(item, saver, throttle, item.totalBytes, null)
            return
        }

        var useSegments = false
        var segmentedTotal = 0L
        var probeHeaders: ServerHeaders? = null
        val probe = URL(item.url).openConnection() as HttpURLConnection
        try {
            probe.requestMethod = "HEAD"
            probe.connectTimeout = 15_000
            probe.readTimeout = 30_000
            probe.setRequestProperty("User-Agent", "HttpDownloadManager/1.0")
            applyAuthHeaders(probe, item)
            probe.connect()
            val code = probe.responseCode
            if (code in 200..299) {
                probeHeaders = headersOf(probe)
                val total = contentLength(probe)
                val ranges = probe.getHeaderField("Accept-Ranges") == "bytes"
                if (ranges && total >= SEGMENT_MIN_BYTES) {
                    useSegments = true
                    segmentedTotal = total
                }
            }
        } catch (_: Exception) {
            // HEAD tidak didukung; lanjut dengan GET biasa
        } finally {
            probe.disconnect()
        }

        if (useSegments) {
            runSegmented(item, saver, throttle, segmentedTotal, probeHeaders)
            return
        }

        val conn = URL(item.url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "HttpDownloadManager/1.0")
            conn.setRequestProperty("Accept-Encoding", "identity")
            applyAuthHeaders(conn, item)
            runSingle(item, conn, saver, throttle)
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun runSingle(
        item: DownloadItem,
        conn: HttpURLConnection,
        saver: FileSaver,
        throttle: SpeedThrottle
    ) {
        var downloaded = item.bytesDownloaded
        var fileName = item.fileName
        var partialFile = saver.partialFile(fileName)
        if (downloaded > 0) conn.setRequestProperty("Range", "bytes=$downloaded-")
        conn.connect()

        val code = conn.responseCode
        if (code !in 200..299) throw IOException("HTTP $code")

        val resolvedName = resolveFinalName(item, headersOf(conn))
        if (resolvedName != fileName) {
            val newPartial = saver.partialFile(resolvedName)
            val keepOld = downloaded > 0 && partialFile.exists()
            val renamed = keepOld && partialFile.renameTo(newPartial)
            if (renamed || !keepOld) {
                if (!keepOld) partialFile.delete()
                partialFile = newPartial
                fileName = resolvedName
                updateItem(item.id) { it.copy(fileName = fileName) }
            }
        }

        val lengthHeader = contentLength(conn)
        var total = if (lengthHeader > 0) lengthHeader else 0L
        if (code == 206) {
            total += downloaded
        } else if (downloaded > 0) {
            // Server tidak mendukung resume; mulai dari awal.
            downloaded = 0
            partialFile.writeBytes(ByteArray(0))
        }
        if (total > 0 && saver.freeBytes() < total) {
            throw IOException(
                "Penyimpanan tidak cukup: butuh ${formatBytes(total)}, " +
                    "tersedia ${formatBytes(saver.freeBytes())}"
            )
        }

        throttle.reset(downloaded)
        val input = conn.inputStream
        val output = BufferedOutputStream(FileOutputStream(partialFile, true))
        val buffer = ByteArray(BUFFER_SIZE)
        var lastNotify = 0L
        try {
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                downloaded += read
                throttle.sleepIfNeeded(downloaded)
                val now = System.currentTimeMillis()
                if (now - lastNotify >= 250) {
                    lastNotify = now
                    val (speed, eta) = speedTracker.sample(item.id, downloaded, total)
                    updateItem(item.id) {
                        it.copy(
                            state = DownloadState.DOWNLOADING,
                            bytesDownloaded = downloaded,
                            totalBytes = total,
                            speedBps = speed,
                            etaSeconds = eta
                        )
                    }
                }
            }
            output.flush()
            coroutineContext.ensureActive()
        } finally {
            runCatching { input.close() }
            runCatching { output.close() }
        }

        verifySize(item.id, downloaded, total)

        val published = saver.publish(partialFile, fileName)
        verifyChecksum(item.id, fileName, published, saver)?.let {
            throw IOException(it)
        }
        speedTracker.reset(item.id)
        updateItem(item.id) {
            it.copy(
                state = DownloadState.COMPLETED,
                fileName = fileName,
                bytesDownloaded = downloaded,
                totalBytes = if (total > 0) total else downloaded,
                contentUri = published.contentUri,
                filePath = published.filePath,
                autoResume = false,
                speedBps = 0,
                etaSeconds = 0
            )
        }
    }

    private suspend fun runSegmented(
        item: DownloadItem,
        saver: FileSaver,
        throttle: SpeedThrottle,
        total: Long,
        headers: ServerHeaders?
    ) {
        var fileName = item.fileName
        var segments = item.segments
        if (segments.isEmpty()) {
            val resolvedName = resolveFinalName(item, headers)
            fileName = resolvedName
            segments = createSegments(total)
            if (total > 0 && saver.freeBytes() < total) {
                throw IOException(
                    "Penyimpanan tidak cukup: butuh ${formatBytes(total)}, " +
                        "tersedia ${formatBytes(saver.freeBytes())}"
                )
            }
            updateItem(item.id) {
                it.copy(
                    fileName = fileName,
                    totalBytes = total,
                    segments = segments,
                    bytesDownloaded = 0,
                    speedBps = 0,
                    etaSeconds = 0
                )
            }
        }

        throttle.reset(segments.sumOf { it.downloaded })
        coroutineScope {
            segments.forEach { seg ->
                launch {
                    downloadSegment(item.id, fileName, seg, saver, throttle)
                }
            }
        }

        val current = _items.value.find { it.id == item.id } ?: return
        verifySize(item.id, current.bytesDownloaded, current.totalBytes)

        val merged = saver.mergeSegments(fileName, segments.size)
        val published = saver.publish(merged, fileName)
        verifyChecksum(item.id, fileName, published, saver)?.let {
            throw IOException(it)
        }
        speedTracker.reset(item.id)
        updateItem(item.id) {
            it.copy(
                state = DownloadState.COMPLETED,
                fileName = fileName,
                bytesDownloaded = current.bytesDownloaded,
                totalBytes = current.totalBytes,
                contentUri = published.contentUri,
                filePath = published.filePath,
                segments = emptyList(),
                autoResume = false,
                speedBps = 0,
                etaSeconds = 0
            )
        }
    }

    private suspend fun downloadSegment(
        id: String,
        fileName: String,
        segment: DownloadSegment,
        saver: FileSaver,
        throttle: SpeedThrottle
    ) {
        val item = _items.value.find { it.id == id } ?: return
        val partial = saver.partialFile(fileName, segment.index)
        var downloaded = segment.downloaded
        val conn = URL(item.url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "HttpDownloadManager/1.0")
            conn.setRequestProperty("Accept-Encoding", "identity")
            applyAuthHeaders(conn, item)
            conn.setRequestProperty("Range", "bytes=${segment.start + downloaded}-${segment.end}")
            conn.connect()
            val code = conn.responseCode
            if (code != 206) throw IOException("Server tidak mendukung Range (HTTP $code)")

            val input = conn.inputStream
            val output = BufferedOutputStream(FileOutputStream(partial, true))
            val buffer = ByteArray(BUFFER_SIZE)
            var lastNotify = 0L
            try {
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    throttle.sleepIfNeeded(totalDownloaded(id))
                    val now = System.currentTimeMillis()
                    if (now - lastNotify >= 250) {
                        lastNotify = now
                        updateSegment(id, segment.index, downloaded)
                    }
                }
                output.flush()
                coroutineContext.ensureActive()
            } finally {
                runCatching { input.close() }
                runCatching { output.close() }
            }
            if (downloaded < (segment.end - segment.start + 1)) {
                throw IOException("Segmen ${segment.index} tidak lengkap")
            }
            updateSegment(id, segment.index, downloaded)
        } finally {
            conn.disconnect()
        }
    }

    @Synchronized
    private fun updateSegment(id: String, index: Int, downloaded: Long) {
        updateItem(id) { item ->
            val segs = item.segments.map { if (it.index == index) it.copy(downloaded = downloaded) else it }
            val totalDone = segs.sumOf { it.downloaded }
            val (speed, eta) = speedTracker.sample(id, totalDone, item.totalBytes)
            item.copy(
                segments = segs,
                bytesDownloaded = totalDone,
                speedBps = speed,
                etaSeconds = eta
            )
        }
    }

    private fun totalDownloaded(id: String): Long {
        return _items.value.find { it.id == id }?.segments?.sumOf { it.downloaded } ?: 0L
    }

    private fun verifySize(id: String, downloaded: Long, total: Long) {
        if (total > 0 && downloaded != total) {
            throw IOException("Ukuran tidak sesuai: diharapkan $total, diterima $downloaded")
        }
    }

    private fun applyAuthHeaders(conn: HttpURLConnection, item: DownloadItem) {
        if (item.username.isNotEmpty()) {
            val raw = "${item.username}:${item.password}"
            val encoded = Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            conn.setRequestProperty("Authorization", "Basic $encoded")
        }
        item.headers.split("\n".toRegex()).forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) {
                val key = line.substring(0, idx).trim()
                val value = line.substring(idx + 1).trim()
                if (key.isNotEmpty()) conn.setRequestProperty(key, value)
            }
        }
    }

    private fun createSegments(total: Long): List<DownloadSegment> {
        val count = SEGMENT_COUNT
        val size = total / count
        return (0 until count).map { i ->
            val start = i * size
            val end = if (i == count - 1) total - 1 else start + size - 1
            DownloadSegment(index = i, start = start, end = end, downloaded = 0)
        }
    }

    private fun parseChecksum(raw: String): Pair<String, String>? {
        val clean = raw.trim()
        if (clean.isEmpty()) return null
        val (algo, rest) = when {
            clean.startsWith("md5:", ignoreCase = true) -> "MD5" to clean.substring(4)
            clean.startsWith("sha1:", ignoreCase = true) -> "SHA-1" to clean.substring(5)
            clean.startsWith("sha256:", ignoreCase = true) -> "SHA-256" to clean.substring(7)
            else -> "MD5" to clean
        }
        val value = rest.trim().lowercase()
        if (value.length < 16) return null
        return algo to value
    }

    private fun verifyChecksum(
        itemId: String,
        fileName: String,
        published: FileSaver.PublishResult,
        saver: FileSaver
    ): String? {
        val current = _items.value.find { it.id == itemId } ?: return null
        val expected = parseChecksum(current.checksum) ?: saver.sidecarChecksum(
            current.copy(
                contentUri = published.contentUri ?: current.contentUri,
                filePath = published.filePath ?: current.filePath
            )
        ) ?: return null
        val (algo, hex) = expected
        val digest = computeDigest(published, algo, saver)
            ?: return "Tidak dapat membaca file untuk verifikasi checksum"
        if (!digest.equals(hex, ignoreCase = true)) {
            return "Checksum $algo tidak cocok (diharapkan $hex, didapat $digest)"
        }
        updateItem(itemId) { it.copy(checksumVerified = true) }
        return null
    }

    private fun computeDigest(
        published: FileSaver.PublishResult,
        algo: String,
        saver: FileSaver
    ): String? = runCatching {
        val input = when {
            !published.filePath.isNullOrEmpty() -> File(published.filePath).inputStream()
            !published.contentUri.isNullOrEmpty() ->
                context.contentResolver.openInputStream(Uri.parse(published.contentUri))
            else -> null
        } ?: return null
        input.use { stream ->
            val md = MessageDigest.getInstance(algo)
            val buf = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = stream.read(buf)
                if (read == -1) break
                md.update(buf, 0, read)
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }
    }.getOrNull()

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.2f GB".format(mb / 1024.0)
    }

    private fun contentLength(conn: HttpURLConnection): Long {
        return conn.getHeaderField("Content-Length")?.trim()?.toLongOrNull() ?: -1L
    }

    private fun headersOf(conn: HttpURLConnection): ServerHeaders {
        return ServerHeaders(
            contentDisposition = conn.getHeaderField("Content-Disposition"),
            contentType = conn.getHeaderField("Content-Type")
        )
    }

    private fun resolveFinalName(item: DownloadItem, headers: ServerHeaders?): String {
        var name = item.fileName
        if (!item.nameIsCustom) {
            val dispositionName = headers?.contentDisposition?.let { contentDispositionName(it) }
            if (!dispositionName.isNullOrBlank()) {
                name = sanitizeFileName(dispositionName)
            }
            if (name.isBlank()) {
                name = guessFileName(item.url)
            }
            val contentType = headers?.contentType?.substringBefore(';')?.trim().orEmpty()
            val ext = MimeTypes.extensionFor(contentType)
            if (ext != null && name.substringAfterLast('.', "").isEmpty() && !name.endsWith('.')) {
                name += ext
            }
            name = sanitizeFileName(name)
        }
        return name.takeIf { it.isNotBlank() } ?: item.fileName
    }

    private fun contentDispositionName(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val star = Regex("filename\\*=([^;]+)").find(header)
        if (star != null) {
            val value = star.groupValues[1].trim()
            val idx = value.indexOf("''")
            if (idx >= 0) {
                val decoded = runCatching {
                    URLDecoder.decode(value.substring(idx + 2), "UTF-8")
                }.getOrNull()
                if (!decoded.isNullOrBlank()) return decoded
            }
        }
        val plain = Regex("filename=\"?([^\";]+)\"?").find(header)
        return plain?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun sanitizeFileName(name: String): String {
        val clean = name.replace(Regex("[/\\]"), "_").trim()
        return clean.ifEmpty { "download" }
    }

    private fun ensureServiceRunning() {
        runCatching {
            val intent = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        // Jika foreground service gagal dimulai (mis. pembatasan Android 12+ saat
        // di latar belakang), download tetap dijalankan di proses aplikasi.
    }

    @Synchronized
    private fun updateItem(id: String, transform: (DownloadItem) -> DownloadItem) {
        update(_items.value.map { if (it.id == id) transform(it) else it })
    }

    @Synchronized
    private fun update(items: List<DownloadItem>) {
        _items.value = items.sortedByDescending { it.addedAt }
        repository.save(_items.value)
    }

    private fun guessFileName(url: String): String {
        val path = Uri.parse(url).lastPathSegment.orEmpty()
        val candidate = path.substringAfterLast('/').trim()
        return candidate.takeIf { it.isNotEmpty() && !it.contains('?') && !it.contains('=') }
            ?: "download_${System.currentTimeMillis()}"
    }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        private const val SEGMENT_MIN_BYTES = 5L * 1024 * 1024
        private const val SEGMENT_COUNT = 4
        private const val RETRY_DELAY_MS = 5_000L
        private const val MIN_FREE_BYTES = 2L * 1024 * 1024
    }
}

private data class ServerHeaders(
    val contentDisposition: String?,
    val contentType: String?
)

private class SpeedThrottle(private val limitKbps: Int) {
    private val lock = Any()
    private var startTime = System.currentTimeMillis()
    private var startBytes = 0L

    fun reset(start: Long) {
        synchronized(lock) {
            startTime = System.currentTimeMillis()
            startBytes = start
        }
    }

    fun sleepIfNeeded(totalDownloaded: Long) {
        if (limitKbps <= 0) return
        synchronized(lock) {
            val limit = limitKbps * 1024L
            val elapsed = System.currentTimeMillis() - startTime
            val expected = startBytes + (elapsed * limit) / 1000L
            if (totalDownloaded > expected) {
                val delayMs = ((totalDownloaded - expected) * 1000L) / limit
                Thread.sleep(delayMs)
            }
        }
    }
}

private class SpeedTracker {
    private val lastBytes = HashMap<String, Long>()
    private val lastTime = HashMap<String, Long>()

    @Synchronized
    fun sample(id: String, bytes: Long, total: Long): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val prevB = lastBytes[id] ?: bytes
        val prevT = lastTime[id] ?: now
        lastBytes[id] = bytes
        lastTime[id] = now
        val speed = if (now > prevT) ((bytes - prevB) * 1000L) / (now - prevT) else 0L
        val eta = if (speed > 0 && total > bytes) (total - bytes) / speed else 0L
        return speed to eta
    }

    @Synchronized
    fun reset(id: String) {
        lastBytes.remove(id)
        lastTime.remove(id)
    }
}
