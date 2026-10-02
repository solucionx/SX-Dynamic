package com.solucionx.sxdynamic.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

class IslandPreviewView(context: Context) : View(context) {
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = UiKit.ACCENT }
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14f * resources.displayMetrics.scaledDensity
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UiKit.MUTED
        textSize = 11f * resources.displayMetrics.scaledDensity
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            (92 * resources.displayMetrics.density).toInt(),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val pillWidth = minOf(width - (24 * density).toInt(), (338 * density).toInt())
        val pillHeight = (76 * density).toInt()
        val left = (width - pillWidth) / 2f
        val top = (height - pillHeight) / 2f
        val rect = RectF(left, top, left + pillWidth, top + pillHeight)
        canvas.drawRoundRect(rect, pillHeight / 2f, pillHeight / 2f, bg)
        canvas.drawCircle(left + 18 * density, top + 26 * density, 4 * density, accent)
        canvas.drawText("SX Dynamic", left + 30 * density, top + 30 * density, title)
        canvas.drawText("Prévia da ilha dinâmica", left + 18 * density, top + 52 * density, sub)
    }
}
