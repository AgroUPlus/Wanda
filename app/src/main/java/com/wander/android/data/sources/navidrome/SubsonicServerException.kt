package com.wander.android.data.sources.navidrome

import java.io.IOException

/**
 * The server was reached and answered `status="failed"`.
 *
 * Kept apart from a transport failure, which is a plain [IOException], because the two mean
 * opposite things to a caller: this one is an answer ("no such lyrics", "not supported"), the
 * other is the absence of one. It stays an [IOException] so existing callers see no change.
 */
class SubsonicServerException(val code: Int?, message: String) : IOException(message)
