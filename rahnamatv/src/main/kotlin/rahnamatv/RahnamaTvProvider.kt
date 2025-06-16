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

    // Müzik kategorisi kaldırıldığı için desteklenen türler güncellendi.
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
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
            // İsteğiniz üzerine, TvType'ı belirlemek için kategori adı gönderiliyor.
            toSearchResponse(it, request.name)
        }
        return newHomePageResponse(request.name, home)
    }

    private fun toSearchResponse(element: Element, category: String): SearchResponse? {
        val linkElement = element.selectFirst("a") ?: return null
        val href = fixUrl(linkElement.attr("href"))
        val title = linkElement.selectFirst("div.data h3")?.text() ?: linkElement.attr("title")
        val posterUrl = fixUrl(linkElement.selectFirst("div.image img")?.attr("src") ?: "")

        // TvType, URL yerine doğrudan kategori adına göre belirleniyor.
        val tvType = when (category.uppercase()) {
            "DİZİLER" -> TvType.TvSeries
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

        // İsteğiniz üzerine: URL'de tire olmaması, ana dizi sayfası olduğunu varsayar.
        // Örn: /kaybeden/ -> Dizi, /kaybeden-1/ -> Bölüm (Film olarak işlenir)
        val isSeriesHomePage = url.contains("/diziler/") && !url.substringAfter("/diziler/").contains("-")

        return if (isSeriesHomePage) {
            val episodes = ArrayList<Episode>()
            val seriesBaseSlug = url.trimEnd('/').substringAfterLast('/')

            // /kaybeden-1, -2, ... şeklinde bölümleri arar
            for (i in 1..50) {
                val episodeUrl = "$mainUrl/$seriesBaseSlug-$i/"
                try {
                    val episodeDoc = app.get(episodeUrl, interceptor = interceptor, referer = url).document
                    if (episodeDoc.select("iframe, video").isNotEmpty()) {
                        // HATA DÜZELTMESİ: Tavsiye edilen newEpisode fonksiyonu kullanıldı.
                        episodes.add(newEpisode(episodeUrl) {
                            name = "Bölüm $i"
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
            // Bu bir film, kısa film, müzik veya tek bir dizi bölümüdür.
            // Hepsi tek bir video içerdiği için MovieLoadResponse olarak işlenir.
            newMovieLoadResponse(title, url, TvType.Movie, url) {
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
