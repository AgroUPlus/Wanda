package com.wander.android.di

import android.content.Context
import com.wander.android.core.network.HttpClientFactory
import com.wander.android.core.network.ProxyRouting
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import okhttp3.Cache
import okhttp3.OkHttpClient
import io.ktor.serialization.kotlinx.json.json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** Every idempotent GET this size buys a second chance to be a 304 instead of a re-download. */
    private const val HTTP_CACHE_SIZE_BYTES = 20L * 1024 * 1024

    /**
     * How long a metadata lookup is trusted before asking again — see [cacheableMetadataHosts].
     * A day: neither host's answer for a given artist/track changes on any faster rhythm, and the
     * in-app freshness windows sitting above this (6h for an artist page, [LyricsRepository]'s
     * month-long absence cache) are already longer.
     */
    private const val METADATA_CACHE_MAX_AGE_SECONDS = 24 * 60 * 60

    /**
     * Hosts whose GET requests are worth forcing a `Cache-Control` onto, because unlike everything
     * else this app calls, their query strings are actually the same on a repeat request.
     *
     * Navidrome/Subsonic calls never repeat a URL: every request carries a fresh
     * [SubsonicApiClient]-generated salt and token in its query string, so caching by URL — which
     * is all OkHttp's `Cache` can do — would never produce a hit no matter what headers were sent.
     * YouTube Music's InnerTube API is POST-only, and HTTP caching only ever applies to GET/HEAD.
     * LRCLIB and MusicBrainz are the two calls actually shaped like a cacheable lookup: no auth, no
     * per-request nonce, the same track/artist producing the same URL every time.
     */
    private val cacheableMetadataHosts = setOf("lrclib.net", "musicbrainz.org")

    /**
     * One connection pool for the whole app: API calls, artwork and audio all share it.
     *
     * Only this client — the one the app's own API calls go through — gets an HTTP response
     * cache, not [HttpClientFactory.okHttpClient] itself: that base client is also what Coil and
     * [com.wander.android.core.cache.AudioCacheManager] build on, and artwork already has its own
     * disk cache while audio streaming has its own [androidx.media3.datasource.cache.SimpleCache]
     * — a second OkHttp-level cache under either would just be spending disk space to hold the
     * same bytes twice.
     *
     * Neither host this app talks to for its own catalog sends caching headers, and — see
     * [cacheableMetadataHosts] — Navidrome's per-request auth means their URLs never repeat
     * regardless, so a bare `.cache(...)` here would sit permanently empty. The network
     * interceptor below is what makes the cache do anything: it stamps a `Cache-Control` onto the
     * two lookups (LRCLIB, MusicBrainz) that are actually shaped like something worth caching.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        secureStorage: com.wander.android.core.security.SecureStorage
    ): OkHttpClient {
        return HttpClientFactory.okHttpClient.newBuilder()
            .cache(Cache(context.cacheDir.resolve("http_cache"), HTTP_CACHE_SIZE_BYTES))
            // A *network* interceptor, not an application one: this has to run on the way back
            // from the actual network call so the header it adds is what the cache layer sees and
            // stores, rather than running after the cache has already decided what to keep.
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                val cacheable = request.method == "GET" &&
                    request.url.host in cacheableMetadataHosts &&
                    response.header("Cache-Control") == null
                if (cacheable) {
                    response.newBuilder()
                        .header("Cache-Control", "public, max-age=$METADATA_CACHE_MAX_AGE_SECONDS")
                        .build()
                } else {
                    response
                }
            }
            .addInterceptor { chain ->
                val request = chain.request()

                if (ProxyRouting.shouldRelay(request.url.host) &&
                    secureStorage.agroProxyEnabled.value &&
                    secureStorage.agroApiKey.isNotEmpty()
                ) {
                    val agroUrl = secureStorage.agroServerUrl.trimEnd('/')
                    if (agroUrl.isNotEmpty()) {
                        val proxyUrl = "${agroUrl}/api/v1/proxy"
                        val newUrl = proxyUrl.toHttpUrlOrNull()
                        if (newUrl != null) {
                            val newRequest = request.newBuilder()
                                .url(newUrl)
                                .header("X-Agro-Proxy-Url", request.url.toString())
                                .header("Authorization", "Bearer ${secureStorage.agroApiKey}")
                                .build()
                            return@addInterceptor chain.proceed(newRequest)
                        }
                    }
                }
                chain.proceed(request)
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideHttpClient(okHttpClient: OkHttpClient): HttpClient =
        io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
            engine {
                preconfigured = okHttpClient
            }
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(HttpClientFactory.jsonConfig)
            }
        }
}
