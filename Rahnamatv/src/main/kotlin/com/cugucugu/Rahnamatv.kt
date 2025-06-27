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
        val home = document.select("div.page-content col-lg-3 col-md-6").mapNotNull { it.toMainPageResult() }

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
    data: String, // Bu parametre, film sayfasının URL'sini içerir.
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    // 1. Verilen 'data' URL'sinden sayfanın HTML içeriğini al ve ayrıştır.
    val document = app.get(data).document

    // 2. HTML içinde 'div' class'ı 'page-content' olan etiketin içindeki 'iframe'i seç.
    // Bu, sayfadaki diğer iframelerle karışmasını engeller ve daha güvenilirdir.
    // 'src' özelliğini al. Eğer iframe veya src bulunamazsa, fonksiyon 'false' döner.
    val iframeSrc = document.selectFirst("div.page-content iframe")?.attr("src") ?: return false

    // 3. 'iframeSrc' bazen protokol olmadan ("//ok.ru/...") şeklinde gelebilir.
    // Bu durumu kontrol edip URL'nin başına "https:" ekleyerek tam bir URL oluşturuyoruz.
    val fullUrl = if (iframeSrc.startsWith("//")) {
        "https:$iframeSrc"
    } else {
        iframeSrc
    }

    // 4. Cloudstream'in dahili `loadExtractor` fonksiyonunu çağır.
    // Bu fonksiyon, "ok.ru" gibi bilinen video siteleri için doğru ayıklayıcıyı otomatik olarak bulur ve çalıştırır.
    // 'data' (sayfa URL'si) referans olarak gönderilir, bu bazı korumaları aşmak için önemlidir.
    // Fonksiyon, video linklerini bulursa 'true', bulamazsa 'false' döner.
    return loadExtractor(fullUrl, data, subtitleCallback, callback)
}