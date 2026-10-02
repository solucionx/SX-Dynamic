package com.solucionx.sxdynamic.service.notification

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.solucionx.sxdynamic.core.Diagnostics
import com.solucionx.sxdynamic.core.appContainer
import com.solucionx.sxdynamic.domain.IslandContent

class SxNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        Diagnostics.info("notifications", "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        Diagnostics.warn("notifications", "Notification listener disconnected")
        NotificationListenerService.requestRebind(ComponentName(this, SxNotificationListenerService::class.java))
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (NotificationRules.shouldIgnore(packageName, sbn)) return

        val settings = appContainer.settingsRepository.read()
        val notification = sbn.notification
        val extras = notification.extras
        val appName = runCatching {
            val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(sbn.packageName)

        val redact = settings.privacyMode || notification.visibility == Notification.VISIBILITY_SECRET
        val title = if (redact) appName else {
            extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().ifBlank { appName }
        }
        val text = if (redact) "Conteúdo oculto" else extractText(notification)

        when {
            NotificationRules.isCall(notification) -> appContainer.overlayCoordinator.showNotification(
                IslandContent.NotificationEvent(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    appName = appName,
                    title = title,
                    text = text,
                    action = notification.contentIntent,
                    isCall = true,
                ),
                persistent = true,
            )

            NotificationRules.isTimer(notification) -> appContainer.overlayCoordinator.showTimer(
                IslandContent.Timer(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    appName = appName,
                    title = title,
                    text = text,
                    action = notification.contentIntent,
                ),
            )

            NotificationRules.isProgress(notification) -> {
                val max = extras?.getInt(Notification.EXTRA_PROGRESS_MAX, 0) ?: 0
                val progress = extras?.getInt(Notification.EXTRA_PROGRESS, 0) ?: 0
                appContainer.overlayCoordinator.showProgress(
                    IslandContent.Progress(
                        key = sbn.key,
                        packageName = sbn.packageName,
                        appName = appName,
                        title = title,
                        progress = progress.coerceAtLeast(0),
                        max = max.coerceAtLeast(0),
                        action = notification.contentIntent,
                    ),
                )
            }

            else -> appContainer.overlayCoordinator.showNotification(
                IslandContent.NotificationEvent(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    appName = appName,
                    title = title,
                    text = text,
                    action = notification.contentIntent,
                ),
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.key?.let(appContainer.overlayCoordinator::clear)
    }

    private fun extractText(notification: Notification): String {
        val extras = notification.extras ?: return ""
        val direct = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (direct.isNotBlank()) return direct
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        if (big.isNotBlank()) return big
        return extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString(" · ") { it.toString() }
            .orEmpty()
    }
}
