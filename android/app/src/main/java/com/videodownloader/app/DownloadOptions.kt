package com.videodownloader.app

import com.yausername.youtubedl_android.YoutubeDLRequest

object DownloadOptions {
    fun formatSelector(settings: DownloadSettings): String {
        if (settings.effectiveAudioOnly()) {
            return "bestaudio*/best"
        }

        return when (settings.quality) {
            "best" -> "bestvideo*+bestaudio/best"
            "worst" -> "worstvideo+worstaudio/worst"
            "audio_only" -> "bestaudio*/best"
            else -> {
                val height = settings.quality.trimEnd('p')
                require(height.all { it.isDigit() }) {
                    "Invalid quality '${settings.quality}'."
                }
                "bestvideo[height<=$height]+bestaudio/best[height<=$height]"
            }
        }
    }

    fun applyToRequest(request: YoutubeDLRequest, settings: DownloadSettings) {
        val outputDir = StorageHelper.ensureDirectory(settings.outputDir).absolutePath
        val outputTemplate = "$outputDir/${settings.filenameTemplate}"

        request.addOption("-f", formatSelector(settings))
        request.addOption("--merge-output-format", settings.mergeFormat)
        request.addOption("-o", outputTemplate)
        request.addOption("--continue")
        request.addOption("--retries", settings.retries.toString())
        request.addOption("--fragment-retries", settings.fragmentRetries.toString())
        request.addOption("--concurrent-fragments", settings.concurrentFragments.toString())

        if (settings.noPlaylist) {
            request.addOption("--no-playlist")
        }

        if (settings.quality == "best" && !settings.effectiveAudioOnly() && settings.preferHighestResolution) {
            request.addOption("-S", "res:9999,fps,size,br")
            request.addOption("--no-prefer-free-formats")
        }

        if (settings.effectiveAudioOnly()) {
            request.addOption("-x")
            request.addOption("--audio-format", settings.audioFormat)
            request.addOption("--audio-quality", "${settings.audioQuality}K")
        }

        if (settings.useCustomReferer && settings.customReferer.isNotBlank()) {
            request.addOption("--referer", settings.customReferer)
        }
    }
}
