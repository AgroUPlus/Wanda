package com.wander.android.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

object HttpClientFactory {

    const val DEFAULT_USER_AGENT = "Wanda/1.0 (GrapheneOS; Privacy-Hardened)"

    val jsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = false
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // A default identity for requests that do not set one, never an override. `.header()`
            // replaces, so applying this unconditionally rewrote the User-Agent that InnerTube and
            // the googlevideo media fetch depend on — the stream URL is minted for one client and
            // rejected when fetched as another.
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.header("User-Agent") != null) return@addInterceptor chain.proceed(request)
                chain.proceed(
                    request.newBuilder()
                        .header("User-Agent", DEFAULT_USER_AGENT)
                        .build()
                )
            }
            .build()
    }

    val ktorClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            engine {
                preconfigured = okHttpClient
            }
            install(ContentNegotiation) {
                json(jsonConfig)
            }
        }
    }
}
