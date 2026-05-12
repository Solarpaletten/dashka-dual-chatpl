package com.solar.dashka.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire response from POST /api/translate.
 *
 * Mirrors the backend success envelope. On error the backend returns
 * `{status: "error", message: "..."}` — we let Retrofit surface that as an
 * HttpException and decode the message in TranslationRepositoryImpl.
 */
@Serializable
data class TranslateResponse(
    val status: String,
    @SerialName("original_text") val originalText: String,
    @SerialName("translated_text") val translatedText: String,
    @SerialName("source_language") val sourceLanguage: String,
    @SerialName("target_language") val targetLanguage: String,
    val confidence: Double,
    @SerialName("processing_time") val processingTime: Long,
    val provider: String,
    val timestamp: String? = null,
    @SerialName("from_cache") val fromCache: Boolean = false,
    val message: String? = null,
)
