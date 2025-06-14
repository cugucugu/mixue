package com.yenikaynak

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.*
import com.lagradost.cloudstream3.utils.*

class DoodStreamExtractor : ExtractorApi() {
    override val name = "DoodStream"
    override val mainUrl = "https://dood.so"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val res = app.get(url, referer = referer)
        val match = Regex(""""file":"(https:[^"]+\.mp4)"""").find(res.text) ?: return null
        val videoUrl = match.groupValues[1].replace("\\", "")
        return listOf(
            newExtractorLink(
                name = name,
                source = name,
                url = videoUrl,
                referer = url,
                quality = Qualities.Unknown.value,
                isM3u8 = false
            )
        )
    }
}
