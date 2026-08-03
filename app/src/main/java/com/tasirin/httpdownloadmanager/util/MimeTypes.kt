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
}
