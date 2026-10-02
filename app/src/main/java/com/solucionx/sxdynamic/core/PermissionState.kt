package com.solucionx.sxdynamic.core

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import com.solucionx.sxdynamic.service.notification.SxNotificationListenerService

data class PermissionSnapshot(
    val overlay: Boolean,
    val notificationAccess: Boolean,
    val appNotifications: Boolean,
    val unrestrictedBattery: Boolean,
)

object PermissionState {
    fun read(context: Context): PermissionSnapshot {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val listener = listenerComponent(context)
        val power = context.getSystemService(PowerManager::class.java)
        return PermissionSnapshot(
            overlay = Settings.canDrawOverlays(context),
            notificationAccess = notificationManager.isNotificationListenerAccessGranted(listener),
            appNotifications = notificationManager.areNotificationsEnabled(),
            unrestrictedBattery = power.isIgnoringBatteryOptimizations(context.packageName),
        )
    }

    fun listenerComponent(context: Context): ComponentName =
        ComponentName(context, SxNotificationListenerService::class.java)
}
