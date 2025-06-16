package com.rahnamatv

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.extractors.YoutubeExtractor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import org.jsoup.Jsoup

class RahnamaTvProvider : MainAPI() {
    override var mainUrl              = "https://rahnama.tv"
    override var name                 = "RahnamaTV"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false


    // Müzik kategorisi kaldırıldığı için desteklenen türler güncellendi.
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

override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    val url = if (page == 1) request.data else "${request.data.removeSuffix("/")}/$page"
    val document = app.get(url).document
    val home = document.select("div.poster-long").mapNotNull { it.toSearchResult() }

    return newHomePageResponse(request.name, home)
}


private fun Element.toSearchResult(): SearchResponse? {
    val title = this.selectFirst("a.block img")?.attr("alt")?.trim() ?: return null
    val href = fixUrlNull(this.selectFirst("a.block")?.attr("href")) ?: return null
    
    val imgElement = this.selectFirst("a.block img.lazy")
    if (imgElement == null) {
        Log.d("FLB", "imgElement is null")
        return null
    }

    val posterUrl = when {
        imgElement.hasAttr("data-src") && imgElement.attr("data-src").isNotBlank() -> {
            Log.d("FLB", "Using data-src: ${imgElement.attr("data-src")}")
            fixUrlNull(imgElement.attr("data-src"))
        }
        imgElement.hasAttr("src") && imgElement.attr("src").isNotBlank() -> {
            Log.d("FLB", "Using src: ${imgElement.attr("src")}")
            fixUrlNull(imgElement.attr("src"))
        }
        else -> {
            Log.d("FLB", "No valid src or data-src found")
            null
        }
    } ?: return null

    return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
}

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title  = document.selectFirst("div.page-title h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content")) ?: return null
        val trailerId = document.selectFirst("div.series-profile-trailer")?.attr("data-yt")
        val trailerUrl = trailerId?.takeIf { it.isNotEmpty() }?.let { "https://www.youtube.com/watch?v=$it" }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            addTrailer(trailerUrl)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("FLB", "data » $data")
        val document = app.get(data).document

        document.select("div#tv-spoox2").forEach {
            val iframe = fixUrlNull(it.selectFirst("iframe")?.attr("src")) ?: return@forEach
            Log.d("FLB", "iframe » $iframe")

            loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
        }

        return true
    }
}
