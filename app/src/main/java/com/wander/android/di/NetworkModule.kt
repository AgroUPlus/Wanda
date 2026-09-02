package com.wander.android.di

import com.wander.android.core.network.HttpClientFactory
import com.wander.android.core.network.ProxyRouting
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import okhttp3.OkHttpClient
import io.ktor.serialization.kotlinx.json.json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** One connection pool for the whole app: API calls, artwork and audio all share it. */
    @Provides
    @Singleton
    fun provideOkHttpClient(secureStorage: com.wander.android.core.security.SecureStorage): OkHttpClient {
        return HttpClientFactory.okHttpClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request()

                if (ProxyRouting.shouldRelay(request.url.host, request.url.encodedPath) &&
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
