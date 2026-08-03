package com.tasirin.downloadmanager.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.tasirin.downloadmanager.data.DownloadItem
import java.io.File

class FileSaver(context: Context) {

    private val appContext = context.applicationContext
    private val downloadDir = File(appContext.filesDir, "downloads").apply { mkdirs() }
    private val customFolderUri = StoragePrefs.getFolderUri(appContext)

    data class PublishResult(val contentUri: String? = null, val filePath: String? = null)

    fun partialFile(fileName: String): File = File(downloadDir, "$fileName.part")

    fun publish(partial: File, fileName: String): PublishResult {
        val folderUri = customFolderUri
        if (folderUri != null) {
            val result = publishToCustomFolder(partial, fileName, folderUri)
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

    private fun publishToMediaStore(partial: File, fileName: String): PublishResult {
        val resolver = appContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, MimeTypes.forFile(fileName))
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
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED && publicDir.canWrite()) {
            val target = File(publicDir, fileName)
            try {
                target.outputStream().use { out -> partial.inputStream().use { it.copyTo(out) } }
                partial.delete()
                return PublishResult(filePath = target.absolutePath)
            } catch (_: Exception) {
                // fallback ke penyimpanan internal
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
        runCatching { partialFile(item.fileName).delete() }
        if (!item.contentUri.isNullOrEmpty()) {
            runCatching { appContext.contentResolver.delete(Uri.parse(item.contentUri), null, null) }
        }
        if (!item.filePath.isNullOrEmpty()) {
            runCatching { File(item.filePath).delete() }
        }
    }
}
