package com.yenikaynak

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

suspend fun loadVidmoly(url: String, referer: String?): List<ExtractorLink> {
    val headers = mapOf("Referer" to (referer ?: "https://vidmoly.to"))
    val document = app.get(url, headers = headers).document

    val script = document.selectFirst("script:containsData(sources: [)")?.data() ?: return emptyList()
    val match = Regex("""file:\s*["'](https?[^"']+\.mp4)["']""").find(script) ?: return emptyList()
    val videoUrl = match.groupValues[1]

    return listOf(newExtractorLink {
        this.name = "Vidmoly"
        this.source = "Vidmoly"
        this.url = videoUrl
        this.referer = referer ?: "https://vidmoly.to"
        this.quality = Qualities.Unknown.value
        this.isM3u8 = false
    })
}
