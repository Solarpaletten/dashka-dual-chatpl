package com.solar.dashka.data.repository

import com.solar.dashka.data.api.DashkaApi
import com.solar.dashka.data.api.DashkaResult
import com.solar.dashka.data.api.dto.ErrorEnvelope
import com.solar.dashka.data.api.dto.TranslateRequest
import com.solar.dashka.domain.model.LangCode
import com.solar.dashka.domain.model.TranslationResult
import com.solar.dashka.domain.repository.TranslationRepository
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslationRepositoryImpl @Inject constructor(
    private val api: DashkaApi,
    private val json: Json,
) : TranslationRepository {

    override suspend fun translate(
        text: String,
        source: LangCode?,
        target: LangCode,
    ): DashkaResult<TranslationResult> {
        return try {
            val response = api.translate(
                TranslateRequest(
                    text = text,
                    sourceLanguage = source?.code,
                    targetLanguage = target.code,
                )
            )
            DashkaResult.Success(
                TranslationResult(
                    originalText = response.originalText,
                    translatedText = response.translatedText,
                    sourceLanguage = response.sourceLanguage,
                    targetLanguage = response.targetLanguage,
                    confidence = response.confidence,
                    processingTimeMs = response.processingTime,
                    provider = response.provider,
                )
            )
        } catch (e: HttpException) {
            handleHttpException(e)
        } catch (e: SocketTimeoutException) {
            DashkaResult.Error.Timeout
        } catch (e: IOException) {
            DashkaResult.Error.NetworkError
        } catch (e: Exception) {
            DashkaResult.Error.Unknown(e)
        }
    }

    private fun handleHttpException(e: HttpException): DashkaResult.Error {
        if (e.code() == 401) return DashkaResult.Error.Unauthorized

        // Try to decode the standard error envelope; fall back to HTTP code text.
        val rawBody = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        val parsedMessage = rawBody
            ?.let { runCatching { json.decodeFromString<ErrorEnvelope>(it).message }.getOrNull() }
            ?: e.message()
            ?: "Server error ${e.code()}"

        return DashkaResult.Error.Server(code = e.code(), message = parsedMessage)
    }
}
