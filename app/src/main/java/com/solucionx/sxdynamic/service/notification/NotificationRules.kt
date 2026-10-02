package com.solucionx.sxdynamic.service.notification

import android.app.Notification
import android.service.notification.StatusBarNotification

object NotificationRules {
    fun shouldIgnore(ownPackage: String, sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == ownPackage) return true
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return true
        if (notification.category == Notification.CATEGORY_TRANSPORT) return true

        val ongoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0
        if (ongoing && !isUsefulOngoing(notification)) return true
        return false
    }

    fun isCall(notification: Notification): Boolean =
        notification.category == Notification.CATEGORY_CALL

    fun isTimer(notification: Notification): Boolean =
        notification.category == Notification.CATEGORY_ALARM ||
            notification.category == Notification.CATEGORY_STOPWATCH

    fun isProgress(notification: Notification): Boolean {
        val extras = notification.extras ?: return notification.category == Notification.CATEGORY_PROGRESS
        val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        return notification.category == Notification.CATEGORY_PROGRESS || max > 0
    }

    private fun isUsefulOngoing(notification: Notification): Boolean =
        isCall(notification) || isTimer(notification) || isProgress(notification)
}
