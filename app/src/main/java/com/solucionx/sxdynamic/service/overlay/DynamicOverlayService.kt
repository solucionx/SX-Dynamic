package com.solucionx.sxdynamic.service.overlay

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import com.solucionx.sxdynamic.MainActivity
import com.solucionx.sxdynamic.R
import com.solucionx.sxdynamic.core.Diagnostics
import com.solucionx.sxdynamic.core.appContainer
import com.solucionx.sxdynamic.data.DynamicSettings
import com.solucionx.sxdynamic.domain.OverlayUiState
import kotlin.math.max

class DynamicOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var displayManager: DisplayManager
    private lateinit var view: DynamicIslandView
    private lateinit var mediaMonitor: MediaMonitor
    private lateinit var batteryMonitor: BatteryMonitor
    private lateinit var params: WindowManager.LayoutParams

    private val mainHandler = Handler(Looper.getMainLooper())

    private var added = false
    private var sizeAnimator: ValueAnimator? = null
    private var latestState = OverlayUiState()
    private var latestSettings = DynamicSettings()

    private val relayoutRunnable = Runnable {
        if (!added) return@Runnable
        sizeAnimator?.cancel()
        render(animate = false)
        Diagnostics.info("overlay", "Overlay geometry refreshed after display change")
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            scheduleGeometryRefresh()
        }
    }

    private val stateListener: (OverlayUiState) -> Unit = { state ->
        latestState = state
        if (added) render(animate = true)
    }

    private val settingsListener: (DynamicSettings) -> Unit = { settings ->
        latestSettings = settings
        if (!settings.enabled) {
            stopSelf()
        } else if (added) {
            appContainer.overlayCoordinator.refreshFromSettings()
            render(animate = false)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        displayManager = getSystemService(DisplayManager::class.java)
        mediaMonitor = MediaMonitor(this, appContainer.overlayCoordinator)
        batteryMonitor = BatteryMonitor(this, appContainer.overlayCoordinator)
        view = DynamicIslandView(this, appContainer.overlayCoordinator, mediaMonitor)
        latestSettings = appContainer.settingsRepository.read()
        latestState = appContainer.overlayCoordinator.snapshot()
        createNotificationChannel()
        startAsForeground()
        displayManager.registerDisplayListener(displayListener, mainHandler)
        appContainer.overlayCoordinator.addListener(stateListener)
        appContainer.settingsRepository.addListener(settingsListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            Diagnostics.warn("overlay", "Overlay permission missing; service stopped")
            stopSelf()
            return START_NOT_STICKY
        }
        ensureOverlay()
        mediaMonitor.start()
        batteryMonitor.start()
        Diagnostics.info("overlay", "Dynamic overlay started")
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        scheduleGeometryRefresh()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(relayoutRunnable)
        sizeAnimator?.cancel()
        runCatching { displayManager.unregisterDisplayListener(displayListener) }
        batteryMonitor.stop()
        mediaMonitor.stop()
        appContainer.overlayCoordinator.removeListener(stateListener)
        appContainer.settingsRepository.removeListener(settingsListener)
        if (added) runCatching { windowManager.removeViewImmediate(view) }
        added = false
        Diagnostics.info("overlay", "Dynamic overlay stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureOverlay() {
        if (added) return
        val width = dp(latestSettings.collapsedWidthDp)
        val height = dp(latestSettings.collapsedHeightDp)
        params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        applyPosition(width)
        runCatching {
            windowManager.addView(view, params)
            added = true
            render(animate = false)
        }.onFailure {
            Diagnostics.error("overlay", "Unable to add overlay window", it)
            stopSelf()
        }
    }

    private fun render(animate: Boolean) {
        if (!added) return
        view.render(latestState, latestSettings)
        val desiredWidth = if (latestState.expanded) {
            latestSettings.expandedWidthDp
        } else {
            latestSettings.collapsedWidthDp
        }
        val desiredHeight = if (latestState.expanded) {
            latestSettings.expandedHeightDp
        } else {
            latestSettings.collapsedHeightDp
        }
        val screenWidth = screenWidthPx()
        val targetWidth = dp(desiredWidth).coerceAtMost(max(dp(120), screenWidth - dp(16)))
        val targetHeight = dp(desiredHeight)

        if (!animate) {
            updateWindow(targetWidth, targetHeight)
        } else {
            animateSize(targetWidth, targetHeight)
        }
    }

    private fun animateSize(targetWidth: Int, targetHeight: Int) {
        val startWidth = params.width
        val startHeight = params.height
        if (startWidth == targetWidth && startHeight == targetHeight) return
        sizeAnimator?.cancel()
        sizeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = latestSettings.animationDurationMs.toLong()
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                val width = (startWidth + (targetWidth - startWidth) * fraction).toInt()
                val height = (startHeight + (targetHeight - startHeight) * fraction).toInt()
                updateWindow(width, height)
            }
            start()
        }
    }

    private fun updateWindow(width: Int, height: Int) {
        if (!added) return
        params.width = width
        params.height = height
        applyPosition(width)
        runCatching { windowManager.updateViewLayout(view, params) }
            .onFailure { Diagnostics.error("overlay", "Overlay layout update failed", it) }
    }

    private fun applyPosition(width: Int) {
        val geometry = displayGeometry()

        // SX Dynamic deliberately stays top-center in every orientation. The
        // camera is centered on the target Poco in portrait, while landscape
        // cutout coordinates move to a side edge. Anchoring X to a side cutout
        // is what previously made the island jump left/right after rotation.
        params.x = (geometry.screenCenterX - width / 2)
            .coerceIn(0, max(0, geometry.screenWidth - width))

        val collapsedHeight = dp(latestSettings.collapsedHeightDp)
        val baseY = geometry.topCutoutCenterY?.let { cutoutCenterY ->
            cutoutCenterY - collapsedHeight / 2
        } ?: max(0, (geometry.statusBarTop - collapsedHeight) / 2)

        params.y = baseY + dp(latestSettings.verticalOffsetDp)
    }

    private fun displayGeometry(): Geometry {
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            val insets = metrics.windowInsets
            val statusTop = insets
                .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())
                .top
            val screenWidth = bounds.width()
            val screenCenter = screenWidth / 2

            // Only a cutout that actually belongs to the top status-bar zone
            // may influence vertical placement. In landscape the physical
            // camera cutout is normally reported on a side edge and must not
            // drag the island away from the top-center anchor.
            val topZoneLimit = max(statusTop * 2, dp(96))
            val topCutout = insets.displayCutout
                ?.boundingRects
                .orEmpty()
                .filter { rect ->
                    rect.top <= dp(8) && rect.centerY() <= topZoneLimit
                }
                .minByOrNull { rect ->
                    kotlin.math.abs(rect.centerX() - screenCenter)
                }

            return Geometry(
                screenWidth = screenWidth,
                screenCenterX = screenCenter,
                topCutoutCenterY = topCutout?.centerY(),
                statusBarTop = statusTop,
            )
        }

        @Suppress("DEPRECATION")
        val width = resources.displayMetrics.widthPixels
        val statusBarId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val status = if (statusBarId > 0) resources.getDimensionPixelSize(statusBarId) else dp(24)
        return Geometry(
            screenWidth = width,
            screenCenterX = width / 2,
            topCutoutCenterY = null,
            statusBarTop = status,
        )
    }

    private fun scheduleGeometryRefresh() {
        mainHandler.removeCallbacks(relayoutRunnable)
        // HyperOS can dispatch the rotation before WindowMetrics has settled.
        // Waiting a fraction of a second prevents using the old portrait width
        // on the first landscape frame (and vice-versa).
        mainHandler.postDelayed(relayoutRunnable, DISPLAY_SETTLE_DELAY_MS)
    }

    private fun screenWidthPx(): Int = if (Build.VERSION.SDK_INT >= 30) {
        windowManager.currentWindowMetrics.bounds.width()
    } else {
        @Suppress("DEPRECATION")
        resources.displayMetrics.widthPixels
    }

    private fun startAsForeground() {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sx)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.overlay_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.overlay_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class Geometry(
        val screenWidth: Int,
        val screenCenterX: Int,
        val topCutoutCenterY: Int?,
        val statusBarTop: Int,
    )

    private companion object {
        const val CHANNEL_ID = "sx_dynamic_overlay"
        const val NOTIFICATION_ID = 1107
        const val DISPLAY_SETTLE_DELAY_MS = 120L
    }
}
