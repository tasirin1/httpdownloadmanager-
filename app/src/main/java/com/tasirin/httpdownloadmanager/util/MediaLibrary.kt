package com.tasirin.httpdownloadmanager.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import java.io.File

object MediaLibrary {

    /** Koleksi MediaStore untuk root folder media (dipakai saat browsing). */
    fun mediaCollectionForRoot(root: String): Uri {
        return when (root.trim('/').substringBefore('/').lowercase()) {
            "pictures", "dcim" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            "movies" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
        }
    }

    /** Koleksi MediaStore untuk menyimpan file; fallback ke Downloads bila
     *  MIME tidak cocok dengan koleksi media (mis. APK ke Pictures). */
    fun mediaCollectionFor(relativePath: String?, mime: String): Uri {
        val root = relativePath?.trim('/')?.substringBefore('/').orEmpty().lowercase()
        return when {
            root == "pictures" || root == "dcim" ->
                if (mime.startsWith("image/")) MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                else MediaStore.Downloads.EXTERNAL_CONTENT_URI
            root == "movies" ->
                if (mime.startsWith("video/")) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else MediaStore.Downloads.EXTERNAL_CONTENT_URI
            else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
        }
    }

    data class MediaEntry(
        val name: String,
        val size: Long,
        val modified: Long,
        val isVideo: Boolean,
        val token: String,
        val filePath: String? = null,
        val contentUri: String? = null,
        val isPartial: Boolean = false,
        val progressPercent: Int = -1
    )

    private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    private val VIDEO_EXTS = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "mpg", "mpeg")

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

    fun scan(context: Context, partialProgress: Map<String, Int> = emptyMap()): List<MediaEntry> {
        val list = mutableListOf<MediaEntry>()

        fun addFile(f: File, isPartial: Boolean = false) {
            if (!f.isFile) return
            val name = if (isPartial) f.name.removeSuffix(".part") else f.name
            val kind = mediaKind(name) ?: return
            list.add(
                MediaEntry(
                    name = name,
                    size = f.length(),
                    modified = f.lastModified(),
                    isVideo = kind == "video",
                    token = tokenForPath(f.absolutePath),
                    filePath = f.absolutePath,
                    isPartial = isPartial,
                    progressPercent = if (isPartial) partialProgress[name] ?: -1 else -1
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

        // 3) Folder internal aplikasi (termasuk file .part yang masih berjalan)
        runCatching {
            val dir = File(context.filesDir, "downloads")
            dir.listFiles()?.forEach { f ->
                if (f.isFile && f.name.endsWith(".part")) {
                    addFile(f, isPartial = true)
                } else {
                    addFile(f)
                }
            }
        }

        // 4) SEMUA foto & video dari device lewat MediaStore (seluruh penyimpanan
        //    bersama: Download, DCIM, Pictures, dll), di semua versi Android.
        //    Urutan koleksi: gambar dulu lalu video.
        runCatching {
            val resolver = context.contentResolver
            val collections = listOf(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI to false,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI to true
            )
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.DATA
            )
            for ((collection, isVideo) in collections) {
                runCatching {
                    resolver.query(
                        collection, projection, null, null,
                        "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                    )?.use { c ->
                        val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                        val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                        val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                        val iMod = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                        val iData = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                        while (c.moveToNext()) {
                            val name = c.getString(iName) ?: continue
                            val uri = ContentUris.withAppendedId(collection, c.getLong(iId)).toString()
                            val dataPath = c.getString(iData)?.takeIf { it.isNotBlank() }
                            list.add(
                                MediaEntry(
                                    name = name,
                                    size = c.getLong(iSize),
                                    modified = c.getLong(iMod) * 1000L,
                                    isVideo = isVideo,
                                    token = tokenForUri(uri),
                                    filePath = dataPath,
                                    contentUri = uri
                                )
                            )
                        }
                    }
                }
            }
        }

        // 5) Folder Download publik (lama, untuk Android < 10 bila MediaStore
        //    tidak mengembalikan apa pun karena izin belum diberikan).
        if (Build.VERSION.SDK_INT < 29) {
            runCatching {
                val dir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                if (dir.isDirectory) dir.listFiles()?.forEach { addFile(it) }
            }
        }

        // Hapus duplikat: file yang sama bisa muncul sebagai path (f:) dan
        // sebagai MediaStore (u:) — dedupe berdasar path file bila ada.
        return list
            .distinctBy { it.filePath ?: it.contentUri ?: it.token }
            .sortedByDescending { it.modified }
    }
}
