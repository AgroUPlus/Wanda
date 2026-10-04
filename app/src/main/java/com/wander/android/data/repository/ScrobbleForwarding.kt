package com.wander.android.data.repository

import com.wander.android.core.database.dao.HistoryDao
import com.wander.android.core.database.dao.PendingScrobble
import com.wander.android.core.security.SecureStorage
import com.wander.android.data.sources.scrobble.LastFmClient
import com.wander.android.data.sources.scrobble.ListenBrainzClient
import com.wander.android.data.sources.scrobble.NowPlaying
import com.wander.android.data.sources.scrobble.ScrobbleOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The two services plays can be forwarded to. */
enum class ScrobbleService { LISTENBRAINZ, LASTFM }

/**
 * Forwarding plays from this device to ListenBrainz and Last.fm, straight from the phone.
 *
 * Each service drains its own outbox flag on `history`, exactly as the Agro sync drains its own,
 * so one being down never holds the other back or loses a play. Nothing is sent to a service that
 * is not connected and switched on; connecting is the consent, and only plays from then on go.
 * Agro is not involved: the token and session never leave this device.
 */
@Singleton
internal class ScrobbleForwarding @Inject constructor(
    private val historyDao: HistoryDao,
    private val secureStorage: SecureStorage,
    private val listenBrainz: ListenBrainzClient,
    private val lastFm: LastFmClient
) {
    private val prefs get() = secureStorage.scrobbling

    /** Why a service was signed out on its own, until the user connects it again. */
    private val _signedOut = MutableStateFlow<Map<ScrobbleService, String>>(emptyMap())
    val signedOut: StateFlow<Map<ScrobbleService, String>> = _signedOut.asStateFlow()

    val isAnyOn: Boolean get() = prefs.listenBrainz.value.enabled || prefs.lastFm.value.enabled

    /**
     * Sends whatever each switched-on service is owed, a bounded number of batches per call.
     * Answers whether anything is worth trying again later.
     */
    suspend fun forward(): Boolean {
        var retry = false
        prefs.listenBrainzToken?.takeIf { prefs.listenBrainz.value.enabled }?.let { token ->
            retry = drain(ScrobbleService.LISTENBRAINZ, ListenBrainzClient.BATCH, { historyDao.getPendingListenBrainz(it) },
                { listenBrainz.submit(token, it) }, { historyDao.markListenBrainzSynced(it) }) || retry
        }
        prefs.lastFmSession?.takeIf { prefs.lastFm.value.enabled }?.let { session ->
            retry = drain(ScrobbleService.LASTFM, LastFmClient.BATCH, { historyDao.getPendingLastFm(it) },
                { lastFm.scrobble(session, it) }, { historyDao.markLastFmSynced(it) }) || retry
        }
        return retry
    }

    private suspend fun drain(
        service: ScrobbleService,
        batch: Int,
        pending: suspend (Int) -> List<PendingScrobble>,
        send: suspend (List<PendingScrobble>) -> ScrobbleOutcome,
        mark: suspend (List<Long>) -> Unit
    ): Boolean {
        repeat(MAX_BATCHES) {
            val plays = pending(batch)
            if (plays.isEmpty()) return false
            when (val outcome = send(plays)) {
                ScrobbleOutcome.Done -> mark(plays.map { it.historyId })
                is ScrobbleOutcome.Retry -> return true
                is ScrobbleOutcome.Unauthorized -> {
                    signOut(service, outcome.reason)
                    return false
                }
            }
        }
        return true
    }

    /** Tells each switched-on service what is playing. Best effort: a miss is shown nowhere. */
    suspend fun nowPlaying(track: NowPlaying) {
        if (secureStorage.isIncognitoMode) return
        prefs.listenBrainzToken?.takeIf { prefs.listenBrainz.value.enabled }?.let { token ->
            (listenBrainz.playingNow(token, track) as? ScrobbleOutcome.Unauthorized)?.let { signOut(ScrobbleService.LISTENBRAINZ, it.reason) }
        }
        prefs.lastFmSession?.takeIf { prefs.lastFm.value.enabled }?.let { session ->
            (lastFm.nowPlaying(session, track) as? ScrobbleOutcome.Unauthorized)?.let { signOut(ScrobbleService.LASTFM, it.reason) }
        }
    }

    /** Checks the token, then keeps it; only plays from now on are sent. */
    suspend fun connectListenBrainz(token: String): Result<String> = listenBrainz.validate(token.trim()).onSuccess { user ->
        historyDao.settleListenBrainz()
        prefs.connectListenBrainz(user, token.trim())
        _signedOut.value = _signedOut.value - ScrobbleService.LISTENBRAINZ
    }

    /** A Last.fm token to approve and the page to approve it on. */
    suspend fun beginLastFm(): Result<Pair<String, String>> = lastFm.beginSignIn()

    /** Exchanges an approved token for a session, then keeps it; only plays from now on are sent. */
    suspend fun finishLastFm(token: String): Result<String> = lastFm.finishSignIn(token).map { (user, session) ->
        historyDao.settleLastFm()
        prefs.connectLastFm(user, session)
        _signedOut.value = _signedOut.value - ScrobbleService.LASTFM
        user
    }

    val lastFmShipsKey: Boolean get() = lastFm.shipsAppKey
    val lastFmHasKey: Boolean get() = lastFm.hasAppKey

    private fun signOut(service: ScrobbleService, reason: String) {
        when (service) {
            ScrobbleService.LISTENBRAINZ -> prefs.disconnectListenBrainz()
            ScrobbleService.LASTFM -> prefs.disconnectLastFm()
        }
        _signedOut.value = _signedOut.value + (service to reason)
    }

    private companion object {
        /** Per service per run, so a long-offline outbox drains over a few runs, not one long one. */
        const val MAX_BATCHES = 10
    }
}
