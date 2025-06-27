package com.cugucugu

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class RahnamatvPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Rahnamatv())
    }
}