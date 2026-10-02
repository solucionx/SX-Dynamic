package com.solucionx.sxdynamic.domain

import android.os.Handler
import android.os.Looper
import com.solucionx.sxdynamic.data.SettingsRepository
import java.util.concurrent.CopyOnWriteArraySet

class OverlayCoordinator(
    private val settingsRepository: SettingsRepository,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<(OverlayUiState) -> Unit>()

    private var media: IslandContent.Media? = null
    private var transientKey: String? = null
    private var transientContent: IslandContent? = null
    private var transientPriority: Int = 0
    private var dismissRunnable: Runnable? = null
    private var state = OverlayUiState()

    fun snapshot(): OverlayUiState = state

    fun addListener(listener: (OverlayUiState) -> Unit) {
        listeners += listener
        mainHandler.post { listener(state) }
    }

    fun removeListener(listener: (OverlayUiState) -> Unit) {
        listeners -= listener
    }

    fun showNotification(content: IslandContent.NotificationEvent, persistent: Boolean = false) {
        val settings = settingsRepository.read()
        if (!settings.showNotifications) return
        showTransient(
            key = content.key,
            content = content,
            priority = if (content.isCall) PRIORITY_CALL else PRIORITY_NOTIFICATION,
            timeoutMs = if (persistent || content.isCall) null else settings.notificationDurationSeconds * 1_000L,
        )
    }

    fun showProgress(content: IslandContent.Progress) {
        if (!settingsRepository.read().showProgress) return
        showTransient(content.key, content, PRIORITY_PROGRESS, null)
    }

    fun showTimer(content: IslandContent.Timer) {
        if (!settingsRepository.read().showTimers) return
        showTransient(content.key, content, PRIORITY_TIMER, null)
    }

    fun showBattery(level: Int, charging: Boolean, plugged: Boolean) {
        if (!settingsRepository.read().showBattery) return
        showTransient(
            key = BATTERY_KEY,
            content = IslandContent.Battery(level.coerceIn(0, 100), charging, plugged),
            priority = PRIORITY_BATTERY,
            timeoutMs = 3_000L,
        )
    }

    fun updateMedia(content: IslandContent.Media?) {
        media = content?.takeIf { settingsRepository.read().showMedia }
        if (transientContent == null) publish(baseContent(), state.expanded)
    }

    fun clear(key: String) {
        runOnMain {
            if (transientKey == key) {
                clearTransientInternal()
                publish(baseContent(), false)
            }
        }
    }

    fun toggleExpanded() = runOnMain { publish(state.content, !state.expanded) }

    fun collapse() = runOnMain { if (state.expanded) publish(state.content, false) }

    fun refreshFromSettings() = runOnMain {
        val settings = settingsRepository.read()
        if (!settings.showMedia) media = null
        publish(transientContent ?: baseContent(), state.expanded)
    }

    private fun showTransient(key: String, content: IslandContent, priority: Int, timeoutMs: Long?) {
        runOnMain {
            if (transientContent != null && priority < transientPriority) return@runOnMain
            cancelDismiss()
            transientKey = key
            transientContent = content
            transientPriority = priority
            publish(content, true)

            if (timeoutMs != null) {
                val expectedKey = key
                dismissRunnable = Runnable {
                    if (transientKey == expectedKey) {
                        clearTransientInternal()
                        publish(baseContent(), false)
                    }
                }.also { mainHandler.postDelayed(it, timeoutMs) }
            }
        }
    }

    private fun baseContent(): IslandContent = media ?: IslandContent.Idle

    private fun clearTransientInternal() {
        cancelDismiss()
        transientKey = null
        transientContent = null
        transientPriority = 0
    }

    private fun cancelDismiss() {
        dismissRunnable?.let(mainHandler::removeCallbacks)
        dismissRunnable = null
    }

    private fun publish(content: IslandContent, expanded: Boolean) {
        state = OverlayUiState(content, expanded, state.revision + 1)
        listeners.forEach { listener -> runCatching { listener(state) } }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private companion object {
        const val BATTERY_KEY = "__battery__"
        const val PRIORITY_BATTERY = 40
        const val PRIORITY_PROGRESS = 55
        const val PRIORITY_NOTIFICATION = 70
        const val PRIORITY_TIMER = 90
        const val PRIORITY_CALL = 100
    }
}
