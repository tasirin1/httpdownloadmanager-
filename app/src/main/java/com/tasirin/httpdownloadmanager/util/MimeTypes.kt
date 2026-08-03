package com.tasirin.httpdownloadmanager.util

object MimeTypes {

    fun forFile(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "apk" -> "application/vnd.android.package-archive"
            "pdf" -> "application/pdf"
            "zip", "rar", "7z", "tar", "gz", "xz" -> "application/zip"
            "mp3", "m4a", "aac", "wav", "ogg", "flac" -> "audio/*"
            "mp4", "mkv", "3gp", "webm", "avi", "mov" -> "video/*"
            "jpg", "jpeg", "png", "gif", "webp", "bmp" -> "image/*"
            "txt", "md", "log", "csv" -> "text/plain"
            "html", "htm" -> "text/html"
            "json" -> "application/json"
            "xml" -> "application/xml"
            else -> "application/octet-stream"
        }
    }

    fun extensionFor(contentType: String?): String? {
        val mime = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        return when (mime) {
            "application/pdf" -> ".pdf"
            "application/zip" -> ".zip"
            "application/x-rar-compressed" -> ".rar"
            "application/x-7z-compressed" -> ".7z"
            "application/json" -> ".json"
            "application/xml", "text/xml" -> ".xml"
            "application/vnd.android.package-archive" -> ".apk"
            "image/jpeg" -> ".jpg"
            "image/png" -> ".png"
            "image/gif" -> ".gif"
            "image/webp" -> ".webp"
            "audio/mpeg", "audio/mp3" -> ".mp3"
            "audio/mp4" -> ".m4a"
            "audio/ogg", "audio/opus" -> ".ogg"
            "video/mp4" -> ".mp4"
            "video/x-matroska" -> ".mkv"
            "video/webm" -> ".webm"
            "text/plain" -> ".txt"
            "text/html" -> ".html"
            "text/csv" -> ".csv"
            else -> null
        }
    }
}
