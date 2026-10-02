package com.solucionx.sxdynamic.update

import android.content.Context
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object GitHubUpdateClient {
    private const val latestReleaseUrl =
        "https://api.github.com/repos/solucionx/SX-Dynamic/releases/latest"

    fun fetchAvailableUpdate(context: Context): UpdateRelease? {
        val currentVersion = currentVersion(context)
        val connection = (URL(latestReleaseUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "SX-Dynamic/" + currentVersion)
        }

        try {
            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_NOT_FOUND) return null
            if (status !in 200..299) {
                val message = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("GitHub Releases returned HTTP " + status + " " + message.take(160))
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (json.optBoolean("draft", false) || json.optBoolean("prerelease", false)) return null

            val tag = json.optString("tag_name").trim()
            val releaseVersion = normalizeVersion(tag)
            if (releaseVersion.isBlank() || compareVersions(releaseVersion, currentVersion) <= 0) {
                return null
            }

            val assets = json.optJSONArray("assets") ?: return null
            val debugPackage = context.packageName.endsWith(".debug")
            var selectedName = ""
            var selectedUrl = ""
            var selectedSize = 0L

            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                if (!name.endsWith(".apk", ignoreCase = true)) continue

                val isDebugAsset = name.contains("-debug", ignoreCase = true)
                if (debugPackage != isDebugAsset) continue

                selectedName = name
                selectedUrl = asset.optString("browser_download_url")
                selectedSize = asset.optLong("size", 0L)
                if (selectedUrl.isNotBlank()) break
            }

            if (selectedUrl.isBlank()) return null

            return UpdateRelease(
                version = releaseVersion,
                tag = tag,
                name = json.optString("name").ifBlank { tag },
                notes = json.optString("body"),
                downloadUrl = selectedUrl,
                fileName = selectedName,
                sizeBytes = selectedSize,
                publishedAt = json.optString("published_at"),
            )
        } finally {
            connection.disconnect()
        }
    }

    fun currentVersion(context: Context): String {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return normalizeVersion(info.versionName.orEmpty())
    }

    internal fun compareVersions(left: String, right: String): Int {
        val a = normalizeVersion(left).split('.').map { it.toIntOrNull() ?: 0 }
        val b = normalizeVersion(right).split('.').map { it.toIntOrNull() ?: 0 }
        val size = maxOf(a.size, b.size)
        for (index in 0 until size) {
            val av = a.getOrElse(index) { 0 }
            val bv = b.getOrElse(index) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    internal fun normalizeVersion(value: String): String {
        val match = Regex("\\d+(?:\\.\\d+){1,3}").find(value)
        return match?.value.orEmpty()
    }
}
