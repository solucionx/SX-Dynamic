package com.solucionx.sxdynamic.data

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.CopyOnWriteArraySet

data class DynamicSettings(
    val enabled: Boolean = false,
    val showNotifications: Boolean = true,
    val showMedia: Boolean = true,
    val showBattery: Boolean = true,
    val showProgress: Boolean = true,
    val showTimers: Boolean = true,
    val collapsedWidthDp: Int = 118,
    val collapsedHeightDp: Int = 34,
    val expandedWidthDp: Int = 338,
    val expandedHeightDp: Int = 92,
    val verticalOffsetDp: Int = 0,
    val animationDurationMs: Int = 260,
    val notificationDurationSeconds: Int = 5,
    val haptics: Boolean = true,
    val privacyMode: Boolean = false,
)

class SettingsRepository(context: Context) : SharedPreferences.OnSharedPreferenceChangeListener {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val listeners = CopyOnWriteArraySet<(DynamicSettings) -> Unit>()

    init {
        preferences.registerOnSharedPreferenceChangeListener(this)
    }

    fun read(): DynamicSettings = DynamicSettings(
        enabled = preferences.getBoolean(KEY_ENABLED, false),
        showNotifications = preferences.getBoolean(KEY_NOTIFICATIONS, true),
        showMedia = preferences.getBoolean(KEY_MEDIA, true),
        showBattery = preferences.getBoolean(KEY_BATTERY, true),
        showProgress = preferences.getBoolean(KEY_PROGRESS, true),
        showTimers = preferences.getBoolean(KEY_TIMERS, true),
        collapsedWidthDp = preferences.getInt(KEY_COLLAPSED_WIDTH, 118).coerceIn(72, 240),
        collapsedHeightDp = preferences.getInt(KEY_COLLAPSED_HEIGHT, 34).coerceIn(28, 72),
        expandedWidthDp = preferences.getInt(KEY_EXPANDED_WIDTH, 338).coerceIn(220, 480),
        expandedHeightDp = preferences.getInt(KEY_EXPANDED_HEIGHT, 92).coerceIn(64, 180),
        verticalOffsetDp = preferences.getInt(KEY_VERTICAL_OFFSET, 0).coerceIn(-80, 80),
        animationDurationMs = preferences.getInt(KEY_ANIMATION_DURATION, 260).coerceIn(100, 700),
        notificationDurationSeconds = preferences.getInt(KEY_NOTIFICATION_DURATION, 5).coerceIn(2, 15),
        haptics = preferences.getBoolean(KEY_HAPTICS, true),
        privacyMode = preferences.getBoolean(KEY_PRIVACY, false),
    )

    fun setEnabled(value: Boolean) = putBoolean(KEY_ENABLED, value)
    fun setShowNotifications(value: Boolean) = putBoolean(KEY_NOTIFICATIONS, value)
    fun setShowMedia(value: Boolean) = putBoolean(KEY_MEDIA, value)
    fun setShowBattery(value: Boolean) = putBoolean(KEY_BATTERY, value)
    fun setShowProgress(value: Boolean) = putBoolean(KEY_PROGRESS, value)
    fun setShowTimers(value: Boolean) = putBoolean(KEY_TIMERS, value)
    fun setHaptics(value: Boolean) = putBoolean(KEY_HAPTICS, value)
    fun setPrivacyMode(value: Boolean) = putBoolean(KEY_PRIVACY, value)
    fun setCollapsedWidth(value: Int) = putInt(KEY_COLLAPSED_WIDTH, value.coerceIn(72, 240))
    fun setCollapsedHeight(value: Int) = putInt(KEY_COLLAPSED_HEIGHT, value.coerceIn(28, 72))
    fun setExpandedWidth(value: Int) = putInt(KEY_EXPANDED_WIDTH, value.coerceIn(220, 480))
    fun setExpandedHeight(value: Int) = putInt(KEY_EXPANDED_HEIGHT, value.coerceIn(64, 180))
    fun setVerticalOffset(value: Int) = putInt(KEY_VERTICAL_OFFSET, value.coerceIn(-80, 80))
    fun setAnimationDuration(value: Int) = putInt(KEY_ANIMATION_DURATION, value.coerceIn(100, 700))
    fun setNotificationDuration(value: Int) = putInt(KEY_NOTIFICATION_DURATION, value.coerceIn(2, 15))

    fun addListener(listener: (DynamicSettings) -> Unit) {
        listeners += listener
        listener(read())
    }

    fun removeListener(listener: (DynamicSettings) -> Unit) {
        listeners -= listener
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        val snapshot = read()
        listeners.forEach { listener -> runCatching { listener(snapshot) } }
    }

    private fun putBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }

    private fun putInt(key: String, value: Int) {
        preferences.edit().putInt(key, value).apply()
    }

    private companion object {
        const val FILE_NAME = "sx_dynamic_settings"
        const val KEY_ENABLED = "enabled"
        const val KEY_NOTIFICATIONS = "show_notifications"
        const val KEY_MEDIA = "show_media"
        const val KEY_BATTERY = "show_battery"
        const val KEY_PROGRESS = "show_progress"
        const val KEY_TIMERS = "show_timers"
        const val KEY_COLLAPSED_WIDTH = "collapsed_width"
        const val KEY_COLLAPSED_HEIGHT = "collapsed_height"
        const val KEY_EXPANDED_WIDTH = "expanded_width"
        const val KEY_EXPANDED_HEIGHT = "expanded_height"
        const val KEY_VERTICAL_OFFSET = "vertical_offset"
        const val KEY_ANIMATION_DURATION = "animation_duration"
        const val KEY_NOTIFICATION_DURATION = "notification_duration"
        const val KEY_HAPTICS = "haptics"
        const val KEY_PRIVACY = "privacy"
    }
}
