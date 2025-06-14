package com.rahnamatv

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class RahnamaTvPlugin : Plugin() {
    // Bu fonksiyon, eklenti CloudStream tarafından yüklendiğinde çalışır
    override fun load(context: Context) {
        // Ana API sağlayıcısını (provider) kaydeder.
        registerMainAPI(RahnamaTvProvider())
    }
}
