package com.solucionx.sxdynamic.core

import android.content.Context
import android.service.notification.NotificationListenerService

object NotificationListenerRuntime {
    @Volatile
    var connected: Boolean = false
        private set

    @Volatile
    var lastPackageName: String? = null
        private set

    @Volatile
    var receivedCount: Long = 0
        private set

    fun markConnected() {
        connected = true
        Diagnostics.info("notifications", "Notification listener connected")
    }

    fun markDisconnected() {
        connected = false
        Diagnostics.warn("notifications", "Notification listener disconnected")
    }

    fun markReceived(packageName: String) {
        receivedCount += 1
        lastPackageName = packageName
        Diagnostics.info("notifications", "Notification received from " + packageName)
    }

    fun requestRebindIfGranted(context: Context): Boolean {
        if (!PermissionState.read(context).notificationAccess) return false
        return runCatching {
            NotificationListenerService.requestRebind(PermissionState.listenerComponent(context))
            Diagnostics.info("notifications", "Requested notification listener rebind")
            true
        }.onFailure {
            Diagnostics.error("notifications", "Notification listener rebind failed", it)
        }.getOrDefault(false)
    }
}
