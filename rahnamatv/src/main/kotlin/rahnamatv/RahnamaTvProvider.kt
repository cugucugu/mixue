package com.rahnamatv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType

// Ana eklenti sınıfı.
// Siteden veri çekme, arama yapma ve video linklerini bulma mantığını içerir.
class RahnamaTvProvider : MainAPI() {
    // API'nin temel bilgileri
    override var mainUrl = "https://rahnama.tv"
    override var name = "RahnamaTV"
    override val hasMainPage = true // Ana sayfa içeriği var mı? Evet.
    override var lang = "tr" // İçerik dili Türkçe.
    
    // Desteklenen içerik türleri: Diziler ve Filmler.
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    // Cloudflare korumasını atlamak için interceptor.
    private val interceptor = CloudflareKiller()

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val document = app.get(mainUrl, interceptor = interceptor).document
        val homePageList = ArrayList<HomePageList>()

        document.select("section.slider_post").forEach { section ->
            val title = section.selectFirst("h2.title")?.text() ?: "Kategori"
            val items = section.select("div.item").mapNotNull { item ->
                toSearchResponse(item)
            }
            if (items.isNotEmpty()) {
                homePageList.add(HomePageList(title, items))
            }
        }
        
        if (homePageList.isEmpty()) return null
        return HomePageResponse(homePageList)
    }

    // Bir HTML elementini (genellikle bir film/dizi kartı)
    // CloudStream'in anlayacağı bir SearchResponse objesine dönüştürür.
    private fun toSearchResponse(element: Element): SearchResponse? {
        val link = element.selectFirst("a") ?: return null
        val href = fixUrl(link.attr("href"))
        val title = link.attr("title")
        val posterUrl = fixUrl(link.selectFirst("img")?.let { it.attr("data-src").ifEmpty { it.attr("src") } } ?: "")

        val tvType = if (href.contains("/series/")) TvType.TvSeries else TvType.Movie

        return newMovieSearchResponse(title, href, tvType) {
            this.posterUrl = posterUrl
        }
    }

    // Arama fonksiyonu.
    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/search?q=$query"
        val document = app.get(searchUrl, interceptor = interceptor).document

        return document.select("div.item").mapNotNull {
            toSearchResponse(it)
        }
    }

    // Bir film veya dizi sayfasına tıklandığında detayları yükler.
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, interceptor = interceptor).document
        val title = document.selectFirst("h1.title")?.text()?.trim() ?: return null
        val poster = fixUrl(document.selectFirst("div.poster img")?.attr("src") ?: "")
        val plot = document.selectFirst("div.story")?.text()?.trim()
        val year = document.select("div.info span a[href*='/year/']")?.text()?.toIntOrNull()
        val tags = document.select("div.info span a[href*='/genre/']").map { it.text() }
        val tvType = if (url.contains("/series/")) TvType.TvSeries else TvType.Movie

        return if (tvType == TvType.TvSeries) {
            val episodes = ArrayList<Episode>()
            document.select("div.seasons_list > ul").forEachIndexed { seasonIndex, seasonElement ->
                val seasonNum = seasonElement.parent()?.selectFirst("span")?.text()?.filter { it.isDigit() }?.toIntOrNull() ?: (seasonIndex + 1)
                
                seasonElement.select("li").forEach { episodeElement ->
                    val epLink = episodeElement.selectFirst("a") ?: return@forEach
                    val epHref = fixUrl(epLink.attr("href"))
                    val epName = epLink.text()
                    val episodeNum = epName.substringAfter("Bölüm").trim().toIntOrNull()
                    
                    episodes.add(
                        Episode(
                            data = epHref,
                            name = epName,
                            season = seasonNum,
                            episode = episodeNum
                        )
                    )
                }
            }
            // HATA DÜZELTMESİ: Fonksiyon LoadResponse üzerinden çağrıldı.
            LoadResponse.newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.reversed()) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
            }
        } else { // Film ise
            // HATA DÜZELTMESİ: Fonksiyon LoadResponse üzerinden çağrıldı.
            LoadResponse.newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
            }
        }
    }
    
    // Bölüm veya film için video kaynak linklerini (embed URL) bulur.
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
