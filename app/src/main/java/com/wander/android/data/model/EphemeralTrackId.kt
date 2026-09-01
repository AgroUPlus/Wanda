package com.wander.android.data.model

/**
 * Ids of tracks that exist for exactly one playback and must never outlive it.
 *
 * `ListenAlongResolver` mints these for a peer's file and for a relayed one. They name a transfer,
 * not a track: the URL behind them carries a bearer token, the relay session it points at is
 * consumed on first use, and neither is worth anything a minute later.
 *
 * They are declared `SourceType.LOCAL` because that is what they behave like once resolved —
 * bytes this device can play. That is also what makes them dangerous to store: a row with this id
 * and `source = LOCAL` is indistinguishable, to a search, from a file actually on the phone.
 */
private val ONE_SHOT_TRACK_PREFIXES = listOf("relay:", "p2p:")

/**
 * True for a stream that can be fetched once and stored never.
 *
 * Two rules hang off this. Nothing may re-open such a URL — a relay answers the second request
 * `409`. And nothing may persist one: a stale row shadows the real file forever, because the
 * offline-first tier searches `LOCAL` by title and finds the dead one first.
 */
fun isOneShotTrackId(id: String): Boolean = ONE_SHOT_TRACK_PREFIXES.any(id::startsWith)
