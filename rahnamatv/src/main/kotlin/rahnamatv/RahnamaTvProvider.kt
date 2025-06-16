package com.rahnamatv // Package is changed as per your suggestion

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.network.CloudflareKiller

// Using your suggested class name and structure with fixes.
class RahnamaTvProvider : MainAPI() {
    // API'nin temel bilgileri
    override var mainUrl = "https://rahnama.tv"
    override var name = "RahnamaTV"
    override val hasMainPage = true
    override var lang = "tr" // Site dili Türkçe
    
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Music
    )

    private val interceptor = CloudflareKiller()

    override val mainPage = mainPageOf(
        "$mainUrl/sinemalar/" to "SİNEMALAR",
        "$mainUrl/diziler/" to "DİZİLER",
        "$mainUrl/kisa-filmler/" to "KISA FİLMLER",
        "$mainUrl/muzikler/" to "MÜZİKLER"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data, interceptor = interceptor).document
        val home = document.select("div.item").mapNotNull {
            toSearchResponse(it)
        }
        // HATA DÜZELTMESİ: Fonksiyon overload belirsizliğini çözmek için 'hasNextPage' parametresi kaldırıldı.
        // Fonksiyonun bu versiyonunda hasNextPage varsayılan olarak false kabul edilir.
        return newHomePageResponse(request.name, home)
    }
    
    private fun toSearchResponse(element: Element): SearchResponse? {
        val link = element.selectFirst("a") ?: return null
        val href = fixUrl(link.attr("href"))
        val title = link.attr("title")
        val posterUrl = fixUrl(link.selectFirst("img")?.let { it.attr("data-src").ifEmpty { it.attr("src") } } ?: "")

        val tvType = when {
            href.contains("/diziler/") -> TvType.TvSeries
            href.contains("/muzikler/") -> TvType.Music
            else -> TvType.Movie
        }

        return newMovieSearchResponse(title, href, tvType) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, interceptor = interceptor).document
        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim() ?: return null
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
        val plot = document.selectFirst("meta[property=og:description]")?.attr("content")
        
        val tvType = when {
            url.contains("/diziler/") -> TvType.TvSeries
            url.contains("/muzikler/") -> TvType.Music
            else -> TvType.Movie
        }

        return if (tvType == TvType.TvSeries) {
            val episodes = ArrayList<Episode>()
            val seriesBaseSlug = url.trimEnd('/').substringAfterLast('/')
            
            for (i in 1..50) { // En fazla 50 bölüm varsayımı
                val episodeUrl = "$mainUrl/$seriesBaseSlug-$i/"
                try {
                    val episodeDoc = app.get(episodeUrl, interceptor = interceptor, referer = url).document
                    if (episodeDoc.select("iframe, video").isNotEmpty()) {
                         episodes.add(
                            Episode(
                                data = episodeUrl,
                                name = "Bölüm $i"
                            )
                        )
                    } else {
                        break
                    }
                } catch (e: Exception) {
                    break
                }
            }
            
            if (episodes.isEmpty() && document.select("iframe, video").isNotEmpty()) {
                episodes.add(Episode(data = url, name = "Film"))
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
            }
        } else { // Film, Kısa Film veya Müzik ise
            newMovieLoadResponse(title, url, tvType, url) {
                this.posterUrl = poster
                this.plot = plot
            }
        }
    }
    
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, interceptor = interceptor).document
        
        document.select("iframe").forEach { iframe ->
            val embedUrl = fixUrl(iframe.attr("src"))
            loadExtractor(embedUrl, data, subtitleCallback, callback)
        }
        
        return true
    }
}
