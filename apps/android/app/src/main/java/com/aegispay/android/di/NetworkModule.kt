package com.aegispay.android.di

import com.aegispay.android.BuildConfig
import com.aegispay.android.auth.AuthRepository
import com.aegispay.android.network.AegisApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import okhttp3.CertificatePinner
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(Date::class.java, Rfc3339DateJsonAdapter().nullSafe())
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(authRepository: AuthRepository): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG)
                HttpLoggingInterceptor.Level.BODY
            else
                HttpLoggingInterceptor.Level.NONE
        }

        val authInterceptor = Interceptor { chain ->
            val token = runBlocking { runCatching { authRepository.validAccessToken() }.getOrNull() }
            val request = chain.request().newBuilder()
                .apply { if (token != null) header("Authorization", "Bearer $token") }
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                .build()
            chain.proceed(request)
        }

        // ── Certificate pinning ────────────────────────────────────────────────
        // Pin the API gateway's public key (SPKI SHA-256).
        // Populate PINNED_CERT_HASHES via BuildConfig (injected in CI from secrets).
        // In debug builds the list is empty → no pinning (allows local dev proxy).
        //
        // To generate a pin hash:
        //   openssl s_client -connect api.aegispay.io:443 2>/dev/null \
        //     | openssl x509 -pubkey -noout \
        //     | openssl pkey -pubin -outform DER \
        //     | openssl dgst -sha256 -binary | base64
        //
        // Then add to build config:
        //   buildConfigField("String", "PINNED_CERT_HASH", "\"sha256/<base64>\"")
        val pinnedHosts: List<String> = buildList {
            val rawHost = BuildConfig.API_BASE_URL
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore("/")
                .substringBefore(":")
            if (rawHost.isNotBlank() && !rawHost.contains("10.0.2.2") && !rawHost.contains("localhost")) {
                add(rawHost)
            }
        }

        val certPinnerBuilder = CertificatePinner.Builder()
        val rawHashes = runCatching {
            BuildConfig::class.java.getField("PINNED_CERT_HASHES").get(null) as? String ?: ""
        }.getOrDefault("")

        if (rawHashes.isNotBlank() && pinnedHosts.isNotEmpty()) {
            rawHashes.split(",").map { it.trim() }.filter { it.startsWith("sha256/") }.forEach { hash ->
                pinnedHosts.forEach { host -> certPinnerBuilder.add(host, hash) }
            }
        }

        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)

        if (rawHashes.isNotBlank() && pinnedHosts.isNotEmpty()) {
            builder.certificatePinner(certPinnerBuilder.build())
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL + "/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): AegisApiService =
        retrofit.create(AegisApiService::class.java)
}
