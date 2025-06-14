package com.yenikaynak

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

// Eklentinin ana sınıfını oluşturuyoruz.
class Yenikaynak : MainAPI() {
    // Eklenti ile ilgili temel bilgileri tanımlıyoruz.
    override var mainUrl = "https://www.yenikaynak.com"
    override var name = "Yenikaynak"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    // Anasayfadaki kategoriler korunuyor.
    override val mainPage = mainPageOf(
        "/diziler/page/" to "Son Eklenen Diziler",
        "/tasavvufi-filmler/page/" to "Tasavvufi Filmler",
        "/aile-filmleri/page/" to "Aile Filmleri",
        "/genclik-filmleri/page/" to "Gençlik Filmleri",
        "/savas/page/" to "Savaş Filmleri",
        "/komedi-filmleri/page/" to "Komedi Filmleri",
        "/romantik-filmleri/page/" to "Romantik Filmleri",
        "/tarihi-film-ve-diziler/page/" to "Tarihi Filmler ve Diziler",
        "/dram-filmleri" to "Dram Filmleri",
    )

    // Anasayfa ve arama sonuçları listeleme fonksiyonu.
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = mainUrl + request.data + page
        val document = app.get(url).document

        // Liste öğesi seçicisi ("article") hala geçerli. Sorun, öğe içindeki detayları çeken fonksiyondaydı.
        val home = document.select("article").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document
        return document.select("article").mapNotNull {
            it.toSearchResult()
        }
    }

    // Seçilen bir film veya dizinin detaylarını yükleyen fonksiyon.
    // DEĞİŞİKLİK: Bu fonksiyon, film ve dizi sayfalarının farklı yapılarını ele alacak şekilde tamamen yeniden yazıldı.
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val isTvSeries = url.contains("/dizi/")

        return if (isTvSeries) {
            // --- DİZİ SAYFASI İŞLEMLERİ ---
            val title = document.selectFirst("div.data > h1")?.text() ?: ""
            val posterUrl = document.selectFirst("div.poster > img")?.attr("src")
            val plot = document.selectFirst("div.wp-content > p")?.text()?.trim()
            val year = document.selectFirst("span.year")?.text()?.toIntOrNull()

            val episodes = document.select("div.se-c").flatMap { seasonContainer ->
                val seasonId = seasonContainer.attr("id").filter { it.isDigit() }.toIntOrNull()
                seasonContainer.select("ul.episodios > li").mapNotNull { el ->
                    val href = el.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                    val epNumStr = el.selectFirst("div.numerando")?.text()
                    val epTitle = el.selectFirst("div.episodiotitle")?.text()
                    newEpisode(href) {
                        name = if (epTitle.isNullOrBlank()) epNumStr else "$epNumStr - $epTitle"
                        season = seasonId
                        episode = epNumStr?.filter { it.isDigit() }?.toIntOrNull()
                    }
                }
            }.reversed()

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = posterUrl
                this.plot = plot
                this.year = year
            }
        } else {
            // --- FİLM SAYFASI İŞLEMLERİ ---
            val title = document.selectFirst("h1.entry-title")?.text() ?: ""
            val posterUrl = document.selectFirst("div.post-thumbnail img")?.attr("src")
            val plot = document.selectFirst("div.entry-content")?.text()?.trim()

            // Filmlerin bölümü olmadığı için video linki direkt bu sayfanın URL'si ile yüklenir.
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = posterUrl
                this.plot = plot
            }
        }
    }

    // Video linklerini (kaynaklarını) çıkaran fonksiyon.
    // DEĞİŞİKLİK: Video oynatıcısının iframe'ini daha kesin bir seçici ile buluyor.
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        // Film ve dizi bölümlerinde ortak olan oynatıcı seçicisi kullanılıyor.
        document.select("div.peli-info iframe, div.source-box iframe").forEach {
            val embedUrl = it.attr("src")
            if (embedUrl.isNotBlank()) {
                loadExtractor(embedUrl, data, subtitleCallback, callback)
            }
        }
        return true
    }

    // HTML elementini bir SearchResponse nesnesine dönüştüren yardımcı fonksiyon.
    // DEĞİŞİKLİK: Liste ve arama sonuçlarındaki öğelerin yeni yapısına göre güncellendi.
    private fun Element.toSearchResult(): SearchResponse? {
        val titleElement = this.selectFirst("h2.entry-title a") ?: return null
        val href = titleElement.attr("href")
        // Geçersiz veya reklam linklerini atla
        if (!href.startsWith(mainUrl)) return null

        val title = titleElement.text()
        val posterUrl = this.selectFirst("div.post-thumbnail img")?.attr("src")

        return if (href.contains("/dizi/")) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }
}
