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
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/sinemalar/" to "Sinemalar",
        "${mainUrl}/diziler/" to "Diziler",
        "${mainUrl}/kısa-filmler/" to "Kısa Filmler",
        "${mainUrl}/muzikler/" to "Müzikler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data}page/$page/"
        val document = app.get(url).document

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
        val tvType = if (href.contains("/diziler/")) TvType.TvSeries else TvType.Movie

        // HATA DÜZELTMESİ: İçerik türüne göre doğru fonksiyonu çağırıyoruz.
        return if (tvType == TvType.TvSeries) {
            newTvSeriesSearchResponse(title, href) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href) {
                this.posterUrl = posterUrl
            }
        }
    }

    // Film/Dizi detay sayfasını yükleyen fonksiyon
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: return null

        val poster = fixUrlNull(document.selectFirst("div.page-content img")?.attr("src"))
        val plot = document.selectFirst("div.page-content > p")?.text()?.trim()
        val isTvSeries = url.contains("/diziler/")

        return if (isTvSeries) {
            val episodes = document.select("div.page-content p a[href*='${url.trimEnd('/')}']").mapNotNull { el ->
                val epHref = fixUrlNull(el.attr("href")) ?: return@mapNotNull null
                val epName = el.text()
                val episode = epName.filter { it.isDigit() }.toIntOrNull()
                newEpisode(epHref) {
                    name = epName
                    this.episode = episode
                }
            }.reversed()

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
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
            }
        }
    }

    // Video linkini (iframe'den) çıkaran fonksiyon
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val iframeSrc = document.selectFirst("iframe[src*='ok.ru']")?.attr("src") ?: return false
        val fullUrl = if (iframeSrc.startsWith("//")) "https:" + iframeSrc else iframeSrc

        return loadExtractor(fullUrl, data, subtitleCallback, callback)
    }
}
