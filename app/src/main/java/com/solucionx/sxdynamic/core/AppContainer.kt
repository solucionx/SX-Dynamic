package com.solucionx.sxdynamic.core

import android.content.Context
import com.solucionx.sxdynamic.data.SettingsRepository
import com.solucionx.sxdynamic.domain.OverlayCoordinator

class AppContainer(context: Context) {
    val context: Context = context.applicationContext
    val settingsRepository = SettingsRepository(this.context)
    val overlayCoordinator = OverlayCoordinator(settingsRepository)
}
