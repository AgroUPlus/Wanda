package com.wander.android.core.update

import com.wander.android.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import javax.inject.Inject
import javax.inject.Singleton

private const val RELEASES_URL =
    "https://api.github.com/repos/AgroUPlus/Wanda/releases/latest"

/** What the "Check for update" row found, once the network round-trip is done. */
sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class UpdateAvailable(val version: String, val releaseUrl: String) : UpdateCheckResult
    data object Failed : UpdateCheckResult
}

/**
 * Compares the newest published GitHub release against the version installed on this device.
 *
 * Manual only, from the Settings row that owns it — no background polling, per the app's
 * battery-first rule. A failed lookup (offline, rate-limited, no releases yet) is reported as
 * [UpdateCheckResult.Failed] rather than silently treated as "up to date".
 */
@Singleton
class UpdateChecker @Inject constructor(
    private val httpClient: HttpClient
) {
    suspend fun checkForUpdate(): UpdateCheckResult {
        val release = runCatching {
            httpClient.get(RELEASES_URL) { header("Accept", "application/vnd.github+json") }
                .body<GitHubRelease>()
        }.getOrNull() ?: return UpdateCheckResult.Failed

        if (release.draft || release.prerelease) return UpdateCheckResult.UpToDate

        val latest = release.tagName.removePrefix("v")
        return if (isNewer(latest, BuildConfig.VERSION_NAME)) {
            UpdateCheckResult.UpdateAvailable(latest, release.htmlUrl)
        } else {
            UpdateCheckResult.UpToDate
        }
    }

    /** Numeric, dot-separated comparison ("1.10.0" > "1.9.0"); a non-numeric part loses to any number. */
    private fun isNewer(latest: String, current: String): Boolean {
        val latestParts = latest.split(".")
        val currentParts = current.split(".")
        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val l = latestParts.getOrNull(i)?.toIntOrNull() ?: 0
            val c = currentParts.getOrNull(i)?.toIntOrNull() ?: 0
            if (l != c) return l > c
        }
        return false
    }
}
