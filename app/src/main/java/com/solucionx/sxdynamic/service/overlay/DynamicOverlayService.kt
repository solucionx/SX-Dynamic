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
import android.view.Surface
import android.view.WindowInsets
import android.view.WindowManager
import com.solucionx.sxdynamic.MainActivity
import com.solucionx.sxdynamic.R
import com.solucionx.sxdynamic.core.Diagnostics
import com.solucionx.sxdynamic.core.appContainer
import com.solucionx.sxdynamic.data.DynamicSettings
import com.solucionx.sxdynamic.domain.OverlayUiState
import kotlin.math.abs
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

    // Distance from the physical camera centre to the nearest physical edge.
    // HyperOS may temporarily stop exposing DisplayCutout while rotating, so
    // remembering this lets us reconstruct the correct camera side instead of
    // falling back to the top-centre of the *content*.
    private var rememberedCameraEdgeInsetPx: Int? = null

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
        applyPosition(width, height)
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
        applyPosition(width, height)
        runCatching { windowManager.updateViewLayout(view, params) }
            .onFailure { Diagnostics.error("overlay", "Overlay layout update failed", it) }
    }

    private fun applyPosition(width: Int, height: Int) {
        val geometry = displayGeometry()
        val anchor = geometry.cameraAnchor ?: syntheticCameraAnchor(geometry)
        val collapsedHeight = dp(latestSettings.collapsedHeightDp)

        // The user's portrait "vertical offset" is treated as an offset normal
        // to the physical edge that contains the camera. This is important:
        // after rotation the same calibration must rotate with the hardware,
        // not keep moving in the screen's Y axis.
        val correction = dp(latestSettings.verticalOffsetDp)

        when (anchor.edge) {
            CameraEdge.TOP -> {
                params.x = (anchor.centerX - width / 2)
                    .coerceIn(0, max(0, geometry.screenWidth - width))
                params.y = anchor.centerY - collapsedHeight / 2 + correction
            }

            CameraEdge.LEFT -> {
                // Keep the camera inside the left rounded cap; the island grows
                // inward into usable content instead of being centred offscreen.
                params.x = anchor.centerX - collapsedHeight / 2 + correction
                params.y = (anchor.centerY - height / 2)
                    .coerceIn(0, max(0, geometry.screenHeight - height))
            }

            CameraEdge.RIGHT -> {
                // Mirror the left-edge behaviour so the camera remains inside
                // the right rounded cap while the island expands inward.
                params.x = anchor.centerX - width + collapsedHeight / 2 - correction
                params.y = (anchor.centerY - height / 2)
                    .coerceIn(0, max(0, geometry.screenHeight - height))
            }

            CameraEdge.BOTTOM -> {
                params.x = (anchor.centerX - width / 2)
                    .coerceIn(0, max(0, geometry.screenWidth - width))
                params.y = anchor.centerY - height + collapsedHeight / 2 - correction
            }
        }
    }

    private fun displayGeometry(): Geometry {
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            val insets = metrics.windowInsets
            val screenWidth = bounds.width()
            val screenHeight = bounds.height()
            val statusTop = insets
                .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())
                .top

            val cutoutRects = insets.displayCutout
                ?.boundingRects
                .orEmpty()
                .filter { it.width() > 0 && it.height() > 0 }

            // A real camera/punch-hole is normally the smallest cutout region.
            // Using the smallest region also avoids treating waterfall/corner
            // decorations as the camera on devices that report more than one.
            val cameraRect = cutoutRects.minByOrNull { rect ->
                rect.width().toLong() * rect.height().toLong()
            }

            val cameraAnchor = cameraRect?.let { rect ->
                val edge = nearestEdge(
                    centerX = rect.centerX(),
                    centerY = rect.centerY(),
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                )
                val edgeInset = when (edge) {
                    CameraEdge.TOP -> rect.centerY()
                    CameraEdge.LEFT -> rect.centerX()
                    CameraEdge.RIGHT -> screenWidth - rect.centerX()
                    CameraEdge.BOTTOM -> screenHeight - rect.centerY()
                }.coerceAtLeast(0)

                if (edgeInset > 0) rememberedCameraEdgeInsetPx = edgeInset

                CameraAnchor(
                    centerX = rect.centerX(),
                    centerY = rect.centerY(),
                    edge = edge,
                )
            }

            return Geometry(
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                statusBarTop = statusTop,
                rotation = currentRotation(),
                cameraAnchor = cameraAnchor,
            )
        }

        @Suppress("DEPRECATION")
        val width = resources.displayMetrics.widthPixels
        @Suppress("DEPRECATION")
        val height = resources.displayMetrics.heightPixels
        val statusBarId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val status = if (statusBarId > 0) resources.getDimensionPixelSize(statusBarId) else dp(24)

        return Geometry(
            screenWidth = width,
            screenHeight = height,
            statusBarTop = status,
            rotation = currentRotation(),
            cameraAnchor = null,
        )
    }

    private fun syntheticCameraAnchor(geometry: Geometry): CameraAnchor {
        val inset = rememberedCameraEdgeInsetPx
            ?: max(dp(14), geometry.statusBarTop / 2)

        return when (geometry.rotation) {
            Surface.ROTATION_90 -> CameraAnchor(
                centerX = geometry.screenWidth - inset,
                centerY = geometry.screenHeight / 2,
                edge = CameraEdge.RIGHT,
            )

            Surface.ROTATION_180 -> CameraAnchor(
                centerX = geometry.screenWidth / 2,
                centerY = geometry.screenHeight - inset,
                edge = CameraEdge.BOTTOM,
            )

            Surface.ROTATION_270 -> CameraAnchor(
                centerX = inset,
                centerY = geometry.screenHeight / 2,
                edge = CameraEdge.LEFT,
            )

            else -> CameraAnchor(
                centerX = geometry.screenWidth / 2,
                centerY = inset,
                edge = CameraEdge.TOP,
            )
        }
    }

    private fun nearestEdge(
        centerX: Int,
        centerY: Int,
        screenWidth: Int,
        screenHeight: Int,
    ): CameraEdge {
        val top = centerY
        val left = centerX
        val right = screenWidth - centerX
        val bottom = screenHeight - centerY
        val minimum = minOf(top, left, right, bottom)

        return when (minimum) {
            left -> CameraEdge.LEFT
            right -> CameraEdge.RIGHT
            bottom -> CameraEdge.BOTTOM
            else -> CameraEdge.TOP
        }
    }

    private fun currentRotation(): Int {
        if (Build.VERSION.SDK_INT >= 30) {
            return runCatching { display?.rotation }.getOrNull() ?: Surface.ROTATION_0
        }

        @Suppress("DEPRECATION")
        return windowManager.defaultDisplay.rotation
    }

    private fun scheduleGeometryRefresh() {
        mainHandler.removeCallbacks(relayoutRunnable)

        // HyperOS dispatches configuration/display changes before WindowMetrics
        // always settles. Two passes make the correction deterministic without
        // polling continuously or wasting battery.
        mainHandler.postDelayed(relayoutRunnable, DISPLAY_SETTLE_DELAY_MS)
        mainHandler.postDelayed(relayoutRunnable, DISPLAY_CONFIRM_DELAY_MS)
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

    private enum class CameraEdge {
        TOP,
        LEFT,
        RIGHT,
        BOTTOM,
    }

    private data class CameraAnchor(
        val centerX: Int,
        val centerY: Int,
        val edge: CameraEdge,
    )

    private data class Geometry(
        val screenWidth: Int,
        val screenHeight: Int,
        val statusBarTop: Int,
        val rotation: Int,
        val cameraAnchor: CameraAnchor?,
    )

    private companion object {
        const val CHANNEL_ID = "sx_dynamic_overlay"
        const val NOTIFICATION_ID = 1107
        const val DISPLAY_SETTLE_DELAY_MS = 120L
        const val DISPLAY_CONFIRM_DELAY_MS = 360L
    }
}
