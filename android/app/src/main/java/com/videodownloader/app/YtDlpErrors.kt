package com.videodownloader.app

object YtDlpErrors {
    private val impersonationPattern = Regex(
        "impersonate target is available|yt-dlp#impersonation|installing the required dependencies",
        RegexOption.IGNORE_CASE,
    )

    fun sanitize(raw: String?): String {
        if (raw.isNullOrBlank()) {
            return "Download failed"
        }

        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val errorLines = lines.filter { it.startsWith("ERROR:", ignoreCase = true) }
        val realErrors = errorLines.filterNot { impersonationPattern.containsMatchIn(it) }

        val candidate = when {
            realErrors.isNotEmpty() -> realErrors.last()
            errorLines.isNotEmpty() -> errorLines.last()
            else -> lines.lastOrNull {
                !it.startsWith("WARNING:", ignoreCase = true) &&
                    !impersonationPattern.containsMatchIn(it)
            } ?: raw
        }

        val cleaned = candidate.removePrefix("ERROR:").trim()
        val looksBlocked = cleaned.contains("403") ||
            cleaned.contains("cloudflare", ignoreCase = true) ||
            impersonationPattern.containsMatchIn(cleaned) ||
            (impersonationPattern.containsMatchIn(raw) && realErrors.isEmpty())

        return if (looksBlocked) {
            "This site requires browser impersonation. Android cannot load curl-cffi, so the download cannot continue here. Use the desktop app for this link."
        } else {
            cleaned.ifBlank { "Download failed" }
        }
    }
}
