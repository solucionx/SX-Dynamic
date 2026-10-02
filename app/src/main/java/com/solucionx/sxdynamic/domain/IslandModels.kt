package com.solucionx.sxdynamic.domain

import android.app.PendingIntent

sealed interface IslandContent {
    data object Idle : IslandContent

    data class NotificationEvent(
        val key: String,
        val packageName: String,
        val appName: String,
        val title: String,
        val text: String,
        val action: PendingIntent?,
        val isCall: Boolean = false,
    ) : IslandContent

    data class Media(
        val packageName: String,
        val title: String,
        val artist: String,
        val playing: Boolean,
    ) : IslandContent

    data class Battery(
        val level: Int,
        val charging: Boolean,
        val plugged: Boolean,
    ) : IslandContent

    data class Progress(
        val key: String,
        val packageName: String,
        val appName: String,
        val title: String,
        val progress: Int,
        val max: Int,
        val action: PendingIntent?,
    ) : IslandContent

    data class Timer(
        val key: String,
        val packageName: String,
        val appName: String,
        val title: String,
        val text: String,
        val action: PendingIntent?,
    ) : IslandContent
}

data class OverlayUiState(
    val content: IslandContent = IslandContent.Idle,
    val expanded: Boolean = false,
    val revision: Long = 0L,
)
