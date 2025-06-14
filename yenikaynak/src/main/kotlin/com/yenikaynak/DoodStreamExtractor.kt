package com.yenikaynak

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class DoodStreamExtractor : ExtractorApi() {
    override val name = "DoodStream"
    override val mainUrl = "https://dood.so"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val res = app.get(url, referer = referer)
        val match = Regex(""""file":"(https:[^"]+\.mp4)"""").find(res.text) ?: return null
        val videoUrl = match.groupValues[1].replace("\\", "")

        return listOf(newExtractorLink {
            this.name = this@DoodStreamExtractor.name
            this.source = this@DoodStreamExtractor.name
            this.url = videoUrl
            this.referer = url
            this.quality = Qualities.Unknown.value
            this.isM3u8 = false
        })
    }
}
