package com.tasirin.httpdownloadmanager.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.tasirin.httpdownloadmanager.data.DownloadItem
import java.io.File
import java.io.IOException

class FileSaver(context: Context) {

    private val appContext = context.applicationContext
    private val downloadDir = File(appContext.filesDir, "downloads").apply { mkdirs() }
    private val customFolderUri = StoragePrefs.getFolderUri(appContext)

    data class PublishResult(val contentUri: String? = null, val filePath: String? = null)

    fun partialFile(fileName: String, segment: Int? = null): File {
        val suffix = if (segment != null) ".part.$segment" else ".part"
        return File(downloadDir, "$fileName$suffix")
    }

    fun partialFiles(item: DownloadItem): List<File> {
        if (item.segments.isEmpty()) {
            return listOf(partialFile(item.fileName))
        }
        return item.segments.map { partialFile(item.fileName, it.index) }
    }

    fun mergeSegments(fileName: String, segmentCount: Int): File {
        val target = partialFile(fileName)
        target.outputStream().use { out ->
            for (i in 0 until segmentCount) {
                val part = partialFile(fileName, i)
                if (!part.exists()) throw IOException("Segmen $i tidak ditemukan")
                part.inputStream().use { input -> input.copyTo(out) }
                part.delete()
            }
        }
        return target
    }

    fun publishToPath(partial: File, fileName: String, folder: String): PublishResult? {
        val dir = File(folder)
        if (!dir.isDirectory && !dir.mkdirs()) return null
        if (!dir.isDirectory) return null
        return runCatching {
            val target = File(dir, fileName)
            target.outputStream().use { out -> partial.inputStream().use { it.copyTo(out) } }
            partial.delete()
            PublishResult(filePath = target.absolutePath)
        }.getOrNull()
    }

    fun publish(partial: File, fileName: String, destination: String? = null): PublishResult {
        when (destination) {
            "download" -> {
                return if (Build.VERSION.SDK_INT >= 29) {
                    publishToMediaStore(partial, fileName)
                } else {
                    publishToPublicDir(partial, fileName)
                }
            }
            "internal" -> return publishToInternal(partial, fileName)
        }
        val folderUri = customFolderUri
        if (folderUri != null) {
            val result = publishToCustomFolder(partial, fileName, folderUri)
            if (result != null) return result
        }
        val textFolder = StoragePrefs.getTextFolder(appContext)
        if (textFolder != null) {
            val result = publishToTextFolder(partial, fileName, textFolder)
            if (result != null) return result
        }
        return if (Build.VERSION.SDK_INT >= 29) {
            publishToMediaStore(partial, fileName)
        } else {
            publishToPublicDir(partial, fileName)
        }
    }

    private fun publishToCustomFolder(
        partial: File,
        fileName: String,
        folderUri: Uri
    ): PublishResult? = runCatching {
        val tree = DocumentFile.fromTreeUri(appContext, folderUri) ?: return null
        val target = tree.findFile(fileName)
            ?: tree.createFile(MimeTypes.forFile(fileName), fileName)
            ?: return null
        val output = appContext.contentResolver.openOutputStream(target.uri, "wt")
            ?: return null
        output.use { out ->
            partial.inputStream().use { input -> input.copyTo(out) }
        }
        partial.delete()
        PublishResult(contentUri = target.uri.toString())
    }.getOrNull()

    private fun publishToTextFolder(
        partial: File,
        fileName: String,
        folder: String
    ): PublishResult? = runCatching {
        val dir = File(folder)
        if (!dir.isDirectory) return null
        runCatching { dir.mkdirs() }
        val target = File(dir, fileName)
        target.outputStream().use { out -> partial.inputStream().use { it.copyTo(out) } }
        partial.delete()
        PublishResult(filePath = target.absolutePath)
    }.getOrNull()

    fun freeBytes(): Long = runCatching {
        StatFs(downloadDir.absolutePath).availableBytes
    }.getOrDefault(Long.MAX_VALUE)

    fun destinationFreeBytes(): Long {
        val textFolder = StoragePrefs.getTextFolder(appContext)
        if (textFolder != null) {
            val dir = File(textFolder)
            if (dir.isDirectory) {
                return runCatching {
                    StatFs(dir.absolutePath).availableBytes
                }.getOrDefault(freeBytes())
            }
        }
        return freeBytes()
    }

    fun sidecarChecksum(item: DownloadItem): Pair<String, String>? {
        val path = item.filePath ?: return null
        val file = File(path)
        val parent = file.parentFile ?: return null
        val base = file.name
        val algos = mapOf(".md5" to "MD5", ".sha1" to "SHA-1", ".sha256" to "SHA-256")
        for ((ext, algo) in algos) {
            val side = File(parent, base + ext)
            if (side.exists()) {
                val first = runCatching {
                    side.readText().trim().split(Regex("\\s+")).firstOrNull().orEmpty()
                }.getOrDefault("")
                if (first.length >= 32) return algo to first.lowercase()
            }
        }
        return null
    }

    fun cleanupOrphanPartials(items: List<DownloadItem>) {
        runCatching {
            val expected = buildSet {
                items.forEach { item ->
                    if (item.segments.isEmpty()) {
                        add(partialFile(item.fileName).name)
                    } else {
                        item.segments.forEach { seg ->
                            add(partialFile(item.fileName, seg.index).name)
                        }
                    }
                }
            }
            downloadDir.listFiles()?.forEach { f ->
                val name = f.name
                if ((name.endsWith(".part") || name.contains(".part.")) && name !in expected) {
                    runCatching { f.delete() }
                }
            }
        }
    }

    private fun publishToMediaStore(partial: File, fileName: String): PublishResult {
        return publishToMediaStoreFolder(partial, fileName, null)
    }

