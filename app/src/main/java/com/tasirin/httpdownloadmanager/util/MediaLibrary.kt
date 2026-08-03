package com.tasirin.httpdownloadmanager.util

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import java.io.File

object MediaLibrary {

    data class MediaEntry(
        val name: String,
        val size: Long,
        val modified: Long,
        val isVideo: Boolean,
        val token: String,
        val filePath: String? = null,
        val contentUri: String? = null
    )

    private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    private val VIDEO_EXTS = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "mpg", "mpeg")

    fun isMedia(name: String): Boolean = mediaKind(name) != null

    fun mediaKind(name: String): String? {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            IMAGE_EXTS.contains(ext) -> "image"
            VIDEO_EXTS.contains(ext) -> "video"
            else -> null
        }
    }

    fun tokenForPath(path: String): String =
        Base64.encodeToString("f:$path".toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)

    fun tokenForUri(uri: String): String =
        Base64.encodeToString("u:$uri".toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)

    fun decodeToken(token: String): String? = runCatching {
        String(
            Base64.decode(token, Base64.URL_SAFE or Base64.NO_WRAP),
            Charsets.UTF_8
        )
    }.getOrNull()

    fun scan(context: Context): List<MediaEntry> {
        val list = mutableListOf<MediaEntry>()

        fun addFile(f: File) {
            if (!f.isFile) return
            val kind = mediaKind(f.name) ?: return
            list.add(
                MediaEntry(
                    name = f.name,
                    size = f.length(),
                    modified = f.lastModified(),
                    isVideo = kind == "video",
                    token = tokenForPath(f.absolutePath),
                    filePath = f.absolutePath
                )
            )
        }

        fun addDoc(df: DocumentFile) {
            if (!df.isFile) return
            val name = df.name ?: return
            val kind = mediaKind(name) ?: return
            val uri = df.uri.toString()
            list.add(
                MediaEntry(
                    name = name,
                    size = runCatching { df.length() }.getOrDefault(0L),
                    modified = runCatching { df.lastModified() }.getOrDefault(0L),
                    isVideo = kind == "video",
                    token = tokenForUri(uri),
                    contentUri = uri
                )
            )
        }

        // 1) Folder teks (Android 5-7 / folder kustom lewat path)
        StoragePrefs.getTextFolder(context)?.let { tf ->
            runCatching { File(tf).listFiles()?.forEach { addFile(it) } }
        }

        // 2) Folder kustom (SAF tree)
        StoragePrefs.getFolderUri(context)?.let { uri ->
            runCatching {
                DocumentFile.fromTreeUri(context, uri)?.listFiles()?.forEach { addDoc(it) }
            }
        }

        // 3) Folder internal aplikasi
        runCatching {
            File(context.filesDir, "downloads").listFiles()?.forEach { addFile(it) }
        }

        // 4) Folder Download publik (lama) / MediaStore (baru)
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching {
                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val projection = arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_MODIFIED
                )
                resolver.query(
                    collection, projection, null, null,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )?.use { c ->
                    val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val iMod = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                    while (c.moveToNext()) {
                        val name = c.getString(iName) ?: continue
                        val kind = mediaKind(name) ?: continue
                        val uri = ContentUris.withAppendedId(collection, c.getLong(iId)).toString()
                        list.add(
                            MediaEntry(
                                name = name,
                                size = c.getLong(iSize),
                                modified = c.getLong(iMod) * 1000L,
                                isVideo = kind == "video",
                                token = tokenForUri(uri),
                                contentUri = uri
                            )
                        )
                    }
                }
            }
        } else {
            runCatching {
                val dir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                if (dir.isDirectory) dir.listFiles()?.forEach { addFile(it) }
            }
        }

        return list
            .distinctBy { it.token }
            .sortedByDescending { it.modified }
            .take(300)
    }
}
