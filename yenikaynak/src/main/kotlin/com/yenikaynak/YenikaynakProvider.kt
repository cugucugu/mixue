package com.cugucugu.yenikaynak

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class YenikaynakProvider : MainAPI() {
    override var name = "Yenikaynak"
    override var mainUrl = "https://www.yenikaynak.com"
    override val supportedTypes = setOf(TvType.Movie)
    override val hasMainPage = true
    override val lang = "tr"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(mainUrl).document
        val movies = doc.select("div.makale > a").mapNotNull { element ->
            val href = fixUrl(element.attr("href"))
            val title = element.selectFirst("h3")?.text()?.trim() ?: return@mapNotNull null
            val poster = element.selectFirst("img")?.absUrl("src")
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }

        return newHomePageResponse(
            listOf(
                HomePageList("Güncel Filmler", movies)
            )
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/arama?q=$query"
        val doc = app.get(searchUrl).document

        return doc.select("div.makale > a").mapNotNull { element ->
            val href = fixUrl(element.attr("href"))
            val title = element.selectFirst("h3")?.text()?.trim() ?: return@mapNotNull null
            val poster = element.selectFirst("img")?.absUrl("src")
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1.entry-title")?.text()?.trim() ?: return null
        val poster = doc.selectFirst("img.aligncenter")?.absUrl("src")
        val plot = doc.selectFirst("div.entry-content p")?.text()?.trim()

        val sources = mutableListOf<ExtractorLink>()

        doc.select("iframe").forEach { iframe ->
            val iframeUrl = fixUrlNull(iframe.attr("src")) ?: return@forEach
            loadExtractor(iframeUrl, mainUrl, subtitleCallback = {}, callback = {
                sources.add(it)
            })
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = plot
            this.recommendations = emptyList()
            this.rating = null
            this.tags = emptyList()
            this.year = null
            this.actors = emptyList()
            this.addLinks(sources)
        }
    }
}
