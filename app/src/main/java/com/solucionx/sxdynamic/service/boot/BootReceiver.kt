package com.solucionx.sxdynamic.service.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.solucionx.sxdynamic.core.Diagnostics
import com.solucionx.sxdynamic.core.OverlayServiceController
import com.solucionx.sxdynamic.core.appContainer

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val settings = context.appContainer.settingsRepository.read()
        if (!settings.enabled || !Settings.canDrawOverlays(context)) return
        val started = OverlayServiceController.start(context)
        if (!started) Diagnostics.warn("boot", "Overlay restoration was not accepted by the system")
    }
}
