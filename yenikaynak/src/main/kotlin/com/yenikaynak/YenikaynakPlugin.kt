package com.yenikaynak

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class YenikaynakPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Yenikaynak())
    }
}
