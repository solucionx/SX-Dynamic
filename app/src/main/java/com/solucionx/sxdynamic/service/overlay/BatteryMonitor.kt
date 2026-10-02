package com.solucionx.sxdynamic.service.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.solucionx.sxdynamic.domain.OverlayCoordinator

class BatteryMonitor(
    private val context: Context,
    private val coordinator: OverlayCoordinator,
) {
    private var registered = false
    private var lastPlugged: Boolean? = null
    private var lastCharging: Boolean? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
            handle(intent, announceChanges = true)
        }
    }

    fun start() {
        if (registered) return
        registered = true
        val sticky = context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        sticky?.let { handle(it, announceChanges = false) }
    }

    fun stop() {
        if (!registered) return
        runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }

    private fun handle(intent: Intent, announceChanges: Boolean) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val percent = if (level >= 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else return
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val pluggedValue = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val plugged = pluggedValue != 0
        val changed = lastPlugged != null && (plugged != lastPlugged || charging != lastCharging)
        lastPlugged = plugged
        lastCharging = charging
        if (announceChanges && changed) coordinator.showBattery(percent, charging, plugged)
    }
}
