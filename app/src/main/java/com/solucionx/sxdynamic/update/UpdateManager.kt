package com.solucionx.sxdynamic.update

import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.solucionx.sxdynamic.MainActivity
import com.solucionx.sxdynamic.R
import com.solucionx.sxdynamic.core.Diagnostics
import java.util.concurrent.Executors

enum class UpdateDownloadState {
    NONE,
    PENDING,
    RUNNING,
    PAUSED,
    SUCCESSFUL,
    FAILED,
}

sealed interface InstallUpdateResult {
    data object Started : InstallUpdateResult
    data object PermissionRequired : InstallUpdateResult
    data object MissingDownload : InstallUpdateResult
    data class Failed(val message: String) : InstallUpdateResult
}

object UpdateManager {
    private const val PREFS = "sx_dynamic_updates"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_RELEASE_VERSION = "release_version"
    private const val KEY_RELEASE_TAG = "release_tag"
    private const val KEY_RELEASE_NAME = "release_name"
    private const val KEY_RELEASE_NOTES = "release_notes"
    private const val KEY_RELEASE_URL = "release_url"
    private const val KEY_RELEASE_FILE = "release_file"
    private const val KEY_RELEASE_SIZE = "release_size"
    private const val KEY_RELEASE_PUBLISHED = "release_published"
    private const val KEY_DOWNLOAD_ID = "download_id"
    private const val KEY_DOWNLOAD_VERSION = "download_version"
    private const val CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L
    private const val UPDATE_CHANNEL = "sx_dynamic_updates"
    private const val UPDATE_NOTIFICATION_ID = 2201

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "sx-update-check").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    fun currentVersion(context: Context): String = GitHubUpdateClient.currentVersion(context)

    fun checkAsync(
        context: Context,
        force: Boolean = false,
        callback: ((Result<UpdateRelease?>) -> Unit)? = null,
    ) {
        val appContext = context.applicationContext
        val prefs = prefs(appContext)
        val now = System.currentTimeMillis()
        val cached = cachedRelease(appContext)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)

        if (!force && now - lastCheck < CHECK_INTERVAL_MS) {
            callback?.let { cb -> mainHandler.post { cb(Result.success(cached)) } }
            if (cached != null) notifyUpdateAvailable(appContext, cached)
            return
        }

        executor.execute {
            val result = runCatching { GitHubUpdateClient.fetchAvailableUpdate(appContext) }
            result.onSuccess { release ->
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                cacheRelease(appContext, release)
                if (release != null) {
                    Diagnostics.info("update", "Update " + release.version + " is available")
                    notifyUpdateAvailable(appContext, release)
                } else {
                    Diagnostics.info("update", "No newer published release found")
                }
            }.onFailure { error ->
                Diagnostics.error("update", "Update check failed", error)
            }
            callback?.let { cb -> mainHandler.post { cb(result) } }
        }
    }

    fun cachedRelease(context: Context): UpdateRelease? {
        val prefs = prefs(context)
        val version = prefs.getString(KEY_RELEASE_VERSION, null).orEmpty()
        val url = prefs.getString(KEY_RELEASE_URL, null).orEmpty()
        val file = prefs.getString(KEY_RELEASE_FILE, null).orEmpty()
        if (version.isBlank() || url.isBlank() || file.isBlank()) return null
        if (GitHubUpdateClient.compareVersions(version, currentVersion(context)) <= 0) {
            clearCachedRelease(context)
            return null
        }
        return UpdateRelease(
            version = version,
            tag = prefs.getString(KEY_RELEASE_TAG, "v" + version).orEmpty(),
            name = prefs.getString(KEY_RELEASE_NAME, "SX Dynamic v" + version).orEmpty(),
            notes = prefs.getString(KEY_RELEASE_NOTES, "").orEmpty(),
            downloadUrl = url,
            fileName = file,
            sizeBytes = prefs.getLong(KEY_RELEASE_SIZE, 0L),
            publishedAt = prefs.getString(KEY_RELEASE_PUBLISHED, "").orEmpty(),
        )
    }

    fun enqueueDownload(context: Context, release: UpdateRelease): Long {
        val manager = context.getSystemService(DownloadManager::class.java)
        val request = DownloadManager.Request(Uri.parse(release.downloadUrl)).apply {
            setTitle("SX Dynamic v" + release.version)
            setDescription("Baixando atualização oficial do GitHub Releases")
            setMimeType("application/vnd.android.package-archive")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                release.fileName,
            )
        }
        val id = manager.enqueue(request)
        prefs(context).edit()
            .putLong(KEY_DOWNLOAD_ID, id)
            .putString(KEY_DOWNLOAD_VERSION, release.version)
            .apply()
        Diagnostics.info("update", "Update download queued for version " + release.version)
        return id
    }

    fun downloadState(context: Context): UpdateDownloadState {
        val preferences = prefs(context)
        val id = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id <= 0L) return UpdateDownloadState.NONE

        val release = cachedRelease(context)
        val downloadedVersion = preferences.getString(KEY_DOWNLOAD_VERSION, "").orEmpty()
        if (release == null || downloadedVersion != release.version) return UpdateDownloadState.NONE
        val manager = context.getSystemService(DownloadManager::class.java)
        val cursor = runCatching { manager.query(DownloadManager.Query().setFilterById(id)) }.getOrNull()
            ?: return UpdateDownloadState.NONE
        cursor.use {
            if (!it.moveToFirst()) return UpdateDownloadState.NONE
            val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (statusIndex < 0) return UpdateDownloadState.NONE
            return when (it.getInt(statusIndex)) {
                DownloadManager.STATUS_PENDING -> UpdateDownloadState.PENDING
                DownloadManager.STATUS_RUNNING -> UpdateDownloadState.RUNNING
                DownloadManager.STATUS_PAUSED -> UpdateDownloadState.PAUSED
                DownloadManager.STATUS_SUCCESSFUL -> UpdateDownloadState.SUCCESSFUL
                DownloadManager.STATUS_FAILED -> UpdateDownloadState.FAILED
                else -> UpdateDownloadState.NONE
            }
        }
    }

    fun installDownloaded(context: Context): InstallUpdateResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.packageName),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return InstallUpdateResult.PermissionRequired
        }

        val id = prefs(context).getLong(KEY_DOWNLOAD_ID, -1L)
        if (id <= 0L) return InstallUpdateResult.MissingDownload
        val manager = context.getSystemService(DownloadManager::class.java)
        val uri = manager.getUriForDownloadedFile(id) ?: return InstallUpdateResult.MissingDownload

        return runCatching {
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_RETURN_RESULT, false)
            }
            context.startActivity(intent)
            Diagnostics.info("update", "Package installer opened for downloaded update")
            InstallUpdateResult.Started
        }.getOrElse { error ->
            Diagnostics.error("update", "Unable to open package installer", error)
            InstallUpdateResult.Failed(error.message.orEmpty())
        }
    }

    fun handleDownloadCompleted(context: Context, downloadId: Long) {
        val expected = prefs(context).getLong(KEY_DOWNLOAD_ID, -1L)
        if (downloadId != expected || downloadId <= 0L) return
        if (downloadState(context) != UpdateDownloadState.SUCCESSFUL) {
            Diagnostics.warn("update", "Update download completed without success status")
            return
        }
        Diagnostics.info("update", "Update APK download completed")
        notifyReadyToInstall(context)
    }

    private fun cacheRelease(context: Context, release: UpdateRelease?) {
        if (release == null) {
            clearCachedRelease(context)
            return
        }
        prefs(context).edit()
            .putString(KEY_RELEASE_VERSION, release.version)
            .putString(KEY_RELEASE_TAG, release.tag)
            .putString(KEY_RELEASE_NAME, release.name)
            .putString(KEY_RELEASE_NOTES, release.notes)
            .putString(KEY_RELEASE_URL, release.downloadUrl)
            .putString(KEY_RELEASE_FILE, release.fileName)
            .putLong(KEY_RELEASE_SIZE, release.sizeBytes)
            .putString(KEY_RELEASE_PUBLISHED, release.publishedAt)
            .apply()
    }

    private fun clearCachedRelease(context: Context) {
        prefs(context).edit()
            .remove(KEY_RELEASE_VERSION)
            .remove(KEY_RELEASE_TAG)
            .remove(KEY_RELEASE_NAME)
            .remove(KEY_RELEASE_NOTES)
            .remove(KEY_RELEASE_URL)
            .remove(KEY_RELEASE_FILE)
            .remove(KEY_RELEASE_SIZE)
            .remove(KEY_RELEASE_PUBLISHED)
            .apply()
    }

    private fun notifyUpdateAvailable(context: Context, release: UpdateRelease) {
        val manager = context.getSystemService(NotificationManager::class.java)
        ensureUpdateChannel(manager)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, UPDATE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_sx)
            .setContentTitle("SX Dynamic v" + release.version + " disponível")
            .setContentText("Toque para revisar e instalar a atualização.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(UPDATE_NOTIFICATION_ID, notification) }
    }

    private fun notifyReadyToInstall(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        ensureUpdateChannel(manager)
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, UPDATE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_sx)
            .setContentTitle("Atualização pronta para instalar")
            .setContentText("Abra o SX Dynamic para concluir a instalação.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(UPDATE_NOTIFICATION_ID, notification) }
    }

    private fun ensureUpdateChannel(manager: NotificationManager) {
        manager.createNotificationChannel(
            NotificationChannel(
                UPDATE_CHANNEL,
                "Atualizações do SX Dynamic",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Novas versões publicadas no repositório oficial SX-Dynamic."
                setShowBadge(false)
            },
        )
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
