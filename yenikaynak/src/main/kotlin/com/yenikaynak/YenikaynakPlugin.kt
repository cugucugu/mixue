package com.yenikaynak

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class YenikaynakPlugin: Plugin() {
    override fun load(context: Context) {
        // Bu fonksiyon, eklentinin ana sınıfını CloudStream'e kaydeder.
        // Bu sayede uygulama, "Yenikaynak" adında yeni bir kaynak olduğunu anlar.
        registerMainAPI(Yenikaynak())
    }
}
