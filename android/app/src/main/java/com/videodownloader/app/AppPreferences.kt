package com.videodownloader.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import java.io.File

data class DownloadSettings(
    val quality: String = "best",
    val audioOnly: Boolean = false,
    val outputDir: String = "",
    val filenameTemplate: String = "%(title)s [%(id)s].%(ext)s",
    val mergeFormat: String = "mp4",
    val preferHighestResolution: Boolean = true,
    val noPlaylist: Boolean = true,
    val audioFormat: String = "mp3",
    val audioQuality: String = "192",
    val retries: Int = 10,
    val fragmentRetries: Int = 10,
    val concurrentFragments: Int = 4,
    val useCustomReferer: Boolean = false,
    val customReferer: String = "",
) {
    fun effectiveAudioOnly(): Boolean = audioOnly || quality == "audio_only"
}

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getDownloadSettings(): DownloadSettings {
        return DownloadSettings(
            quality = prefs.getString(KEY_QUALITY, "best") ?: "best",
            audioOnly = prefs.getBoolean(KEY_AUDIO_ONLY, false),
            outputDir = prefs.getString(KEY_OUTPUT_DIR, defaultOutputDir()) ?: defaultOutputDir(),
            filenameTemplate = prefs.getString(KEY_FILENAME_TEMPLATE, "%(title)s [%(id)s].%(ext)s")
                ?: "%(title)s [%(id)s].%(ext)s",
            mergeFormat = prefs.getString(KEY_MERGE_FORMAT, "mp4") ?: "mp4",
            preferHighestResolution = prefs.getBoolean(KEY_PREFER_HIGHEST, true),
            noPlaylist = prefs.getBoolean(KEY_NO_PLAYLIST, true),
            audioFormat = prefs.getString(KEY_AUDIO_FORMAT, "mp3") ?: "mp3",
            audioQuality = prefs.getString(KEY_AUDIO_QUALITY, "192") ?: "192",
            retries = prefs.getInt(KEY_RETRIES, 10),
            fragmentRetries = prefs.getInt(KEY_FRAGMENT_RETRIES, 10),
            concurrentFragments = prefs.getInt(KEY_CONCURRENT_FRAGMENTS, 4),
            useCustomReferer = prefs.getBoolean(KEY_USE_REFERER, false),
            customReferer = prefs.getString(KEY_CUSTOM_REFERER, "") ?: "",
        )
    }

    fun saveDownloadSettings(settings: DownloadSettings) {
        prefs.edit()
            .putString(KEY_QUALITY, settings.quality)
            .putBoolean(KEY_AUDIO_ONLY, settings.audioOnly)
            .putString(KEY_OUTPUT_DIR, settings.outputDir)
            .putString(KEY_FILENAME_TEMPLATE, settings.filenameTemplate)
            .putString(KEY_MERGE_FORMAT, settings.mergeFormat)
            .putBoolean(KEY_PREFER_HIGHEST, settings.preferHighestResolution)
            .putBoolean(KEY_NO_PLAYLIST, settings.noPlaylist)
            .putString(KEY_AUDIO_FORMAT, settings.audioFormat)
            .putString(KEY_AUDIO_QUALITY, settings.audioQuality)
            .putInt(KEY_RETRIES, settings.retries)
            .putInt(KEY_FRAGMENT_RETRIES, settings.fragmentRetries)
            .putInt(KEY_CONCURRENT_FRAGMENTS, settings.concurrentFragments)
            .putBoolean(KEY_USE_REFERER, settings.useCustomReferer)
            .putString(KEY_CUSTOM_REFERER, settings.customReferer)
            .apply()
    }

    fun saveOutputDir(path: String) {
        prefs.edit().putString(KEY_OUTPUT_DIR, path).apply()
    }

    fun resetToDefaults() {
        saveDownloadSettings(
            DownloadSettings(outputDir = defaultOutputDir()),
        )
    }

    companion object {
        private const val PREFS_NAME = "video_downloader_prefs"
        private const val KEY_QUALITY = "quality"
        private const val KEY_AUDIO_ONLY = "audio_only"
        private const val KEY_OUTPUT_DIR = "output_dir"
        private const val KEY_FILENAME_TEMPLATE = "filename_template"
        private const val KEY_MERGE_FORMAT = "merge_format"
        private const val KEY_PREFER_HIGHEST = "prefer_highest"
        private const val KEY_NO_PLAYLIST = "no_playlist"
        private const val KEY_AUDIO_FORMAT = "audio_format"
        private const val KEY_AUDIO_QUALITY = "audio_quality"
        private const val KEY_RETRIES = "retries"
        private const val KEY_FRAGMENT_RETRIES = "fragment_retries"
        private const val KEY_CONCURRENT_FRAGMENTS = "concurrent_fragments"
        private const val KEY_USE_REFERER = "use_referer"
        private const val KEY_CUSTOM_REFERER = "custom_referer"

        fun defaultOutputDir(): String {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            return File(downloads, "video-downloader").absolutePath
        }

        fun qualityLabel(context: Context, quality: String, audioOnly: Boolean): String {
            if (audioOnly || quality == "audio_only") {
                return context.getString(R.string.quality_audio_only)
            }
            return when (quality) {
                "best" -> context.getString(R.string.quality_best)
                "worst" -> context.getString(R.string.quality_worst)
                else -> "${quality}p"
            }
        }

        val qualityValues = listOf("best", "1080", "720", "480", "360", "worst", "audio_only")
        val mergeFormats = listOf("mp4", "mkv", "webm", "mov")
        val audioFormats = listOf("mp3", "m4a", "opus", "wav")
        val audioQualities = listOf("128", "192", "256", "320")
    }
}
