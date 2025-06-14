package com.jetfilmizle

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import org.jsoup.nodes.Element

class JetFilmizle : MainAPI() {
    override var mainUrl = "https://jetfilmizle.watch"
    override var name = "JetFilmizle"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "$mainUrl/page/" to "Son Filmler",
        "$mainUrl/netflix/page/" to "Netflix",
        "$mainUrl/editorun-secimi/page/" to "Editörün Seçimi",
        "$mainUrl/turk-film-full-hd-izle/page/" to "Türk Filmleri",
        "$mainUrl/cizgi-filmler-full-izle/page/" to "Çizgi Filmler",
        "$mainUrl/kategoriler/yesilcam-filmleri-full-izle/page/" to "Yeşilçam Filmleri"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get("${request.data}$page").document
        val home = doc.select("article.movie").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("h2 a,h3 a,h4 a,h5 a,h6 a")?.text()?.substringBefore(" izle") ?: return null
        val href = fixUrlNull(selectFirst("a")?.attr("href")) ?: return null
        val poster = fixUrlNull(selectFirst("img")?.attr("data-src") ?: selectFirst("img")?.attr("src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.post(
            "$mainUrl/filmara.php",
            referer = "$mainUrl/",
            data = mapOf("s" to query)
        ).document

        return doc.select("article.movie").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document

        val title = doc.selectFirst("section.movie-exp div.movie-exp-title")?.text()?.substringBefore(" izle") ?: return null
        val poster = fixUrlNull(doc.selectFirst("section.movie-exp img")?.attr("data-src") ?: doc.selectFirst("section.movie-exp img")?.attr("src"))
        val yearText = doc.select("div.yap:contains(Vizyon), div.yap:contains(Yapım)").text().trim()
        val year = Regex("""\d{4}""").find(yearText)?.value?.toIntOrNull()
        val description = doc.selectFirst("section.movie-exp p.aciklama")?.text()
        val tags = doc.select("section.movie-exp div.catss a").map { it.text() }
        val rating = doc.selectFirst("section.movie-exp div.imdb_puan span")?.text()?.toRatingInt()
        val actors = doc.select("section.movie-exp div.oyuncu").mapNotNull {
            val name = it.selectFirst("div.name")?.text() ?: return@mapNotNull null
            val image = fixUrlNull(it.selectFirst("img")?.attr("data-src"))
            Actor(name, image)
        }

        val recommendations = doc.select("div#benzers article").mapNotNull {
            val name = it.selectFirst("h2 a,h3 a,h4 a")?.text()?.substringBefore(" izle") ?: return@mapNotNull null
            val href = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
            val posterRec = fixUrlNull(it.selectFirst("img")?.attr("data-src"))
            newMovieSearchResponse(name, href, TvType.Movie) {
                this.posterUrl = posterRec
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            this.rating = rating
            this.recommendations = recommendations
            addActors(actors)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document

        val iframes = mutableListOf<String>()
        doc.select("div#movie iframe").forEach {
            val iframe = fixUrlNull(it.attr("data-litespeed-src") ?: it.attr("src")) ?: return@forEach
            iframes.add(iframe)
        }

        for (iframe in iframes) {
            try {
                val nestedIframe = if (iframe.contains("d2rs")) {
                    val nestedDoc = app.get(iframe).document
                    fixUrlNull(nestedDoc.selectFirst("iframe")?.attr("src")) ?: continue
                } else iframe

                loadExtractor(nestedIframe, "$mainUrl/", subtitleCallback, callback)
            } catch (e: Exception) {
                Log.e("JetFilmizle", "Extractor error: $iframe", e)
            }
        }

        return true
    }
}
