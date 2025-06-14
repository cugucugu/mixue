package com.jetfilmizle

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class JetFilmizle : MainAPI() {
    // DÜZELTME: Sitenin adresi isteğiniz üzerine .watch olarak güncellendi.
    override var mainUrl = "https://jetfilmizle.watch"
    override var name = "JetFilmizle"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie)

    // Ana sayfa kategorileri güncel site yapısına göre düzenlendi.
    override val mainPage = mainPageOf(
        "$mainUrl/page/" to "Son Eklenenler",
        "$mainUrl/kategoriler/netflix-filmleri/page/" to "Netflix",
        "$mainUrl/kategoriler/editorun-secimi/page/" to "Editörün Seçimi",
        "$mainUrl/kategoriler/turk-filmleri-izle/page/" to "Türk Filmleri",
        "$mainUrl/kategoriler/cizgi-filmler/page/" to "Çizgi Filmler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data + page).document
        // DÜZELTME: Ana sayfadaki film listesini çeken seçici, sitenin yeni HTML yapısına
        // ('article' etiketleri) göre güncellendi. "İçerik gözükmüyor" sorunu bu şekilde çözüldü.
        val home = document.select("article.movie").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    // DÜZELTME: Film kartını (küçük resim, başlık) okuyan fonksiyon, yeni yapıya göre düzenlendi.
    private fun Element.toSearchResult(): SearchResponse? {
        val titleElement = this.selectFirst("h2.entry-title a") ?: return null
        val title = titleElement.text().substringBefore(" izle").trim()
        val href = fixUrl(titleElement.attr("href"))
        // Resimlerin "data-src" veya "src" özelliğinden okunması sağlandı.
        val posterUrl = fixUrlNull(this.selectFirst("figure.post-thumbnail img")?.attr("data-src") ?: this.selectFirst("figure.post-thumbnail img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        // DÜZELTME: Arama sonuçları için de doğru seçici ('article.movie') kullanıldı.
        return document.select("article.movie").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text()?.substringBefore(" izle")?.trim()!!
        // DÜZELTME: Film detay sayfasındaki tüm seçiciler yeni tasarıma göre güncellendi.
        val poster = fixUrlNull(document.selectFirst("div.single-poster img")?.attr("src"))
        val year = document.select("div.film-meta span:contains(Yıl) a").text().toIntOrNull()
        val description = document.selectFirst("div.entry-content p")?.text()?.trim()
        val tags = document.select("div.film-meta span:contains(Kategori) a").map { it.text() }
        val rating = document.selectFirst("div.film-meta span.film-imdb-rating")?.text()?.trim()?.toRatingInt()
        
        val actors = document.select("div.film-meta span:contains(Oyuncular) a").map {
            ActorData(Actor(it.text()))
        }

        val recommendations = document.select("div.related-movies article.movie").mapNotNull { it.toSearchResult() }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            this.rating = rating
            this.recommendations = recommendations
            this.actors = actors
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        // DÜZELTME: Video kaynaklarını (partları) barındıran iframe'i bulan seçici güncellendi.
        document.select("div.film-embed iframe").forEach {
            val iframeUrl = it.attr("abs:src")
            if (iframeUrl.isNotBlank()) {
                loadExtractor(iframeUrl, data, subtitleCallback, callback)
            }
        }
        return true
    }
}
