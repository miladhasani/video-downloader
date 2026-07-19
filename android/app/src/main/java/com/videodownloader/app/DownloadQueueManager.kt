package com.videodownloader.app

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class DownloadQueueManager(
    private val scope: CoroutineScope,
    private val getSettings: () -> DownloadSettings,
) {
    private val _queue = MutableStateFlow<List<QueuedDownload>>(emptyList())
    val queue: StateFlow<List<QueuedDownload>> = _queue.asStateFlow()

    private val mutex = Mutex()
    private var workerJob: Job? = null
    private var paused = false
    private var currentItemId: String? = null

    val isRunning: Boolean
        get() = workerJob?.isActive == true

    val pendingCount: Int
        get() = _queue.value.count { it.status == QueueStatus.PENDING }

    val activeItem: QueuedDownload?
        get() = _queue.value.firstOrNull { it.status == QueueStatus.DOWNLOADING }

    fun add(url: String): Boolean {
        val normalized = normalizeUrl(url) ?: return false
        if (_queue.value.any { it.url == normalized && it.status == QueueStatus.PENDING }) {
            return false
        }

        val item = QueuedDownload(
            id = UUID.randomUUID().toString(),
            url = normalized,
        )
        _queue.update { it + item }
        if (!paused) {
            ensureWorker()
        }
        return true
    }

    fun addAll(urls: Collection<String>): Int {
        var added = 0
        urls.forEach { url ->
            if (add(url)) {
                added++
            }
        }
        return added
    }

    fun remove(id: String) {
        val item = _queue.value.firstOrNull { it.id == id } ?: return
        if (item.status == QueueStatus.DOWNLOADING) {
            stopCurrent(markCancelled = false)
        }
        _queue.update { items -> items.filterNot { it.id == id } }
        if (!paused) {
            ensureWorker()
        }
    }

    fun clearPending() {
        _queue.update { items ->
            items.filterNot { it.status == QueueStatus.PENDING }
        }
    }

    fun clearFinished() {
        _queue.update { items ->
            items.filterNot {
                it.status == QueueStatus.COMPLETED ||
                    it.status == QueueStatus.FAILED ||
                    it.status == QueueStatus.CANCELLED
            }
        }
    }

    fun clearAll() {
        stopCurrent(markCancelled = true)
        paused = true
        workerJob?.cancel()
        workerJob = null
        _queue.value = emptyList()
    }

    fun start() {
        paused = false
        ensureWorker()
    }

    fun pause() {
        paused = true
        stopCurrent(markCancelled = true)
        workerJob?.cancel()
        workerJob = null
    }

    fun stopCurrent(markCancelled: Boolean = true) {
        val itemId = currentItemId
        if (itemId != null) {
            try {
                YoutubeDL.getInstance().destroyProcessById(processId(itemId))
            } catch (_: Exception) {
            }
            if (markCancelled) {
                updateItem(itemId) {
                    it.copy(
                        status = QueueStatus.CANCELLED,
                        statusMessage = "",
                        progress = 0,
                    )
                }
            }
        }
        currentItemId = null
    }

    private fun ensureWorker() {
        if (paused || workerJob?.isActive == true) {
            return
        }
        if (_queue.value.none { it.status == QueueStatus.PENDING }) {
            return
        }

        workerJob = scope.launch {
            while (isActive && !paused) {
                val next = mutex.withLock {
                    _queue.value.firstOrNull { it.status == QueueStatus.PENDING }
                } ?: break

                try {
                    downloadItem(next)
                } catch (_: CancellationException) {
                    break
                }
            }
        }
    }

    private suspend fun downloadItem(item: QueuedDownload) {
        currentItemId = item.id
        updateItem(item.id) {
            it.copy(
                status = QueueStatus.DOWNLOADING,
                progress = 0,
                statusMessage = "",
                errorMessage = null,
            )
        }

        try {
            val request = YoutubeDLRequest(item.url)
            DownloadOptions.applyToRequest(request, getSettings())

            YoutubeDL.getInstance().execute(request, processId(item.id)) { progress, _, line ->
                updateItem(item.id) { current ->
                    current.copy(
                        progress = progress.toInt().coerceIn(0, 100),
                        statusMessage = line.ifBlank { current.statusMessage },
                    )
                }
            }

            updateItem(item.id) {
                it.copy(
                    status = QueueStatus.COMPLETED,
                    progress = 100,
                    statusMessage = "",
                    errorMessage = null,
                )
            }
        } catch (e: Exception) {
            val current = _queue.value.firstOrNull { it.id == item.id }
            if (current?.status != QueueStatus.CANCELLED) {
                updateItem(item.id) {
                    it.copy(
                        status = QueueStatus.FAILED,
                        errorMessage = e.localizedMessage ?: e.javaClass.simpleName,
                        statusMessage = "",
                    )
                }
            }
        } finally {
            if (currentItemId == item.id) {
                currentItemId = null
            }
        }
    }

    private fun updateItem(id: String, transform: (QueuedDownload) -> QueuedDownload) {
        _queue.update { items ->
            items.map { item ->
                if (item.id == id) transform(item) else item
            }
        }
    }

    private fun processId(itemId: String): String = "video-downloader-$itemId"

    companion object {
        fun parseUrls(text: String): List<String> {
            return text.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { normalizeUrl(it) }
        }

        fun normalizeUrl(url: String): String? {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) {
                return null
            }
            return if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            ) {
                trimmed
            } else {
                null
            }
        }
    }
}
