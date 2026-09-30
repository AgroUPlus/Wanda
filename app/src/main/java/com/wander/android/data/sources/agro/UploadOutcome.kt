package com.wander.android.data.sources.agro

/** What happened to one file. */
sealed interface UploadOutcome {
    /** The server already had these bytes. Nothing was transferred. */
    data object AlreadyPresent : UploadOutcome
    data object Uploaded : UploadOutcome
    /** Sent as far as it got. The next attempt resumes rather than restarting. */
    data class Partial(val received: Long) : UploadOutcome
    data class Failed(val reason: String) : UploadOutcome
}
