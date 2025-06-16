package com.rahnamatv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.network.CloudflareKiller

class RahnamaTvProvider : MainAPI() {
    override var mainUrl = "https://rahnama.tv"
    override var name = "RahnamaTV"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    private val cfKiller = CloudflareKiller()

    override val mainPage = mainPageOf(
        "${mainUrl}/sinemalar/" to "SİNEMALAR",
        "${mainUrl}/diziler/" to "DİZİLER",
        "${mainUrl}/kisa-filmler/" to "KISA FİLMLER",
        "${mainUrl}/muzikler/" to "MÜZİKLER"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data, interceptor = cfKiller).document
        val home = document.select("article.post").mapNotNull {
            it.toSearchResult(request.data)
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(categoryUrl: String): SearchResponse? {
        val titleElement = this.selectFirst("h2.entry-title a") ?: return null
        val title = titleElement.text().trim()
        val href = titleElement.attr("href")
        val poster = this.selectFirst("div.elementskit-entry-header img")?.attr("src")
        
        // URL yapısına göre tür belirleme
        val isSeries = when {
            href.contains(Regex("-\\d+/?$")) -> true // Bölüm URL'si
            categoryUrl.contains("/diziler/") -> true // Diziler kategorisindeyse
            else -> false
        }
        
        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query", interceptor = cfKiller).document
        return document.select("article.post").mapNotNull {
            it.toSearchResult("")
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, interceptor = cfKiller).document
        
        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: return null
        val poster = document.selectFirst("div.elementskit-entry-header img")?.attr("src")
        
        // Yıl ve açıklama bilgisi bu sitede bulunmuyor
        val description = "Rahnama.tv'de yayınlanan içerik"
        
        // URL yapısına göre tür belirleme
        val isSeries = when {
            url.contains(Regex("-\\d+/?$")) -> true // Bölüm URL'si
            url.contains(Regex("/diziler/[^-]+$")) -> true // Dizi ana sayfası
            else -> false
        }
        
        if (isSeries) {
            // Dizi bölümleri için
            val baseUrl = if (url.contains("-\\d+/?$".toRegex())) {
                url.substringBeforeLast("-")
            } else {
                url
            }
            
            // Bölümleri otomatik oluştur
            val episodes = (1..20).mapNotNull { episodeNum ->
                val episodeUrl = "$baseUrl-$episodeNum/"
                try {
                    app.get(episodeUrl, interceptor = cfKiller)
                    Episode(episodeUrl, "Bölüm $episodeNum", episodeNum)
                } catch (e: Exception) {
                    null
                }
            }
            
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            // Filmler için
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, interceptor = cfKiller).document
        val iframes = document.select("iframe").mapNotNull { it.attr("src") }
        
        iframes.forEach { iframeUrl ->
            if (iframeUrl.contains("ok.ru")) {
                // OK.ru video linklerini doğrudan kullanıyoruz
                val cleanUrl = iframeUrl.substringBefore("?")
                
                // newExtractorLink kullanarak deprecated uyarısını düzelttim
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "OK.ru",
                        url = cleanUrl,
                        referer = "https://ok.ru/",
                        quality = Qualities.Unknown.value,
                        type = INFER_TYPE
                    )
                )
            }
        }
        
        return iframes.isNotEmpty()
    }
}
