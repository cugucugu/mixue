package com.yenikaynak

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class YenikaynakPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(YenikaynakProvider())
    }
}
