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

    // Anasayfadaki içerikleri yükleyen fonksiyon.
    // DEĞİŞİKLİK: Anasayfa kategorileri isteğiniz üzerine güncellendi.
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

    // Anasayfa bölümlerini ve arama sonuçlarını işleyen fonksiyon.
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Sayfa numarası ile birlikte tam URL'yi oluşturuyoruz.
        val url = mainUrl + request.data + page
        val document = app.get(url).document

        // DEĞİŞİKLİK: Sitenin yeni tasarımında içerik kutuları "div.ml-item" yerine "article" etiketi içinde.
        val home = document.select("article").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    // Arama fonksiyonu.
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document

        // DEĞİŞİKLİK: Arama sonuçları da artık "article" etiketi kullanıyor.
        return document.select("article").mapNotNull {
            it.toSearchResult()
        }
    }

    // Seçilen bir film veya dizinin detaylarını yükleyen fonksiyon.
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        // DEĞİŞİKLİK: Başlık, poster ve özet için seçiciler güncellendi.
        val title = document.selectFirst("div.single_tabs h1.title")?.text()?.trim() ?: ""
        val posterUrl = document.selectFirst("div.poster > img")?.attr("src")
        val plot = document.selectFirst("div.wp-content > p")?.text()?.trim() ?: ""
        val year = document.selectFirst("span.year")?.text()?.toIntOrNull()

        // DEĞİŞİKLİK: Bölüm listesinin seçicisi güncellendi.
        val episodes = document.select("ul.episodios > li").mapNotNull { el ->
            val href = el.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val epName = el.selectFirst("div.numerando")?.text()
            val epTitle = el.selectFirst("div.episodiotitle")?.text()
            val seasonNum = el.selectFirst("img")?.attr("src")?.substringAfter("s")?.substringBefore(".png")?.toIntOrNull()

            newEpisode(href) {
                name = if (!epTitle.isNullOrEmpty()) "$epName - $epTitle" else epName
                season = seasonNum
                // Bölüm numarasını "1. Bölüm" gibi metinlerden ayıklıyoruz.
                episode = epName?.filter { it.isDigit() }?.toIntOrNull()
            }
        }.reversed()

        return if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = posterUrl
                this.plot = plot
                this.year = year
            }
        } else {
            // Filmlerin video linkleri direkt "load" fonksiyonunda yüklendiği için "data" olarak URL'yi veriyoruz.
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = posterUrl
                this.plot = plot
                this.year = year
            }
        }
    }

    // Video linklerini (kaynaklarını) çıkaran fonksiyon.
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        // DEĞİŞİKLİK: Video kaynaklarını barındıran iframe'lerin seçicisi güncellendi.
        document.select("div.source-box iframe").forEach {
            val embedUrl = it.attr("src")
            // CloudStream'in yerleşik extractor'larını kullanarak video linkini çözmeye çalışıyoruz.
            loadExtractor(embedUrl, data, subtitleCallback, callback)
        }
        return true
    }

    // HTML elementini bir SearchResponse nesnesine dönüştüren yardımcı fonksiyon.
    private fun Element.toSearchResult(): SearchResponse? {
        // DEĞİŞİKLİK: Yeni yapıya göre link, başlık ve poster seçicileri güncellendi.
        val href = this.selectFirst("a")?.attr("href") ?: return null
        if (!href.startsWith(mainUrl)) return null

        val title = this.selectFirst("h2")?.text() ?: ""
        val posterUrl = this.selectFirst("img")?.attr("src")

        // URL'den içerik türünü anlama mantığı korunuyor.
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
