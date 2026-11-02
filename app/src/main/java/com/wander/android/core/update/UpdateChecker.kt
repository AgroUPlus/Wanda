package com.wander.android.core.update

import android.util.Log
import com.wander.android.BuildConfig
import com.wander.android.core.network.HttpClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val RELEASES_URL =
    "https://api.github.com/repos/AgroUPlus/Wanda/releases/latest"

private const val TAG = "UpdateChecker"

/** What the "Check for update" row found, once the network round-trip is done. */
sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class UpdateAvailable(val version: String, val releaseUrl: String) : UpdateCheckResult

    /** No answer worth trusting; [reason] says which part of the check failed. */
    data class Failed(val reason: UpdateCheckFailure) : UpdateCheckResult
}

sealed interface UpdateCheckFailure {
    /** GitHub was never reached: offline, DNS, TLS, or a timeout. */
    data object Network : UpdateCheckFailure

    /** GitHub answered, but not with a release — usually its rate limit (403/429), or 404 for none yet. */
    data class Http(val status: Int) : UpdateCheckFailure

    /** A reply that is not a release, or a latest release whose tag is not a version. */
    data object Parse : UpdateCheckFailure
}

/**
 * Compares the newest published GitHub release against the version installed on this device.
 *
 * Runs when the user taps the Settings row, and once per launch while "Check for updates on
 * launch" is on (see `WanderAppViewModel.checkForUpdateOnLaunch`). Never scheduled in the
 * background, per the app's battery-first rule. A failed lookup is reported as
 * [UpdateCheckResult.Failed] with its reason, rather than silently treated as "up to date".
 */
@Singleton
class UpdateChecker @Inject constructor(
    private val httpClient: HttpClient
) {
    suspend fun checkForUpdate(): UpdateCheckResult {
        val release = when (val fetched = fetchLatestRelease()) {
            is Fetched.Release -> fetched.release
            is Fetched.Failure -> return UpdateCheckResult.Failed(fetched.reason)
        }
        // `/releases/latest` never returns either, but the fields are there to be honoured.
        if (release.draft || release.prerelease) return UpdateCheckResult.UpToDate

        val latest = release.tagName.removePrefix("v")
        val newer = isNewer(latest, BuildConfig.VERSION_NAME) ?: run {
            Log.w(TAG, "release tag is not a version: ${release.tagName}")
            return UpdateCheckResult.Failed(UpdateCheckFailure.Parse)
        }
        return if (newer) UpdateCheckResult.UpdateAvailable(latest, release.htmlUrl) else UpdateCheckResult.UpToDate
    }

    private sealed interface Fetched {
        data class Release(val release: GitHubRelease) : Fetched
        data class Failure(val reason: UpdateCheckFailure) : Fetched
    }

    /**
     * The body is read as text and decoded here, not through `body<GitHubRelease>()`: the shared
     * client does not throw on an error status, so a rate-limit reply used to reach the decoder and
     * fail there, indistinguishable from a broken release.
     */
    private suspend fun fetchLatestRelease(): Fetched {
        val response: HttpResponse
        val text: String
        try {
            response = httpClient.get(RELEASES_URL) { header("Accept", "application/vnd.github+json") }
            if (!response.status.isSuccess()) {
                Log.w(TAG, "GitHub answered HTTP ${response.status.value}")
                return Fetched.Failure(UpdateCheckFailure.Http(response.status.value))
            }
            text = response.bodyAsText()
        } catch (e: IOException) {
            Log.w(TAG, "GitHub could not be reached: ${e.javaClass.simpleName}")
            return Fetched.Failure(UpdateCheckFailure.Network)
        }
        return try {
            Fetched.Release(HttpClientFactory.jsonConfig.decodeFromString(GitHubRelease.serializer(), text))
        } catch (e: IllegalArgumentException) {
            // kotlinx.serialization's SerializationException is an IllegalArgumentException.
            Log.w(TAG, "GitHub's reply is not a release: ${e.javaClass.simpleName}")
            Fetched.Failure(UpdateCheckFailure.Parse)
        }
    }
}
