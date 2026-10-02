package com.solucionx.sxdynamic.service.overlay

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.solucionx.sxdynamic.data.DynamicSettings
import com.solucionx.sxdynamic.domain.IslandContent
import com.solucionx.sxdynamic.domain.OverlayCoordinator
import com.solucionx.sxdynamic.domain.OverlayUiState
import kotlin.math.max
import kotlin.math.sin

class DynamicIslandView(
    context: Context,
    private val coordinator: OverlayCoordinator,
    private val mediaMonitor: MediaMonitor,
) : View(context) {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(14f)
        typeface = android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD)
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(188, 194, 204)
        textSize = sp(12f)
    }
    private val appLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(142, 147, 156)
        textSize = sp(9.5f)
        typeface = android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD)
    }
    private val controlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(17f)
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD)
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(72, 215, 117)
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(2.2f)
    }
    private val iconCache = object : LruCache<String, Bitmap>(24) {}

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

        if (state.expanded) drawExpanded(canvas) else drawCollapsed(canvas)

        val media = state.content as? IslandContent.Media
        if (media?.playing == true) postInvalidateDelayed(180L)
    }

    private fun drawCollapsed(canvas: Canvas) {
        when (val content = state.content) {
            IslandContent.Idle -> Unit

            is IslandContent.Media -> {
                val iconSize = dp(22f)
                val left = dp(5f)
                val top = (height - iconSize) / 2f
                val art = content.artwork ?: loadAppIcon(content.packageName)
                if (art != null) {
                    drawBitmapRounded(
                        canvas,
                        art,
                        RectF(left, top, left + iconSize, top + iconSize),
                        dp(6f),
                    )
                }
                drawEqualizer(canvas, width - dp(19f), height / 2f, content.playing, compact = true)
            }

            is IslandContent.NotificationEvent -> {
                val iconSize = dp(22f)
                val left = dp(5f)
                val top = (height - iconSize) / 2f
                loadAppIcon(content.packageName)?.let {
                    drawBitmapRounded(
                        canvas,
                        it,
                        RectF(left, top, left + iconSize, top + iconSize),
                        dp(6f),
                    )
                }
            }

            is IslandContent.Battery -> {
                accentPaint.color = if (content.charging) Color.rgb(73, 222, 132) else Color.rgb(74, 156, 255)
                canvas.drawCircle(width - dp(14f), height / 2f, dp(3.2f), accentPaint)
            }

            is IslandContent.Progress -> {
                loadAppIcon(content.packageName)?.let {
                    val iconSize = dp(22f)
                    val left = dp(5f)
                    val top = (height - iconSize) / 2f
                    drawBitmapRounded(
                        canvas,
                        it,
                        RectF(left, top, left + iconSize, top + iconSize),
                        dp(6f),
                    )
                }
                val fraction = if (content.max > 0) {
                    content.progress.toFloat() / content.max.toFloat()
                } else {
                    0f
                }
                drawCircularProgress(canvas, width - dp(14f), height / 2f, fraction)
            }

            is IslandContent.Timer -> {
                loadAppIcon(content.packageName)?.let {
                    val iconSize = dp(22f)
                    val left = dp(5f)
                    val top = (height - iconSize) / 2f
                    drawBitmapRounded(
                        canvas,
                        it,
                        RectF(left, top, left + iconSize, top + iconSize),
                        dp(6f),
                    )
                }
            }
        }
    }

    private fun drawExpanded(canvas: Canvas) {
        when (val content = state.content) {
            IslandContent.Idle -> Unit
            is IslandContent.NotificationEvent -> drawNotification(canvas, content)
            is IslandContent.Media -> drawMedia(canvas, content)
            is IslandContent.Battery -> drawBattery(canvas, content)
            is IslandContent.Progress -> drawProgressEvent(canvas, content)
            is IslandContent.Timer -> drawTimer(canvas, content)
        }
    }

    private fun drawNotification(canvas: Canvas, content: IslandContent.NotificationEvent) {
        val iconSize = dp(38f)
        val iconLeft = dp(12f)
        val iconTop = (height - iconSize) / 2f
        loadAppIcon(content.packageName)?.let {
            drawBitmapRounded(
                canvas,
                it,
                RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize),
                dp(9f),
            )
        }

        val textLeft = iconLeft + iconSize + dp(10f)
        val right = width - dp(16f)
        val labelY = height / 2f - dp(20f)
        val titleY = height / 2f - dp(3f)
        val messageY = height / 2f + dp(18f)

        drawTextEllipsized(canvas, content.appName, textLeft, labelY, right - textLeft, appLabelPaint)
        drawTextEllipsized(
            canvas,
            content.title.ifBlank { content.appName },
            textLeft,
            titleY,
            right - textLeft,
            titlePaint,
        )
        drawTextEllipsized(canvas, content.text, textLeft, messageY, right - textLeft, subtitlePaint)
    }

    private fun drawMedia(canvas: Canvas, content: IslandContent.Media) {
        val artSize = dp(50f)
        val artLeft = dp(12f)
        val artTop = (height - artSize) / 2f
        val artwork = content.artwork ?: loadAppIcon(content.packageName)

        artwork?.let {
            drawBitmapRounded(
                canvas,
                it,
                RectF(artLeft, artTop, artLeft + artSize, artTop + artSize),
                dp(12f),
            )
        }

        val controlsWidth = dp(105f)
        val textLeft = artLeft + artSize + dp(10f)
        val textRight = width - controlsWidth - dp(6f)
        val titleY = height / 2f - dp(7f)
        val artistY = height / 2f + dp(15f)

        drawTextEllipsized(
            canvas,
            content.title,
            textLeft,
            titleY,
            max(0f, textRight - textLeft),
            titlePaint,
        )
        drawTextEllipsized(
            canvas,
            content.artist.ifBlank { appNameForPackage(content.packageName) },
            textLeft,
            artistY,
            max(0f, textRight - textLeft),
            subtitlePaint,
        )

        drawMediaControls(canvas, content)
    }

    private fun drawBattery(canvas: Canvas, content: IslandContent.Battery) {
        accentPaint.color = if (content.charging) Color.rgb(73, 222, 132) else Color.rgb(74, 156, 255)
        canvas.drawCircle(dp(22f), height / 2f, dp(5f), accentPaint)
        val left = dp(38f)
        val batteryTitle = if (content.charging) {
            "Carregando · " + content.level + "%"
        } else {
            "Bateria · " + content.level + "%"
        }
        drawTextEllipsized(
            canvas,
            batteryTitle,
            left,
            height / 2f - dp(3f),
            width - left - dp(16f),
            titlePaint,
        )
        drawTextEllipsized(
            canvas,
            if (content.plugged) "Fonte de energia conectada" else "Fonte de energia desconectada",
            left,
            height / 2f + dp(18f),
            width - left - dp(16f),
            subtitlePaint,
        )
    }

    private fun drawProgressEvent(canvas: Canvas, content: IslandContent.Progress) {
        val iconSize = dp(38f)
        val iconLeft = dp(12f)
        val iconTop = (height - iconSize) / 2f
        loadAppIcon(content.packageName)?.let {
            drawBitmapRounded(
                canvas,
                it,
                RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize),
                dp(9f),
            )
        }

        val left = iconLeft + iconSize + dp(10f)
        val right = width - dp(16f)
        drawTextEllipsized(
            canvas,
            content.title,
            left,
            height / 2f - dp(7f),
            right - left,
            titlePaint,
        )
        val percent = if (content.max > 0) {
            ((content.progress * 100f) / content.max).toInt().coerceIn(0, 100)
        } else {
            -1
        }
        drawTextEllipsized(
            canvas,
            if (percent >= 0) percent.toString() + "%" else "Em andamento",
            left,
            height / 2f + dp(14f),
            right - left,
            subtitlePaint,
        )
        if (percent >= 0) drawLinearProgress(canvas, percent / 100f)
    }

    private fun drawTimer(canvas: Canvas, content: IslandContent.Timer) {
        val iconSize = dp(38f)
        val iconLeft = dp(12f)
        val iconTop = (height - iconSize) / 2f
        loadAppIcon(content.packageName)?.let {
            drawBitmapRounded(
                canvas,
                it,
                RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize),
                dp(9f),
            )
        }
        val left = iconLeft + iconSize + dp(10f)
        val right = width - dp(16f)
        drawTextEllipsized(
            canvas,
            content.title,
            left,
            height / 2f - dp(5f),
            right - left,
            titlePaint,
        )
        drawTextEllipsized(
            canvas,
            content.text,
            left,
            height / 2f + dp(17f),
            right - left,
            subtitlePaint,
        )
    }

    private fun drawMediaControls(canvas: Canvas, content: IslandContent.Media) {
        val centerY = height / 2f + dp(4f)
        val start = width - dp(92f)
        val gap = dp(31f)
        canvas.drawText("‹", start, centerY, controlPaint)
        canvas.drawText(if (content.playing) "Ⅱ" else "▶", start + gap, centerY, controlPaint)
        canvas.drawText("›", start + gap * 2, centerY, controlPaint)
    }

    private fun drawEqualizer(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        playing: Boolean,
        compact: Boolean,
    ) {
        val now = System.currentTimeMillis() / 180.0
        val gap = if (compact) dp(3f) else dp(4f)
        val maxHeight = if (compact) dp(13f) else dp(20f)
        val minHeight = dp(3f)
        accentPaint.color = Color.rgb(73, 222, 132)

        for (i in 0 until 4) {
            val phase = now + i * 0.9
            val amplitude = if (playing) {
                ((sin(phase) + 1.0) / 2.0).toFloat()
            } else {
                0.15f + i * 0.05f
            }
            val barHeight = minHeight + (maxHeight - minHeight) * amplitude
            val x = centerX + (i - 1.5f) * gap
            canvas.drawLine(x, centerY - barHeight / 2f, x, centerY + barHeight / 2f, accentPaint)
        }
    }

    private fun drawCircularProgress(canvas: Canvas, centerX: Float, centerY: Float, fraction: Float) {
        val radius = dp(6f)
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(54, 58, 67)
            style = Paint.Style.STROKE
            strokeWidth = dp(2f)
        }
        val progressPaint = Paint(track).apply { color = Color.rgb(72, 156, 255) }
        canvas.drawCircle(centerX, centerY, radius, track)
        canvas.drawArc(
            RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius),
            -90f,
            360f * fraction.coerceIn(0f, 1f),
            false,
            progressPaint,
        )
    }

    private fun drawLinearProgress(canvas: Canvas, fraction: Float) {
        val left = dp(70f)
        val right = width - dp(18f)
        val y = height - dp(10f)
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(45, 50, 60) }
        val progress = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(72, 156, 255) }
        canvas.drawRoundRect(left, y, right, y + dp(3f), dp(2f), dp(2f), track)
        canvas.drawRoundRect(
            left,
            y,
            left + (right - left) * fraction.coerceIn(0f, 1f),
            y + dp(3f),
            dp(2f),
            dp(2f),
            progress,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        if (settings.haptics) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

        val content = state.content
        if (!state.expanded) {
            if (content != IslandContent.Idle) coordinator.toggleExpanded()
            return true
        }

        if (content is IslandContent.Media && event.x > width - dp(120f)) {
            val localX = event.x - (width - dp(120f))
            when {
                localX < dp(42f) -> mediaMonitor.previous()
                localX < dp(82f) -> mediaMonitor.playPause()
                else -> mediaMonitor.next()
            }
            return true
        }

        val action = when (content) {
            is IslandContent.NotificationEvent -> content.action
            is IslandContent.Progress -> content.action
            is IslandContent.Timer -> content.action
            is IslandContent.Media -> content.action
            else -> null
        }

        if (action != null) send(action) else coordinator.collapse()
        return true
    }

    private fun send(action: PendingIntent) {
        runCatching { action.send() }
        coordinator.collapse()
    }

    private fun loadAppIcon(packageName: String): Bitmap? {
        iconCache.get(packageName)?.let { return it }
        return runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val size = dp(48f).toInt().coerceAtLeast(1)
            val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                Bitmap.createScaledBitmap(drawable.bitmap, size, size, true)
            } else {
                Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { target ->
                    val iconCanvas = Canvas(target)
                    drawable.setBounds(0, 0, size, size)
                    drawable.draw(iconCanvas)
                }
            }
            iconCache.put(packageName, bitmap)
            bitmap
        }.getOrNull()
    }

    private fun appNameForPackage(packageName: String): String = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault("Mídia")

    private fun drawBitmapRounded(
        canvas: Canvas,
        bitmap: Bitmap,
        destination: RectF,
        radius: Float,
    ) {
        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = max(
            destination.width() / bitmap.width.toFloat(),
            destination.height() / bitmap.height.toFloat(),
        )
        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate(
            destination.centerX() - bitmap.width * scale / 2f,
            destination.centerY() - bitmap.height * scale / 2f,
        )
        shader.setLocalMatrix(matrix)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        canvas.drawRoundRect(destination, radius, radius, paint)
    }

    private fun drawTextEllipsized(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        paint: Paint,
    ) {
        if (maxWidth <= 0f) return
        val safe = text.replace('\n', ' ').trim()
        if (safe.isBlank()) return

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
        IslandContent.Idle -> "Ilha dinâmica"
        is IslandContent.NotificationEvent -> content.appName + ": " + content.title + " " + content.text
        is IslandContent.Media -> content.title + ", " + content.artist
        is IslandContent.Battery -> "Bateria " + content.level + " por cento"
        is IslandContent.Progress -> content.appName + ": " + content.title
        is IslandContent.Timer -> content.appName + ": " + content.title + " " + content.text
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
