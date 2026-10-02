package com.solucionx.sxdynamic.core

import android.content.Context
import android.content.Intent
import com.solucionx.sxdynamic.service.overlay.DynamicOverlayService

object OverlayServiceController {
    fun start(context: Context): Boolean {
        if (!PermissionState.read(context).overlay) {
            Diagnostics.warn("overlay", "Overlay start ignored because permission is missing")
            return false
        }
        return runCatching {
            context.startForegroundService(Intent(context, DynamicOverlayService::class.java))
            true
        }.onFailure {
            Diagnostics.error("overlay", "Foreground service start failed", it)
        }.getOrDefault(false)
    }

    fun stop(context: Context) {
        runCatching { context.stopService(Intent(context, DynamicOverlayService::class.java)) }
            .onFailure { Diagnostics.error("overlay", "Foreground service stop failed", it) }
    }
}
