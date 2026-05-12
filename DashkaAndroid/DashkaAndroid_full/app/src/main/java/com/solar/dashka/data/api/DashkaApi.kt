package com.solar.dashka.data.api

import com.solar.dashka.data.api.dto.HealthResponse
import com.solar.dashka.data.api.dto.TranslateRequest
import com.solar.dashka.data.api.dto.TranslateResponse
import com.solar.dashka.data.api.dto.TtsRequest
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Streaming

/**
 * Retrofit interface for the Dashka backend (post-REC-001…005).
 *
 * Sprint 1 actively uses only `translate` and `health`. `tts` is declared so the
 * API surface stays consistent — it will be wired up in a later sprint.
 *
 * Authentication: handled transparently by DashkaTokenInterceptor in the
 * OkHttp client — endpoints don't accept a header parameter.
 *
 * Errors: Retrofit 3 throws HttpException for non-2xx responses on suspend
 * functions, and IOException for network failures. The repository catches both
 * and converts them into DashkaResult.Error subtypes.
 */
interface DashkaApi {

    @POST("api/translate")
    suspend fun translate(@Body req: TranslateRequest): TranslateResponse

    @POST("api/tts")
    @Streaming
    suspend fun tts(@Body req: TtsRequest): ResponseBody

    @GET("api/health")
    suspend fun health(): HealthResponse
}
