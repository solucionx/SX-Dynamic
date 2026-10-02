package com.solucionx.sxdynamic.service.overlay

import android.app.PendingIntent
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.solucionx.sxdynamic.data.DynamicSettings
import com.solucionx.sxdynamic.domain.IslandContent
import com.solucionx.sxdynamic.domain.OverlayCoordinator
import com.solucionx.sxdynamic.domain.OverlayUiState
import kotlin.math.max

class DynamicIslandView(
    context: Context,
    private val coordinator: OverlayCoordinator,
    private val mediaMonitor: MediaMonitor,
) : View(context) {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(31, 41, 55)
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(64, 160, 255) }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(14f)
        typeface = android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD)
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(190, 198, 210)
        textSize = sp(12f)
    }
    private val controlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(18f)
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD)
    }

    private var state = OverlayUiState()
    private var settings = DynamicSettings()

    init {
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun render(newState: OverlayUiState, newSettings: DynamicSettings) {
        state = newState
        settings = newSettings
        contentDescription = describe(newState.content)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val radius = height / 2f
        canvas.drawRoundRect(rect, radius, radius, backgroundPaint)
        canvas.drawRoundRect(
            RectF(0.5f, 0.5f, width - 0.5f, height - 0.5f),
            radius,
            radius,
            borderPaint,
        )

        if (!state.expanded) drawCollapsed(canvas) else drawExpanded(canvas)
    }

    private fun drawCollapsed(canvas: Canvas) {
        when (val content = state.content) {
            is IslandContent.Media -> {
                accentPaint.color = if (content.playing) Color.rgb(63, 210, 140) else Color.rgb(120, 130, 145)
                canvas.drawCircle(dp(15f), height / 2f, dp(4f), accentPaint)
            }
            is IslandContent.Battery -> {
                accentPaint.color = if (content.charging) Color.rgb(83, 222, 132) else Color.rgb(64, 160, 255)
                canvas.drawCircle(dp(15f), height / 2f, dp(4f), accentPaint)
            }
            is IslandContent.NotificationEvent,
            is IslandContent.Progress,
            is IslandContent.Timer -> {
                accentPaint.color = Color.rgb(64, 160, 255)
                canvas.drawCircle(dp(15f), height / 2f, dp(4f), accentPaint)
            }
            IslandContent.Idle -> Unit
        }
    }

    private fun drawExpanded(canvas: Canvas) {
        val left = dp(18f)
        val titleY = height / 2f - dp(5f)
        val subtitleY = height / 2f + dp(17f)
        val textRight = if (state.content is IslandContent.Media) width - dp(120f) else width - dp(18f)

        when (val content = state.content) {
            IslandContent.Idle ->
                drawTextEllipsized(canvas, "SX Dynamic", left, height / 2f + dp(5f), width - dp(36f), titlePaint)

            is IslandContent.NotificationEvent -> {
                accentPaint.color = if (content.isCall) Color.rgb(75, 215, 130) else Color.rgb(64, 160, 255)
                canvas.drawCircle(dp(12f), titleY - dp(4f), dp(3f), accentPaint)
                drawTextEllipsized(canvas, content.title, left + dp(8f), titleY, textRight - left, titlePaint)
                drawTextEllipsized(
                    canvas,
                    content.text.ifBlank { content.appName },
                    left,
                    subtitleY,
                    textRight - left,
                    subtitlePaint,
                )
            }

            is IslandContent.Media -> {
                accentPaint.color = Color.rgb(168, 102, 255)
                canvas.drawCircle(dp(12f), titleY - dp(4f), dp(3f), accentPaint)
                drawTextEllipsized(canvas, content.title, left + dp(8f), titleY, textRight - left, titlePaint)
                drawTextEllipsized(
                    canvas,
                    content.artist.ifBlank { "Reprodução de mídia" },
                    left,
                    subtitleY,
                    textRight - left,
                    subtitlePaint,
                )
                drawMediaControls(canvas, content)
            }

            is IslandContent.Battery -> {
                accentPaint.color = if (content.charging) Color.rgb(83, 222, 132) else Color.rgb(64, 160, 255)
                canvas.drawCircle(dp(12f), titleY - dp(4f), dp(3f), accentPaint)
                val batteryTitle = if (content.charging) {
                    "Carregando · " + content.level + "%"
                } else {
                    "Bateria · " + content.level + "%"
                }
                drawTextEllipsized(canvas, batteryTitle, left + dp(8f), titleY, textRight - left, titlePaint)
                val subtitle = if (content.plugged) "Fonte de energia conectada" else "Fonte de energia desconectada"
                drawTextEllipsized(canvas, subtitle, left, subtitleY, textRight - left, subtitlePaint)
            }

            is IslandContent.Progress -> {
                drawTextEllipsized(canvas, content.title, left, titleY, textRight - left, titlePaint)
                val percent = if (content.max > 0) {
                    ((content.progress * 100f) / content.max).toInt().coerceIn(0, 100)
                } else {
                    -1
                }
                drawTextEllipsized(
                    canvas,
                    if (percent >= 0) percent.toString() + "%" else "Em andamento",
                    left,
                    subtitleY,
                    textRight - left,
                    subtitlePaint,
                )
                if (percent >= 0) drawProgress(canvas, percent / 100f)
            }

            is IslandContent.Timer -> {
                accentPaint.color = Color.rgb(255, 180, 64)
                canvas.drawCircle(dp(12f), titleY - dp(4f), dp(3f), accentPaint)
                drawTextEllipsized(canvas, content.title, left + dp(8f), titleY, textRight - left, titlePaint)
                drawTextEllipsized(
                    canvas,
                    content.text.ifBlank { "Temporizador ativo" },
                    left,
                    subtitleY,
                    textRight - left,
                    subtitlePaint,
                )
            }
        }
    }

    private fun drawMediaControls(canvas: Canvas, content: IslandContent.Media) {
        val centerY = height / 2f + dp(4f)
        val start = width - dp(108f)
        val gap = dp(34f)
        canvas.drawText("‹", start, centerY, controlPaint)
        canvas.drawText(if (content.playing) "Ⅱ" else "▶", start + gap, centerY, controlPaint)
        canvas.drawText("›", start + gap * 2, centerY, controlPaint)
    }

    private fun drawProgress(canvas: Canvas, fraction: Float) {
        val left = dp(18f)
        val right = width - dp(18f)
        val y = height - dp(10f)
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(45, 50, 60) }
        canvas.drawRoundRect(left, y, right, y + dp(3f), dp(2f), dp(2f), track)
        canvas.drawRoundRect(
            left,
            y,
            left + (right - left) * fraction.coerceIn(0f, 1f),
            y + dp(3f),
            dp(2f),
            dp(2f),
            accentPaint,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        if (settings.haptics) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

        val content = state.content
        if (!state.expanded) {
            coordinator.toggleExpanded()
            return true
        }

        if (content is IslandContent.Media && event.x > width - dp(125f)) {
            val localX = event.x - (width - dp(125f))
            when {
                localX < dp(42f) -> mediaMonitor.previous()
                localX < dp(84f) -> mediaMonitor.playPause()
                else -> mediaMonitor.next()
            }
            return true
        }

        val action = when (content) {
            is IslandContent.NotificationEvent -> content.action
            is IslandContent.Progress -> content.action
            is IslandContent.Timer -> content.action
            else -> null
        }
        if (action != null) send(action) else coordinator.collapse()
        return true
    }

    private fun send(action: PendingIntent) {
        runCatching { action.send() }
        coordinator.collapse()
    }

    private fun drawTextEllipsized(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        paint: Paint,
    ) {
        val safe = text.replace('\n', ' ').trim()
        val output = if (paint.measureText(safe) <= maxWidth) {
            safe
        } else {
            val ellipsis = "…"
            val available = max(0f, maxWidth - paint.measureText(ellipsis))
            var end = paint.breakText(safe, true, available, null).coerceIn(0, safe.length)
            while (end > 0 && Character.isHighSurrogate(safe[end - 1])) end--
            safe.take(end).trimEnd() + ellipsis
        }
        canvas.drawText(output, x, y, paint)
    }

    private fun describe(content: IslandContent): String = when (content) {
        IslandContent.Idle -> "SX Dynamic"
        is IslandContent.NotificationEvent -> content.appName + ": " + content.title + " " + content.text
        is IslandContent.Media -> "Mídia: " + content.title + ", " + content.artist
        is IslandContent.Battery -> "Bateria " + content.level + " por cento"
        is IslandContent.Progress -> content.appName + ": " + content.title
        is IslandContent.Timer -> content.appName + ": " + content.title + " " + content.text
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
