package com.pomodoro.timer

object YouTubeLinks {
    private val idPattern = Regex("^[\\w-]{11}$")
    private val urlPatterns = listOf(
        Regex("(?:youtube\\.com/watch\\?.*v=|youtube\\.com/embed/|youtu\\.be/|youtube\\.com/shorts/)([\\w-]{11})"),
        Regex("youtube\\.com/live/([\\w-]{11})"),
    )

    fun parseVideoId(input: String): String? {
        val trimmed = input.trim()
        if (idPattern.matches(trimmed)) return trimmed
        for (pattern in urlPatterns) {
            pattern.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
        }
        return null
    }

    fun thumbnailUrl(videoId: String): String =
        "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
}
