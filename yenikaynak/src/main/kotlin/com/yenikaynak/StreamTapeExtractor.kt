package com.cugucugu.yenikaynak

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlin.random.Random

class StreamTapeExtractor : ExtractorApi() {
    override val name = "StreamTape"
    override val mainUrl = "https://streamtape.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val res = app.get(url, referer = referer)
        val match = Regex("""'robotlink'\s*,\s*'([^']+)'""").find(res.text)
        val path = match?.groupValues?.get(1) ?: return null
        val videoUrl = "$mainUrl$path"
        return listOf(
            ExtractorLink(name, name, videoUrl, referer = url, quality = Qualities.Unknown.value, isM3u8 = false)
        )
    }
}