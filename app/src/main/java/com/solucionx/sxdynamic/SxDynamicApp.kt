package com.solucionx.sxdynamic

import android.app.Application
import com.solucionx.sxdynamic.core.AppContainer
import com.solucionx.sxdynamic.core.Diagnostics

class SxDynamicApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Diagnostics.initialize(this)
        container = AppContainer(this)
        Diagnostics.info("app", "SX Dynamic process started")
    }
}
