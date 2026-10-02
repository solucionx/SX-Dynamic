package com.solucionx.sxdynamic.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object SystemSettingsNavigator {
    fun overlay(context: Context) = launch(
        context,
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName)),
    )

    fun notificationListener(context: Context) = launch(
        context,
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
    )

    fun batteryOptimization(context: Context) = launch(
        context,
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )

    fun appDetails(context: Context) = launch(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)),
    )

    fun hyperOsAutoStart(context: Context) = appDetails(context)

    private fun launch(context: Context, intent: Intent): Boolean = runCatching {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) {
            false
        } else {
            context.startActivity(intent)
            true
        }
    }.getOrDefault(false)
}
