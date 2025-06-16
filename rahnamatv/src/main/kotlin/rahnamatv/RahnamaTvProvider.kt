package com.cloudstreamplugins.rahnama

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.utils.*

class Rahnama : MainAPI() {
    override var name = "RahnamaTV"
    override var mainUrl = "https://rahnama.tv"
    override var lang = "fa" // Farsça içerik varsa
    override val hasMainPage = true
    override val hasSearch = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = listOf(
        "Sinemalar" to "$mainUrl/sinemalar",
        "Diziler" to "$mainUrl/diziler",
        "Kısa Filmler" to "$mainUrl/kisa-filmler",
        "Müzikler" to "$mainUrl/muzikler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val home = request.dataUrl
        val document = app.get(home).document
        val items = document.select("article").mapNotNull {
            val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = it.selectFirst("h2")?.text()?.trim() ?: return@mapNotNull null
            val poster = it.selectFirst("img")?.attr("src")
            val type = if (home.contains("/diziler")) TvType.TvSeries else TvType.Movie
            newMovieSearchResponse(title, href, type) {
                this.posterUrl = poster
            }
        }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("meta[property=og:title]")?.attr("content") ?: return errorLoadResponse
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
        val description = doc.selectFirst("meta[property=og:description]")?.attr("content")

        val episodes = mutableListOf<Episode>()
        val base = Regex("""https://rahnama\.tv/([a-zA-Z0-9\-]+)/""").find(url)?.groupValues?.get(1)
        if (base != null) {
            // Dizi ise, örneğin /kaybeden -> /kaybeden-1, -2, ...
            for (i in 1..20) {
                val episodeUrl = "$mainUrl/$base-$i/"
                val episodeDoc = app.get(episodeUrl).document
                if (episodeDoc.select("video, iframe").isEmpty()) break
                episodes.add(Episode(episodeUrl, "Bölüm $i"))
            }
        }

        return if (episodes.isEmpty()) {
            val videoUrl = doc.selectFirst("video source")?.attr("src")
                ?: doc.selectFirst("iframe")?.attr("src")
                ?: return errorLoadResponse
            newMovieLoadResponse(title, url, TvType.Movie, videoUrl) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(data).document
        val videoUrl = doc.selectFirst("video source")?.attr("src")
            ?: doc.selectFirst("iframe")?.attr("src")
            ?: return
        callback(
            ExtractorLink(
                source = "RahnamaTV",
                name = "Rahnama",
                url = videoUrl,
                referer = mainUrl,
                quality = Qualities.Unknown
            )
        )
    }
}