    fun publishToMediaStoreFolder(
        partial: File,
        fileName: String,
        relativePath: String?
    ): PublishResult {
        val resolver = appContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, MimeTypes.forFile(fileName))
            relativePath?.let { rel ->
                put(MediaStore.Downloads.RELATIVE_PATH, rel.trim('/').trimEnd('/') + "/")
            }
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    partial.inputStream().use { it.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                partial.delete()
                return PublishResult(contentUri = uri.toString())
            } catch (_: Exception) {
                resolver.delete(uri, null, null)
            }
        }
        return publishToInternal(partial, fileName)
    }

    @Suppress("DEPRECATION")
    private fun publishToPublicDir(partial: File, fileName: String): PublishResult {
        val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            runCatching { publicDir.mkdirs() }
            if (publicDir.isDirectory && publicDir.canWrite()) {
                val target = File(publicDir, fileName)
                try {
                    target.outputStream().use { out -> partial.inputStream().use { it.copyTo(out) } }
                    partial.delete()
                    return PublishResult(filePath = target.absolutePath)
                } catch (_: Exception) {
                    // fallback ke penyimpanan internal
                }
            }
        }
        return publishToInternal(partial, fileName)
    }

    private fun publishToInternal(partial: File, fileName: String): PublishResult {
        val target = File(downloadDir, fileName)
        target.outputStream().use { out -> partial.inputStream().use { it.copyTo(out) } }
        partial.delete()
        return PublishResult(filePath = target.absolutePath)
    }

    fun deleteFiles(item: DownloadItem) {
        partialFiles(item).forEach { runCatching { it.delete() } }
        if (!item.contentUri.isNullOrEmpty()) {
            runCatching { appContext.contentResolver.delete(Uri.parse(item.contentUri), null, null) }
        }
        if (!item.filePath.isNullOrEmpty()) {
            runCatching { File(item.filePath).delete() }
        }
    }

    fun rename(item: DownloadItem, newName: String): Boolean {
        if (newName.isBlank() || newName == item.fileName) return false
        return runCatching {
            when {
                !item.contentUri.isNullOrEmpty() -> {
                    val uri = Uri.parse(item.contentUri)
                    if (Build.VERSION.SDK_INT >= 29 && uri.authority == MediaStore.AUTHORITY) {
                        val values = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, newName)
                        }
                        appContext.contentResolver.update(uri, values, null, null) > 0
                    } else {
                        DocumentsContract.renameDocument(appContext.contentResolver, uri, newName) != null
                    }
                }
                !item.filePath.isNullOrEmpty() -> {
                    val file = File(item.filePath)
                    val target = File(file.parentFile, newName)
                    file.exists() && file.renameTo(target)
                }
                else -> false
            }
        }.getOrDefault(false)
    }

    fun move(item: DownloadItem, destTreeUri: Uri): PublishResult? {
        return runCatching {
            val tree = DocumentFile.fromTreeUri(appContext, destTreeUri) ?: return null
            val target = tree.findFile(item.fileName)
                ?: tree.createFile(MimeTypes.forFile(item.fileName), item.fileName)
                ?: return null
            val input = when {
                !item.contentUri.isNullOrEmpty() ->
                    appContext.contentResolver.openInputStream(Uri.parse(item.contentUri))
                !item.filePath.isNullOrEmpty() -> File(item.filePath).inputStream()
                else -> null
            } ?: return null
            input.use { src ->
                val out = appContext.contentResolver.openOutputStream(target.uri, "wt")
                    ?: return null
                out.use { dst -> src.copyTo(dst) }
            }
            deleteFiles(item)
            PublishResult(contentUri = target.uri.toString())
        }.getOrNull()
    }

    fun organizeByType(result: PublishResult, fileName: String): PublishResult {
        if (result.contentUri == null && result.filePath == null) return result
        val sub = subfolderFor(fileName) ?: return result
        return runCatching {
            when {
                !result.contentUri.isNullOrEmpty() -> {
                    val uri = Uri.parse(result.contentUri)
                    if (Build.VERSION.SDK_INT >= 29 && uri.authority == MediaStore.AUTHORITY) {
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/$sub")
                        }
                        appContext.contentResolver.update(uri, values, null, null)
                        result
                    } else {
                        val doc = DocumentFile.fromSingleUri(appContext, uri) ?: return result
                        val parent = doc.parentFile ?: return result
                        val subDir = parent.findFile(sub)
                            ?: parent.createDirectory(sub)
                            ?: return result
                        val target = subDir.findFile(fileName)
                            ?: subDir.createFile(MimeTypes.forFile(fileName), fileName)
                            ?: return result
                        val input = appContext.contentResolver.openInputStream(uri) ?: return result
                        input.use { src ->
                            val out = appContext.contentResolver.openOutputStream(target.uri, "wt")
                                ?: return result
                            out.use { dst -> src.copyTo(dst) }
                        }
                        appContext.contentResolver.delete(uri, null, null)
                        PublishResult(contentUri = target.uri.toString())
                    }
                }
                !result.filePath.isNullOrEmpty() -> {
                    val file = File(result.filePath)
                    val parent = file.parentFile ?: return result
                    val subDir = File(parent, sub)
                    if (!subDir.isDirectory && !subDir.mkdirs()) return result
                    val target = File(subDir, file.name)
                    if (target.exists()) target.delete()
                    if (file.renameTo(target)) {
                        PublishResult(filePath = target.absolutePath)
                    } else {
                        result
                    }
                }
                else -> result
            }
        }.getOrDefault(result)
    }

    private fun subfolderFor(fileName: String): String? {
        val kind = MediaLibrary.mediaKind(fileName) ?: return null
        return when (kind) {
            "video" -> "Videos"
            "image" -> "Photos"
            else -> null
        }
    }

}
