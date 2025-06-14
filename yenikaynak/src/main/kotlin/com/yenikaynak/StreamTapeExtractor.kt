package com.yenikaynak

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class StreamTapeExtractor : ExtractorApi() {
    override val name = "StreamTape"
    override val mainUrl = "https://streamtape.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val res = app.get(url, referer = referer)
        val match = Regex("""'robotlink'\s*,\s*'([^']+)'""").find(res.text)
        val path = match?.groupValues?.get(1) ?: return null
        val videoUrl = "$mainUrl$path"

        return listOf(newExtractorLink {
            this.name = this@StreamTapeExtractor.name
            this.source = this@StreamTapeExtractor.name
            this.url = videoUrl
            this.referer = url
            this.quality = Qualities.Unknown.value
            this.isM3u8 = false
        })
    }
}
