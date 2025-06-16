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

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    private val interceptor = CloudflareKiller()

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
        val document = app.get(request.data, interceptor = interceptor).document
        // DÜZELTME: Sitenin ana liste yapısı 'article.post' olarak güncellendi.
        val home = document.select("article.post").mapNotNull {
            toSearchResponse(it, request.name)
        }
        return newHomePageResponse(request.name, home)
    }

    // DÜZELTME: Gönderdiğiniz HTML yapısına göre seçiciler tamamen değiştirildi.
    private fun toSearchResponse(element: Element, category: String): SearchResponse? {
        val titleElement = element.selectFirst("h2.entry-title a") ?: return null
        val href = fixUrl(titleElement.attr("href"))
        val title = titleElement.text().trim()
        val posterUrl = fixUrl(element.selectFirst("div.elementskit-entry-header img")?.attr("src") ?: "")

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
                    val response = app.head(episodeUrl, interceptor = interceptor, referer = url)
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

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, interceptor = interceptor).document

        // Video iframe'ini bulur ve kaynağını işler.
        document.select("iframe").forEach { iframe ->
            val embedUrl = fixUrl(iframe.attr("src"))
            loadExtractor(embedUrl, data, subtitleCallback, callback)
        }

        return true
    }
}
