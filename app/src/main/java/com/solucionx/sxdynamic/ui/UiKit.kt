package com.solucionx.sxdynamic.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object UiKit {
    const val BG = 0xFF07101F.toInt()
    const val SURFACE = 0xFF0E1A2B.toInt()
    const val PRIMARY = 0xFF2E7DD7.toInt()
    const val ACCENT = 0xFF60A5FA.toInt()
    const val TEXT = 0xFFF5F8FC.toInt()
    const val MUTED = 0xFFA7B3C6.toInt()
    const val SUCCESS = 0xFF42D392.toInt()
    const val WARNING = 0xFFFFBE5C.toInt()

    fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    fun panel(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(18), context.dp(16), context.dp(18), context.dp(16))
        background = rounded(SURFACE, context.dp(20).toFloat(), stroke = 0xFF1E3250.toInt())
    }

    fun title(context: Context, text: String, size: Float = 20f): TextView = TextView(context).apply {
        this.text = text
        setTextColor(TEXT)
        textSize = size
        typeface = Typeface.create("sans", Typeface.BOLD)
    }

    fun subtitle(context: Context, text: String, size: Float = 13f): TextView = TextView(context).apply {
        this.text = text
        setTextColor(MUTED)
        textSize = size
        setLineSpacing(0f, 1.08f)
    }

    fun actionButton(context: Context, text: String): Button = Button(context).apply {
        this.text = text
        isAllCaps = false
        textSize = 13f
        setTextColor(Color.WHITE)
        typeface = Typeface.create("sans", Typeface.BOLD)
        background = rounded(PRIMARY, context.dp(14).toFloat())
        minHeight = context.dp(44)
        setPadding(context.dp(16), 0, context.dp(16), 0)
    }

    fun chip(context: Context, text: String, good: Boolean): TextView = TextView(context).apply {
        this.text = text
        setTextColor(if (good) SUCCESS else WARNING)
        textSize = 12f
        gravity = Gravity.CENTER
        setPadding(context.dp(10), context.dp(5), context.dp(10), context.dp(5))
        background = rounded(if (good) 0x1F42D392 else 0x1FFFBE5C, context.dp(20).toFloat())
    }

    fun spacer(context: Context, height: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, context.dp(height))
    }

    fun rounded(color: Int, radius: Float, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            stroke?.let { setStroke(1, it) }
        }
}
