package com.wander.android.data.sources.navidrome

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds Subsonic auth to unsigned requests for the configured Navidrome server, see
 * [SubsonicCredentials.sign]. Installed on every client that fetches artwork.
 */
@Singleton
class SubsonicRequestSigner @Inject constructor(
    private val credentials: SubsonicCredentials
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val signed = credentials.sign(request.url)
        return chain.proceed(if (signed == request.url) request else request.newBuilder().url(signed).build())
    }
}
