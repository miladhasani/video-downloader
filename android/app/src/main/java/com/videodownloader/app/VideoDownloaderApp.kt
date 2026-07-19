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
                isInitialized = true
                Log.i(TAG, "yt-dlp initialized: ${YoutubeDL.getInstance().versionName(this@VideoDownloaderApp)}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize yt-dlp", e)
            }
        }
    }

    companion object {
        private const val TAG = "VideoDownloaderApp"
    }
}
