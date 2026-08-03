package com.tasirin.httpdownloadmanager.data

enum class DownloadState {
    PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED
}

data class DownloadItem(
    val id: String,
    val url: String,
    val fileName: String,
    val state: DownloadState,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val error: String? = null,
    val contentUri: String? = null,
    val filePath: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val nameIsCustom: Boolean = false,
    val autoResume: Boolean = false
) {
    val progressPercent: Int
        get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else 0
}
