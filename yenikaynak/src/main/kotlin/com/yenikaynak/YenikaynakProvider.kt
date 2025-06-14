// Gerekli kütüphaneleri ve CloudStream API'lerini import ediyoruz.
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

// Eklentinin ana sınıfını oluşturuyoruz.
class Yenikaynak : MainAPI() {
    // Eklenti ile ilgili temel bilgileri tanımlıyoruz.
    override var mainUrl = "https://www.yenikaynak.com"
    override var name = "Yenikaynak"
    override val hasMainPage = true // Anasayfa desteği var.
    override var lang = "tr" // İçerik dili Türkçe.
    override val hasDownloadSupport = true // İndirme desteği var.
    override val supportedTypes = setOf( // Film ve dizi türlerini destekliyor.
        TvType.Movie,
        TvType.TvSeries
    )

    // Anasayfadaki içerikleri yükleyen fonksiyon.
    override val mainPage = mainPageOf(
        "/diziler" to "Son Eklenen Diziler",
        "/filmler" to "Son Eklenen Filmler",
    )

    // Anasayfa bölümlerini ve arama sonuçlarını işleyen fonksiyon.
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Siteye istek atıp HTML içeriğini alıyoruz.
        val document = app.get(mainUrl + request.data).document
        // Anasayfadaki her bir film/dizi kartını temsil eden HTML elementlerini seçiyoruz.
        // NOT: Sitenin tasarımı değişirse bu seçici ("div.ml-item") güncellenmelidir.
        val home = document.select("div.ml-item").mapNotNull {
            it.toSearchResult()
        }
        // Sonuçları bir başlık altında gruplayarak anasayfada gösteriyoruz.
        return newHomePageResponse(request.name, home)
    }

    // Arama fonksiyonu.
    override suspend fun search(query: String): List<SearchResponse> {
        // Arama için özel URL oluşturuyoruz.
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document

        // Arama sonuçları sayfasındaki her bir öğeyi seçiyoruz.
        // NOT: Sitenin tasarımı değişirse bu seçici ("div.ml-item") güncellenmelidir.
        return document.select("div.ml-item").mapNotNull {
            it.toSearchResult()
        }
    }

    // Seçilen bir film veya dizinin detaylarını yükleyen fonksiyon.
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        // Başlığı alıyoruz.
        val title = document.selectFirst("div.mvic-desc > h3")?.text()?.trim() ?: ""
        // Poster resminin URL'sini alıyoruz.
        val posterUrl = document.selectFirst("div.mvic-thumb > img")?.attr("src")
        // Film/dizi özetini alıyoruz.
        val plot = document.selectFirst("div.mov-desc")?.text()?.trim() ?: ""

        // Eğer bir diziyse, bölümleri de yüklüyoruz.
        val episodes = document.select("ul.episodios li").map {
            val epNum = it.selectFirst("div.numerando")?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull()
            val seasonNum = it.selectFirst("span.se-p")?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull()
            newEpisode(it.selectFirst("a")?.attr("href") ?: "") {
                name = it.selectFirst("div.episodiotitle > a")?.text()
                episode = epNum
                season = seasonNum
            }
        }.reversed() // Genellikle sitelerde bölümler tersten sıralanır.

        // İçeriğin türüne göre (dizi mi, film mi) uygun LoadResponse'u döndürüyoruz.
        return if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = posterUrl
                this.plot = plot
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = posterUrl
                this.plot = plot
            }
        }
    }

    // Video linklerini (kaynaklarını) çıkaran fonksiyon.
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Film/dizi sayfasının HTML'ini alıyoruz.
        val document = app.get(data).document
        // Video oynatıcısını içeren iframe'leri veya linkleri buluyoruz.
        // NOT: Sitenin oynatıcı yapısı değişirse bu seçiciler güncellenmelidir.
        document.select("div.video-options ul li a").forEach {
            // Linkin içerdiği URL'yi alıyoruz.
            val embedUrl = it.attr("data-url")
            // CloudStream'in yerleşik extractor'larını kullanarak video linkini çözmeye çalışıyoruz.
            loadExtractor(embedUrl, data, subtitleCallback, callback)
        }
        return true
    }

    // HTML elementini bir SearchResponse nesnesine dönüştüren yardımcı fonksiyon.
    private fun Element.toSearchResult(): SearchResponse? {
        val link = this.selectFirst("a") ?: return null
        val href = link.attr("href")
        // Geçerli bir URL değilse atla.
        if (!href.contains(mainUrl)) return null

        val title = this.selectFirst("h2")?.text() ?: this.selectFirst("span.mli-title")?.text() ?: ""
        val posterUrl = this.selectFirst("img")?.attr("data-original")

        // İçeriğin dizi mi film mi olduğunu URL'den anlamaya çalışıyoruz.
        return if (href.contains("/dizi/")) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }
}
