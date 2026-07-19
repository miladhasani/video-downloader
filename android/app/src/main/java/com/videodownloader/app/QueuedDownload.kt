package com.videodownloader.app

enum class QueueStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class QueuedDownload(
    val id: String,
    val url: String,
    val status: QueueStatus = QueueStatus.PENDING,
    val progress: Int = 0,
    val statusMessage: String = "",
    val errorMessage: String? = null,
)
