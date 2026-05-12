package com.solar.dashka.data.api

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the X-Dashka-Token header to every outgoing request when the token
 * is non-blank. Mirrors the web client behavior added in REC-001.
 *
 * Token comes from BuildConfig.DASHKA_API_TOKEN, which is populated at build
 * time from gradle.properties (or -PDASHKA_API_TOKEN=...). See gradle.properties
 * for the security note: this token is an abuse-deterrent, not a real secret.
 */
class DashkaTokenInterceptor(
    private val token: String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (token.isBlank()) {
            // Build was done without a token. Pass through; the backend will return
            // 401 if it has DASHKA_API_TOKEN set, or 200 if the guard is disabled.
            return chain.proceed(original)
        }
        val authed = original.newBuilder()
            .addHeader(HEADER_NAME, token)
            .build()
        return chain.proceed(authed)
    }

    companion object {
        const val HEADER_NAME = "X-Dashka-Token"
    }
}
