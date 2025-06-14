package com.jetfilmizle

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class JetFilmizle : MainAPI() {
    // Sitenin güncel adresi
    override var mainUrl = "https://jetfilmizle.watch"
    override var name = "JetFilmizle"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "$mainUrl/page/" to "Son Eklenenler",
        "$mainUrl/kategoriler/netflix/page/" to "Netflix",
        "$mainUrl/kategoriler/editorun-secimi/page/" to "Editörün Seçimi",
        "$mainUrl/kategoriler/turk-filmleri/page/" to "Türk Filmleri",
        "$mainUrl/kategoriler/cizgi-filmler/page/" to "Çizgi Filmler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data + page).document
        // Sitenin yeni HTML yapısına uygun olarak güncellendi
        val home = document.select("div.ml-item").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleElement = this.selectFirst("h2.mli-title a") ?: return null
        val title = titleElement.text().substringBefore(" izle").trim()
        val href = fixUrl(titleElement.attr("href"))
        // Resimler "data-original" özelliğinden okunuyor
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-original"))

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // Arama fonksiyonu GET metodu kullanacak şekilde güncellendi
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("div.ml-item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.title")?.text()?.substringBefore(" izle")?.trim()!!
        val poster = fixUrlNull(document.selectFirst("div.poster img")?.attr("src"))
        val year = document.select("div.jfs-details div.fd-item:contains(Yıl) span").text().toIntOrNull()
        val description = document.selectFirst("div.synopsis p")?.text()?.trim()
        val tags = document.select("div.jfs-details div.fd-item:contains(Kategori) a").map { it.text() }
        val rating = document.selectFirst("div.jfs-details span.imdb")?.text()?.trim()?.toRatingInt()
        
        // HATA DÜZELTMESİ: 'Actor' yerine 'ActorData' listesi oluşturuluyor.
        // API artık 'ActorData' tipinde bir liste bekliyor.
        val actors = document.select("div.cast-list div.cast-item").mapNotNull {
            val name = it.selectFirst("div.ci-name")?.text() ?: return@mapNotNull null
            val image = fixUrlNull(it.selectFirst("img")?.attr("data-original"))
            // 'Actor' nesnesi 'ActorData' içine sarmalanıyor.
            ActorData(Actor(name, image))
        }

        val recommendations = document.select("div.movies-list div.ml-item").mapNotNull { it.toSearchResult() }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            this.rating = rating
            this.recommendations = recommendations
            // HATA DÜZELTMESİ: 'addActors' yerine 'actors' özelliğine doğrudan atama yapılıyor.
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
        // Video kaynakları (partlar) "data-iframe" içeren sekmelerden okunuyor.
        document.select("ul.source-list li").forEach {
            val iframeUrl = it.attr("abs:data-iframe")
            if (iframeUrl.isNotBlank()) {
                // 'referer' olarak mevcut film sayfasının URL'si ekleniyor.
                loadExtractor(iframeUrl, data, subtitleCallback, callback)
            }
        }
        return true
    }
}
