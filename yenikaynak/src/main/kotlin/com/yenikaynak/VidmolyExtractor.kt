package com.cugucugu.yenikaynak

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlin.random.Random

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

        return listOf(
            ExtractorLink(
                name = name,
                source = name,
                url = videoUrl,
                referer = referer ?: mainUrl,
                quality = Qualities.Unknown.value,
                isM3u8 = false
            )
        )
    }
}
