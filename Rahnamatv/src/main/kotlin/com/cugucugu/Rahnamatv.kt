package com.cugucugu

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class Rahnamatv : MainAPI() {
    override var mainUrl              = "https://rahnama.tv"
    override var name                 = "Rahnamatv"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}/sinemalar/"      to "Sinemalar",
        "${mainUrl}/diziler/"   to "Diziler",
        "${mainUrl}/kısa-filmler/" to "Kısa Filmler",
        "${mainUrl}/muzikler/"  to "Müzikler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}").document
        val home = document.select("div.post-items div.col-lg-3.col-md-6").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("div.elementskit-post-body  a")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("div.elementskit-post-body  a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.elementskit-post-image-card img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title     = this.selectFirst("div.title a")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("div.title a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

      override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        // DÜZELTME: Başlık ve poster seçicileri detay sayfasına göre güncellendi.
        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: return null
        val poster = document.selectFirst("div.elementskit-entry-header img")?.attr("src")
        
        // DÜZELTME: Sitede bu detaylar olmadığı için varsayılan bir açıklama eklendi.
        val plot = "Rahnama.tv'de yayınlanan içerik."

        val isSeriesHomePage = url.contains("/diziler/") && !url.substringAfter("/diziler/").contains("-")

        return if (isSeriesHomePage) {
            val episodes = ArrayList<Episode>()
            val seriesBaseSlug = url.trimEnd('/').substringAfterLast('/')

            // /kaybeden-1, -2, ... şeklinde bölümleri arar
            for (i in 1..50) {
                val episodeUrl = "$mainUrl/$seriesBaseSlug-$i/"
                try {
                    // Sayfanın var olup olmadığını kontrol etmek için HEAD isteği kullanmak daha verimlidir.
                    val response = app.head(episodeUrl, referer = url)
                    if (response.code == 200) {
                        episodes.add(newEpisode(episodeUrl) {
                            name = "Bölüm $i"
                            episode = i
                        })
                    } else {
                        break
                    }
                } catch (e: Exception) {
                    break
                }
            }
            
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
            }
        }
    }
    // Video linkini (örn: ok.ru) çeker
    override suspend fun loadLinks(
        data: String, // `load` fonksiyonundan gelen film/bölüm URL'si
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        // `entry-content` içindeki `ok.ru` iframe'ini hedefliyoruz
        val iframeSrc = document.selectFirst("div.entry-content iframe[src*='ok.ru']")?.attr("src") ?: return false

        // URL'nin "https:" ile başladığından emin oluyoruz
        val fullUrl = if (iframeSrc.startsWith("//")) "https:$iframeSrc" else iframeSrc

        // Cloudstream'in dahili link ayıklayıcısını çağırıyoruz
        return loadExtractor(fullUrl, data, subtitleCallback, callback)
    }
}
