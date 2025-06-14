package com.jetfilmizle

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

// Gerekli veri sınıfları ve JsUnpacker gibi araçlar eklendi.
import com.lagradost.cloudstream3.utils.JsUnpacker
// DÜZELTME: Eksik olan okhttp3 importları eklendi.
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

// JSON verilerini ayrıştırmak için gerekli veri sınıfları
private data class D2rsSource(
    val file: String,
    val type: String? = null,
    val label: String? = null
)

private data class VidBizSource(
    val file: String,
    val label: String
)

private data class VidBiz(
    val status: String,
    val sources: List<VidBizSource>
)

class JetFilmizle : MainAPI() {
    override var mainUrl = "https://jetfilmizle.watch"
    override var name = "JetFilmizle"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie)

    // Ana sayfa linkleri yeni yapıya uygun hale getirildi.
    override val mainPage = mainPageOf(
        "$mainUrl/page/" to "Son Filmler",
        "$mainUrl/kategoriler/netflix-filmleri/" to "Netflix",
        "$mainUrl/kategoriler/editorun-secimi/" to "Editörün Seçimi",
        "$mainUrl/kategoriler/turk-filmleri-izle/" to "Türk Filmleri",
        "$mainUrl/kategoriler/cizgi-filmler/" to "Çizgi Filmler",
        "$mainUrl/kategoriler/yesilcam-filmleri/" to "Yeşilçam Filmleri"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) "${request.data}page/$page/" else request.data
        val document = app.get(url).document
        // Seçici, sitenin mevcut yapısıyla tutarlı olacak şekilde korundu.
        val home = document.select("article.movie").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleElement = this.selectFirst("h2.entry-title a") ?: return null
        val title = titleElement.text().substringBefore(" izle").trim()
        val href = fixUrl(titleElement.attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("figure.post-thumbnail img")?.attr("data-src") ?: this.selectFirst("figure.post-thumbnail img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // Arama, POST yerine GET metodu ile yapılıyor.
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("article.movie").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text()?.substringBefore(" izle")?.trim()!!
        val poster = fixUrlNull(document.selectFirst("div.single-poster img")?.attr("src"))
        val year = document.select("div.film-meta span:contains(Yıl) a").text().toIntOrNull()
        val description = document.selectFirst("div.entry-content p")?.text()?.trim()
        val tags = document.select("div.film-meta span:contains(Kategori) a").map { it.text() }
        val rating = document.selectFirst("div.film-meta span.film-imdb-rating")?.text()?.trim()?.toRatingInt()
        
        val actors = document.select("div.film-meta span:contains(Oyuncular) a").map {
            ActorData(Actor(it.text()))
        }

        val recommendations = document.select("div.related-movies article.movie").mapNotNull { it.toSearchResult() }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            this.rating = rating
            this.recommendations = recommendations
            this.actors = actors
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        document.select("div.film-embed iframe, div.film-options a[data-iframe]").apmap { element ->
            val iframeUrl = element.attr("abs:src").ifBlank { element.attr("abs:data-iframe") }
            
            if (iframeUrl.contains("d2rs.com")) {
                val doc = app.get(iframeUrl).text
                val parameter = doc.substringAfter("form.append(\"q\", \"").substringBefore("\");")
                val d2List = app.post("https://d2rs.com/zeus/api.php", data = mapOf("q" to parameter), referer = iframeUrl).text
                val sources: List<D2rsSource> = mapper.readValue(d2List)
                sources.forEach {
                    // DÜZELTME: Kullanım dışı kalan ExtractorLink yapıcısı yerine newExtractorLink fonksiyonu kullanıldı.
                    callback.invoke(
                        newExtractorLink(
                            this.name,
                            "D2rs - ${it.label}",
                            "https://d2rs.com/zeus/${it.file}",
                            mainUrl,
                            getQualityFromName(it.label),
                            it.type == "video/mp4"
                        )
                    )
                }
            } else if (iframeUrl.contains("videolar.biz")) {
                val doc = app.get(iframeUrl, referer = mainUrl).document
                val script = doc.select("script").find { it.data().contains("eval(function(p,a,c,k,e,") }?.data() ?: return@apmap
                val unpacked = JsUnpacker(script).unpack() ?: return@apmap
                val kaken = unpacked.substringAfter("window.kaken=\"").substringBefore("\";")
                val apiUrl = "https://s2.videolar.biz/api/"
                val requestBody = kaken.toRequestBody("text/plain".toMediaType())
                val vidBizText = app.post(apiUrl, requestBody = requestBody, referer = iframeUrl).text
                val vidBizData: VidBiz = mapper.readValue(vidBizText)
                if (vidBizData.status == "ok") {
                    vidBizData.sources.forEach {
                        // DÜZELTME: Kullanım dışı kalan ExtractorLink yapıcısı yerine newExtractorLink fonksiyonu kullanıldı.
                        callback.invoke(
                            newExtractorLink(
                                this.name,
                                "VidBiz - ${it.label}",
                                it.file,
                                mainUrl,
                                getQualityFromName(it.label),
                                isM3u8 = true
                            )
                        )
                    }
                }
            } else {
                loadExtractor(iframeUrl, data, subtitleCallback, callback)
            }
        }
        return true
    }
}
