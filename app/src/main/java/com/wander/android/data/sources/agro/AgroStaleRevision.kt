package com.wander.android.data.sources.agro

import java.io.IOException

/**
 * The server refused an edit because the playlist changed after the version the edit was made
 * against. Not a failure to report: the caller fetches the playlist again, keeps what still makes
 * sense, and retries. [currentRevision] is the version the server is at now.
 */
class AgroStaleRevision(val currentRevision: Long) :
    IOException("The playlist changed on the server since this edit was made")
