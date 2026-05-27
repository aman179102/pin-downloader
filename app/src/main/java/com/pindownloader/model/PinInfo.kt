package com.pindownloader.model

data class PinInfo(
    val title: String = "",
    val thumbnailUrl: String = "",
    val videoQualities: Map<String, String> = emptyMap(),
    val isVideo: Boolean = false,
    val pinUrl: String = ""
) {
    val bestVideoUrl: String?
        get() {
            val priority = listOf(
                "V_HLSV3_DESKTOP", "V_HLSV3_MOBILE",
                "V_1080P", "1080", "1080p",
                "V_720P", "720", "720p",
                "V_480P", "480", "480p", "V_360P", "360", "360p"
            )
            for (q in priority) {
                videoQualities[q]?.let { return it }
            }
            for ((key, value) in videoQualities) {
                val upper = key.uppercase()
                if (upper.contains("1080")) return value
                if (upper.contains("720")) return value
                if (upper.contains("480")) return value
            }
            return videoQualities.values.firstOrNull()
        }

    val isHls: Boolean get() = bestVideoUrl?.contains(".m3u8") == true
}
