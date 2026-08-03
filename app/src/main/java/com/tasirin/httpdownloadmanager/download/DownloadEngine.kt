package com.tasirin.httpdownloadmanager.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.tasirin.httpdownloadmanager.data.DownloadItem
import com.tasirin.httpdownloadmanager.data.DownloadRepository
import com.tasirin.httpdownloadmanager.data.DownloadState
import com.tasirin.httpdownloadmanager.util.FileSaver
import com.tasirin.httpdownloadmanager.util.MimeTypes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
import java.util.UUID

class DownloadEngine(private val context: Context) {

    private val repository = DownloadRepository(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()

    private val _items = MutableStateFlow<List<DownloadItem>>(
        repository.load().sortedByDescending { it.addedAt }
    )
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    fun addDownload(url: String, fileName: String?) {
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
            nameIsCustom = customName.isNotEmpty()
        )
        update(_items.value + item)
        start(item)
    }

    fun pause(id: String) {
        jobs.remove(id)?.cancel()
        updateItem(id) { it.copy(state = DownloadState.PAUSED) }
    }

    fun resume(id: String) {
        val item = _items.value.find { it.id == id } ?: return
        if (item.state != DownloadState.PAUSED && item.state != DownloadState.FAILED) return
        updateItem(item.id) { it.copy(state = DownloadState.PENDING) }
        start(item.copy(state = DownloadState.PENDING))
    }

    fun cancel(id: String) {
        jobs.remove(id)?.cancel()
        updateItem(id) { it.copy(state = DownloadState.CANCELLED) }
    }

    fun remove(id: String) {
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

    private fun start(item: DownloadItem) {
        ensureServiceRunning()
        if (jobs[item.id]?.isActive == true) return
        val job = scope.launch {
            try {
                updateItem(item.id) { it.copy(state = DownloadState.DOWNLOADING) }
                runDownload(item)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateItem(item.id) { it.copy(state = DownloadState.FAILED, error = e.message) }
            }
        }
        jobs[item.id] = job
        job.invokeOnCompletion { jobs.remove(item.id) }
    }

    private suspend fun runDownload(item: DownloadItem) {
        val saver = FileSaver(context)
        var downloaded = item.bytesDownloaded
        var fileName = item.fileName
        var partialFile = saver.partialFile(fileName)
        val conn = URL(item.url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "HttpDownloadManager/1.0")
            conn.setRequestProperty("Accept-Encoding", "identity")
            if (downloaded > 0) conn.setRequestProperty("Range", "bytes=$downloaded-")
            conn.connect()

            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")

            val resolvedName = resolveFinalName(item, conn)
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

            val lengthHeader = conn.getHeaderFieldLong("Content-Length", -1L)
            var total = if (lengthHeader > 0) lengthHeader else 0L
            if (code == 206) {
                total += downloaded
            } else if (downloaded > 0) {
                // Server tidak mendukung resume; mulai dari awal.
                downloaded = 0
                partialFile.writeBytes(ByteArray(0))
            }

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
                    val now = System.currentTimeMillis()
                    if (now - lastNotify >= 250) {
                        lastNotify = now
                        updateItem(item.id) {
                            it.copy(
                                state = DownloadState.DOWNLOADING,
                                bytesDownloaded = downloaded,
                                totalBytes = total
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

            val published = saver.publish(partialFile, fileName)
            updateItem(item.id) {
                it.copy(
                    state = DownloadState.COMPLETED,
                    fileName = fileName,
                    bytesDownloaded = downloaded,
                    totalBytes = if (total > 0) total else downloaded,
                    contentUri = published.contentUri,
                    filePath = published.filePath
                )
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun resolveFinalName(item: DownloadItem, conn: HttpURLConnection): String {
        var name = item.fileName
        if (!item.nameIsCustom) {
            val dispositionName = contentDispositionName(conn.getHeaderField("Content-Disposition"))
            if (!dispositionName.isNullOrBlank()) {
                name = sanitizeFileName(dispositionName)
            }
            if (name.isBlank()) {
                name = guessFileName(item.url)
            }
            val contentType = conn.getHeaderField("Content-Type")
                ?.substringBefore(';')?.trim().orEmpty()
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
        val clean = name.replace(Regex("[/\\\\]"), "_").trim()
        return clean.ifEmpty { "download" }
    }

    private fun ensureServiceRunning() {
        val intent = Intent(context, DownloadService::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun updateItem(id: String, transform: (DownloadItem) -> DownloadItem) {
        update(_items.value.map { if (it.id == id) transform(it) else it })
    }

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
    }
}
