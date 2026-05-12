package com.solar.dashka.di

import com.solar.dashka.BuildConfig
import com.solar.dashka.data.api.DashkaApi
import com.solar.dashka.data.api.DashkaTokenInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(DashkaTokenInterceptor(BuildConfig.DASHKA_API_TOKEN))
            // Slightly above OpenAI 10s server-side timeout (REC-004) so the
            // server-side error reaches us as a clean 500, not a client timeout.
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            // HEADERS-level only — body is never logged. The X-Dashka-Token
            // header WILL appear in debug logcat (because the token interceptor
            // is registered first), which is acceptable for development.
            // Release builds attach no logging interceptor at all.
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.HEADERS }
            )
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.DASHKA_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideDashkaApi(retrofit: Retrofit): DashkaApi =
        retrofit.create(DashkaApi::class.java)
}
