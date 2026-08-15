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

    fun applyToRequest(request: YoutubeDLRequest, settings: DownloadSettings, pageUrl: String = "") {
        val outputDir = StorageHelper.ensureDirectory(settings.outputDir).absolutePath
        val outputTemplate = "$outputDir/${settings.filenameTemplate}"

        request.addOption("-f", formatSelector(settings))
        request.addOption("--no-update")
        request.addOption("--merge-output-format", settings.mergeFormat)
        request.addOption("-o", outputTemplate)
        request.addOption("--continue")
        request.addOption("--retries", settings.retries.toString())
        request.addOption("--fragment-retries", settings.fragmentRetries.toString())
        request.addOption("--concurrent-fragments", settings.concurrentFragments.toString())
        request.addOption("--user-agent", CHROME_ANDROID_UA)
        request.addOption("--add-header", "Accept-Language:en-US,en;q=0.9")
        // Android's bundled Python cannot load curl-cffi, so never request impersonation.
        request.addOption("--extractor-args", "generic:impersonate=false")

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

        val referer = when {
            settings.useCustomReferer && settings.customReferer.isNotBlank() -> settings.customReferer
            pageUrl.isNotBlank() -> pageUrl
            else -> null
        }
        if (referer != null) {
            request.addOption("--referer", referer)
        }
    }

    private const val CHROME_ANDROID_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
}
