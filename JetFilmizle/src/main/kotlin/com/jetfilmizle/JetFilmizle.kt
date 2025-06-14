package com.jetfilmizle

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class JetFilmizle : MainAPI() {
    // Sitenin güncel adresine güncellendi.
    override var mainUrl = "https://jetfilmizle.vip"
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
        // Sitenin yeni yapısına uygun seçiciye güncellendi.
        val home = document.select("div.ml-item").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    // Film kartlarını okuyan fonksiyon, sitenin yeni HTML yapısına göre düzenlendi.
    private fun Element.toSearchResult(): SearchResponse? {
        val titleElement = this.selectFirst("h2.mli-title a") ?: return null
        val title = titleElement.text().substringBefore(" izle").trim()
        val href = fixUrl(titleElement.attr("href"))
        // Resimlerin "data-original" özelliğinden alınması sağlandı.
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-original"))

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    // Arama fonksiyonu, sitenin yeni GET tabanlı arama sistemine göre güncellendi.
    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document

        return document.select("div.ml-item").mapNotNull {
            it.toSearchResult()
        }
    }

    // Film detaylarını çeken fonksiyon, yeni HTML seçicilerine göre tamamen yeniden yazıldı.
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.title")?.text()?.substringBefore(" izle")?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.poster img")?.attr("src"))
        // Yıl bilgisi daha güvenilir bir yerden çekildi.
        val year = document.select("div.jfs-details div.fd-item:contains(Yıl) span").text().toIntOrNull()
        val description = document.selectFirst("div.synopsis p")?.text()?.trim()
        val tags = document.select("div.jfs-details div.fd-item:contains(Kategori) a").map { it.text() }
        val rating = document.selectFirst("div.jfs-details span.imdb")?.text()?.trim()?.toRatingInt()
        val actors = document.select("div.cast-list div.cast-item").mapNotNull {
            val name = it.selectFirst("div.ci-name")?.text() ?: return@mapNotNull null
            val image = fixUrlNull(it.selectFirst("img")?.attr("data-original"))
            Actor(name, image)
        }
        val recommendations = document.select("div.movies-list div.ml-item").mapNotNull { it.toSearchResult() }

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
override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.title")?.text()?.substringBefore(" izle")?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.poster img")?.attr("src"))
        val year = document.select("div.jfs-details div.fd-item:contains(Yıl) span").text().toIntOrNull()
        val description = document.selectFirst("div.synopsis p")?.text()?.trim()
        val tags = document.select("div.jfs-details div.fd-item:contains(Kategori) a").map { it.text() }
        val rating = document.selectFirst("div.jfs-details span.imdb")?.text()?.trim()?.toRatingInt()
        val actors = document.select("div.cast-list div.cast-item").mapNotNull {
            val name = it.selectFirst("div.ci-name")?.text() ?: return@mapNotNull null
            val image = fixUrlNull(it.selectFirst("img")?.attr("data-original"))
            Actor(name, image)
        }
        val recommendations = document.select("div.movies-list div.ml-item").mapNotNull { it.toSearchResult() }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            this.rating = rating
            this.recommendations = recommendations
            // DÜZELTME: 'addActors(actors)' yerine doğrudan atama yapıldı.
            this.actors = actors
        }
    }
