package com.wander.android.data.repository

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the current [RecordingRules].
 *
 * Both halves are already held in memory by their own repositories and re-read only after a write,
 * so taking a snapshot costs nothing beyond the two lock acquisitions it replaces — this exists for
 * ownership, not for speed. It is the one place that knows a recording judgement needs *both* the
 * user's pins and the fingerprinter's links.
 */
@Singleton
class RecordingRulesRepository @Inject constructor(
    private val splitRepository: RecordingSplitRepository,
    private val linkRepository: RecordingLinkRepository
) {

    /** The rules as they stand right now. */
    suspend fun current(): RecordingRules =
        RecordingRules(splitRepository.splits(), linkRepository.links())
}
