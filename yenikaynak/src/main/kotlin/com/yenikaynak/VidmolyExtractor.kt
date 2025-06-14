package com.yenikaynak

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class VidmolyExtractor : ExtractorApi() {
    override val name = "Vidmoly"
    override val mainUrl = "https://vidmoly.to"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val headers = mapOf("Referer" to (referer ?: mainUrl))
        val document = app.get(url, headers = headers).document

        val script = document.selectFirst("script:containsData(sources: [)")?.data() ?: return null
        val match = Regex("""file:\s*["'](https?[^"']+\.mp4)["']""").find(script) ?: return null
        val videoUrl = match.groupValues[1]

        return listOf(newExtractorLink {
            this.name = this@VidmolyExtractor.name
            this.source = this@VidmolyExtractor.name
            this.url = videoUrl
            this.referer = referer ?: mainUrl
            this.quality = Qualities.Unknown.value
            this.isM3u8 = false
        })
    }
}
