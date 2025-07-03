package com.cugucugu

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Rahnamatv : MainAPI() {
    override var mainUrl = "https://rahnama.tv"
    override var name = "Rahnamatv"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    // Dizi desteğini de ekliyoruz
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/sinemalar/" to "Sinemalar",
        "${mainUrl}/diziler/" to "Diziler",
        "${mainUrl}/kısa-filmler/" to "Kısa Filmler",
        "${mainUrl}/muzikler/" to "Müzikler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Sayfalama desteği (sonraki sayfalar için /page/2/ eklenir)
        val url = if (page <= 1) request.data else "${request.data}page/$page/"
        val document = app.get(url).document

        // Ana sayfadaki her bir içerik kartını seçiyoruz
        val home = document.select("div.post-items div.col-lg-3.col-md-6").mapNotNull {
            it.toSearchResult()
        }

        return newHomePageResponse(request.name, home)
    }

    // Bir HTML elementinden (karttan) arama sonucu oluşturan yardımcı fonksiyon
    private fun Element.toSearchResult(): SearchResponse? {
        val linkElement = this.selectFirst("h2.entry-title a") ?: return null
        val href = fixUrlNull(linkElement.attr("href")) ?: return null
        val title = linkElement.text()
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        // Linkin "/diziler/" içerip içermediğine bakarak içerik türünü anlıyoruz
        val tvType = if (href.contains("/diziler/")) TvType.TvSeries else TvType.Movie

        return newSearchResponse(title, href, tvType) {
            this.posterUrl = posterUrl
        }
    }
    
    // Film/Dizi detay sayfasını yükleyen fonksiyon
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: return null
        
        // Poster ve açıklamayı detay sayfasından almaya çalışıyoruz.
        // Bu sitede detay sayfalarında bu bilgiler her zaman bulunmuyor.
        val poster = fixUrlNull(document.selectFirst("div.page-content img")?.attr("src"))
        val plot = document.selectFirst("div.page-content p")?.text()?.trim()

        // URL'ye göre TV Dizisi mi yoksa Film mi olduğunu anlıyoruz
        val isTvSeries = url.contains("/diziler/")

        return if (isTvSeries) {
            // Bu bir dizi sayfası ise, bölüm linklerini arıyoruz.
            // Örnek: "Kaybeden" ana sayfasında "Kaybeden (1)", "Kaybeden (2)" gibi linkler bulunur.
            val episodes = document.select("div.page-content p a[href*='${url.trimEnd('/')}']").mapNotNull { el ->
                val epHref = fixUrlNull(el.attr("href")) ?: return@mapNotNull null
                val epName = el.text()
                val episode = epName.filter { it.isDigit() }.toIntOrNull()

                newEpisode(epHref) {
                    name = epName
                    this.episode = episode
                }
            }.reversed()

            // Eğer bölüm listesi boşsa ve URL bir bölüm linki ise (örn: /kaybeden-1),
            // o zaman bu tek bölümlük bir diziymiş gibi davran.
            if (episodes.isEmpty() && Regex("""-\d+/?$""").containsMatchIn(url)) {
                 newTvSeriesLoadResponse(title, url, TvType.TvSeries, listOf(
                     newEpisode(url) { name = title }
                 )) {
                    this.posterUrl = poster
                    this.plot = plot
                }
            } else {
                newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.plot = plot
                }
            }
        } else {
            // Bu bir film sayfası. loadLinks'e data olarak kendi URL'sini veriyoruz.
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
            }
        }
    }
    
    // Video linkini (iframe'den) çıkaran fonksiyon
    override suspend fun loadLinks(
        data: String, // `load` fonksiyonundan gelen film veya bölüm URL'si
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        // KRİTİK DÜZELTME: iframe'i bulmak için daha genel ve doğru bir seçici kullanıyoruz.
        // Bu seçici hem film hem de dizi bölümü sayfasında çalışacaktır.
        val iframeSrc = document.selectFirst("iframe[src*='ok.ru']")?.attr("src") ?: return false

        // URL'nin "https:" ile başladığından emin oluyoruz
        val fullUrl = if (iframeSrc.startsWith("//")) "https:$iframeSrc" else iframeSrc

        // Cloudstream'in dahili link ayıklayıcısını çağırıyoruz
        return loadExtractor(fullUrl, data, subtitleCallback, callback)
    }
}
