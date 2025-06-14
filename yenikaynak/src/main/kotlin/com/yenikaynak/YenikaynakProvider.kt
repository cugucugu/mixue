package com.yenikaynak

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class YenikaynakProvider : MainAPI() {
    override var name = "Yenikaynak"
    override var mainUrl = "https://www.yenikaynak.com"
    override val supportedTypes = setOf(TvType.Movie)
    override val hasMainPage = true

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(mainUrl).document
        val movies = doc.select("div.makale > a").mapNotNull {
            val href = fixUrl(it.attr("href"))
            val title = it.selectFirst("h3")?.text() ?: return@mapNotNull null
            val poster = it.selectFirst("img")?.absUrl("src")
            MovieSearchResponse(title, href, name, TvType.Movie, poster)
        }
        return HomePageResponse(listOf(HomePageList("Güncel Filmler", movies)))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/arama?q=$query"
        val doc = app.get(searchUrl).document
        return doc.select("div.makale > a").mapNotNull {
            val href = fixUrl(it.attr("href"))
            val title = it.selectFirst("h3")?.text() ?: return@mapNotNull null
            val poster = it.selectFirst("img")?.absUrl("src")
            MovieSearchResponse(title, href, name, TvType.Movie, poster)
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1.entry-title")?.text() ?: return null
        val poster = doc.selectFirst("img.aligncenter")?.absUrl("src")
        val plot = doc.selectFirst("div.entry-content p")?.text()

        val sources = ArrayList<ExtractorLink>()
        doc.select("iframe").forEach { iframe ->
            val iframeUrl = iframe.attr("src")
            loadExtractor(iframeUrl, mainUrl) {
                sources.add(it)
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie) {
            this.posterUrl = poster
            this.plot = plot
            addSources(sources)
        }
    }
}
