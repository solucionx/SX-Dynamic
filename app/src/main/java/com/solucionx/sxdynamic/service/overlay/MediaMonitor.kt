package com.solucionx.sxdynamic.service.overlay

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import com.solucionx.sxdynamic.core.Diagnostics
import com.solucionx.sxdynamic.domain.IslandContent
import com.solucionx.sxdynamic.domain.OverlayCoordinator
import com.solucionx.sxdynamic.service.notification.SxNotificationListenerService

class MediaMonitor(
    context: Context,
    private val coordinator: OverlayCoordinator,
) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, SxNotificationListenerService::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var current: MediaController? = null
    private var started = false

    private val activeSessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { refresh(it.orEmpty()) }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publishCurrent()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publishCurrent()
        override fun onSessionDestroyed() = refreshSafely()
    }

    fun start() {
        if (started) return
        started = true
        runCatching {
            manager.addOnActiveSessionsChangedListener(activeSessionsListener, listenerComponent, handler)
            refresh(manager.getActiveSessions(listenerComponent))
        }.onFailure {
            Diagnostics.warn("media", "Media session access unavailable until notification access is granted")
        }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { manager.removeOnActiveSessionsChangedListener(activeSessionsListener) }
        current?.unregisterCallback(callback)
        current = null
        coordinator.updateMedia(null)
    }

    fun previous() = runCatching { current?.transportControls?.skipToPrevious() }
    fun next() = runCatching { current?.transportControls?.skipToNext() }

    fun playPause() = runCatching {
        val controller = current ?: return@runCatching
        val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        if (playing) controller.transportControls.pause() else controller.transportControls.play()
    }

    private fun refreshSafely() {
        runCatching { refresh(manager.getActiveSessions(listenerComponent)) }
    }

    private fun refresh(controllers: List<MediaController>) {
        val selected = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.firstOrNull()

        if (current?.sessionToken == selected?.sessionToken) {
            publishCurrent()
            return
        }
        current?.unregisterCallback(callback)
        current = selected
        current?.registerCallback(callback, handler)
        publishCurrent()
    }

    private fun publishCurrent() {
        val controller = current
        if (controller == null) {
            coordinator.updateMedia(null)
            return
        }

        val metadata = controller.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            .ifBlank { "Mídia" }
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
            .ifBlank { metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty() }
        val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        val artwork = extractArtwork(metadata)

        coordinator.updateMedia(
            IslandContent.Media(
                packageName = controller.packageName,
                title = title,
                artist = artist,
                playing = playing,
                artwork = artwork,
            ),
        )
    }

    private fun extractArtwork(metadata: MediaMetadata?): Bitmap? {
        metadata ?: return null
        return metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
    }
}
