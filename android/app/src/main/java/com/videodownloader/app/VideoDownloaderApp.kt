package com.videodownloader.app

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VideoDownloaderApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var isInitialized = false
        private set

    lateinit var downloadQueue: DownloadQueueManager
        private set

    override fun onCreate() {
        super.onCreate()
        downloadQueue = DownloadQueueManager(
            scope = appScope,
            getSettings = {
                val prefs = AppPreferences(this)
                prefs.getDownloadSettings()
            },
        )

        appScope.launch {
            try {
                YoutubeDL.getInstance().init(this@VideoDownloaderApp)
                FFmpeg.getInstance().init(this@VideoDownloaderApp)
                updateYtDlp()
                isInitialized = true
                Log.i(TAG, "yt-dlp ready: ${ytDlpVersion()}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize yt-dlp", e)
            }
        }
    }

    fun ytDlpVersion(): String {
        return YoutubeDL.getInstance().versionName(this)
            ?: YoutubeDL.getInstance().version(this)
            ?: "unknown"
    }

    fun updateYtDlp(): YoutubeDL.UpdateStatus? {
        return try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(
                this,
                YoutubeDL.UpdateChannel.STABLE,
            )
            Log.i(TAG, "yt-dlp update: $status (${ytDlpVersion()})")
            status
        } catch (e: Exception) {
            Log.w(TAG, "yt-dlp update failed; keeping current binary", e)
            null
        }
    }

    companion object {
        private const val TAG = "VideoDownloaderApp"
    }
}
