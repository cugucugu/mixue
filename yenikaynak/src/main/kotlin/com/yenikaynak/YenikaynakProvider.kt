package com.yenikaynak

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

// Eklentinin ana sınıfı
class Yenikaynak : MainAPI() {
    // Eklenti ile ilgili temel bilgiler
    override var mainUrl = "https://www.yenikaynak.com"
    override var name = "Yenikaynak"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    // Anasayfa kategorileri
    override val mainPage = mainPageOf(
        "/diziler/page/" to "Son Eklenen Diziler",
        "/tasavvufi-filmler/page/" to "Tasavvufi Filmler",
        "/aile-filmleri/page/" to "Aile Filmleri",
        "/genclik-filmleri/page/" to "Gençlik Filmleri",
        "/savas/page/" to "Savaş Filmleri",
        "/komedi-filmleri/page/" to "Komedi Filmleri",
        "/romantik-filmleri/page/" to "Romantik Filmleri",
        "/tarihi-film-ve-diziler/page/" to "Tarihi Filmler ve Diziler",
        "/dram-filmleri" to "Dram Filmleri"
    )

    // Anasayfa ve arama sonuçları listeleme fonksiyonu
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Not: Bazı kategorilerde /page/ yapısı çalışmayabilir, site yapısıyla ilgili.
        // Eğer bir kategori boş gelirse, linkten "/page/" kısmını kaldırmak gerekebilir.
        val url = mainUrl + request.data + page
        val document = app.get(url).document

        // DEĞİŞİKLİK: Liste öğesi seçicisi "div.movies-list > div.ml-item" olarak güncellendi.
        // Bu, sitedeki film ve dizi listeleri için daha doğru bir seçicidir.
        val home = document.select("div.movies-list > div.ml-item").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document
        // Arama sonuçları da aynı yapıyı kullanıyor.
        return document.select("div.movies-list > div.ml-item").mapNotNull {
            it.toSearchResult()
        }
    }

    // Seçilen bir film veya dizinin detaylarını yükleyen fonksiyon
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val isTvSeries = url.contains("/dizi/")

        return if (isTvSeries) {
            // --- DİZİ SAYFASI İŞLEMLERİ ---
            // DEĞİŞİKLİK: Dizi detayları için seçiciler tamamen güncellendi.
            val title = document.selectFirst("div.mvic-desc > h3")?.text()?.trim() ?: ""
            // GÖRSEL URL DÜZELTMESİ: Hatalı thumbnail yolunu kaldırır.
            val posterUrl = document.selectFirst("div.mvic-thumb > img")?.attr("src")?.replace(Regex("""/thumb_.*?/"""), "/")
            val plot = document.selectFirst("div.mov-desc")?.text()?.trim()
            val year = document.select("div.mvic-info div.mvici-right a[href*=/year/]").firstOrNull()?.text()?.toIntOrNull()

            // DEĞİŞİKLİK: Sezon ve bölüm listesi seçicileri güncellendi.
            val episodes = document.select("ul.episodios > li").mapNotNull { el ->
                val href = el.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val epNumStr = el.selectFirst("div.numerando")?.text()
                val epTitle = el.selectFirst("div.episodiotitle")?.text()
                val seasonNum = el.parent()?.parent()?.previousElementSibling()?.selectFirst("span.se-p")?.text()?.filter { it.isDigit() }?.toIntOrNull()

                newEpisode(href) {
                    name = if (epTitle.isNullOrBlank()) epNumStr else "$epNumStr - $epTitle"
                    season = seasonNum
                    episode = epNumStr?.filter { it.isDigit() }?.toIntOrNull()
                }
            }.reversed()

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = posterUrl
                this.plot = plot
                this.year = year
            }
        } else {
            // --- FİLM SAYFASI İŞLEMLERİ ---
            // DEĞİŞİKLİK: Film detayları için de seçiciler güncellendi.
            val title = document.selectFirst("div.mvic-desc > h3")?.text()?.trim() ?: ""
            // GÖRSEL URL DÜZELTMESİ: Hatalı thumbnail yolunu kaldırır.
            val posterUrl = document.selectFirst("div.mvic-thumb > img")?.attr("src")?.replace(Regex("""/thumb_.*?/"""), "/")
            val plot = document.selectFirst("div.mov-desc")?.text()?.trim()
            val year = document.select("div.mvic-info div.mvici-right a[href*=/year/]").firstOrNull()?.text()?.toIntOrNull()

            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = posterUrl
                this.plot = plot
                this.year = year
            }
        }
    }

    // Video linklerini çıkaran fonksiyon
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        // DEĞİŞİKLİK: Oynatıcı linklerini içeren `data-url` attribute'u hedeflendi.
        document.select("div.video-options ul li a").forEach {
            val embedUrl = it.attr("data-url")
            if (embedUrl.isNotBlank()) {
                loadExtractor(embedUrl, data, subtitleCallback, callback)
            }
        }
        return true
    }

    // HTML elementini arama/liste sonucuna dönüştüren yardımcı fonksiyon
    private fun Element.toSearchResult(): SearchResponse? {
        val linkElement = this.selectFirst("a") ?: return null
        val href = linkElement.attr("href")
        if (!href.startsWith(mainUrl)) return null

        // DEĞİŞİKLİK: Başlık ve poster için daha doğru seçiciler kullanıldı.
        // Lazy loading için "data-original" deneniyor, yoksa "src" alınıyor.
        val title = linkElement.attr("title")
        // GÖRSEL URL DÜZELTMESİ: Hatalı thumbnail yolunu kaldırır.
        val posterUrl = linkElement.selectFirst("img")?.let { it.attr("data-original").ifBlank { it.attr("src") } }?.replace(Regex("""/thumb_.*?/"""), "/")

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
