package com.wander.android.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.io.IOException
import java.util.concurrent.TimeUnit

object HttpClientFactory {

    const val DEFAULT_USER_AGENT = "Wanda/1.0 (GrapheneOS; Privacy-Hardened)"

    /**
     * `network_security_config.xml` permits cleartext globally at the manifest level because P2P
     * peers and self-hosted LAN servers live at dynamic IPs no static domain list can enumerate.
     * That leaves nothing stopping plain HTTP to a *public* host. This is the actual enforcement:
     * cleartext is only ever allowed to a private/loopback/mDNS peer.
     */
    private val PRIVATE_IPV4_PREFIXES = listOf("10.", "192.168.", "169.254.")

    private fun isLocalOrLoopback(host: String): Boolean {
        val h = host.trim('[', ']')
        if (h.equals("localhost", ignoreCase = true) || h == "127.0.0.1" || h == "::1" || h == "10.0.2.2") {
            return true
        }
        if (h.endsWith(".local", ignoreCase = true)) return true
        if (PRIVATE_IPV4_PREFIXES.any { h.startsWith(it) }) return true
        // 172.16.0.0/12 covers the second octet 16-31.
        val octets = h.split(".")
        if (octets.size == 4 && octets[0] == "172") {
            val second = octets[1].toIntOrNull()
            if (second != null && second in 16..31) return true
        }
        return false
    }

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
            .addInterceptor { chain ->
                val request = chain.request()
                if (request.url.scheme == "http" && !isLocalOrLoopback(request.url.host)) {
                    throw IOException("Cleartext HTTP traffic to public host '${request.url.host}' rejected")
                }
                chain.proceed(request)
            }
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
