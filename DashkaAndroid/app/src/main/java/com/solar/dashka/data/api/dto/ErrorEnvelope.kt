package com.solar.dashka.data.api.dto

import kotlinx.serialization.Serializable

/**
 * Backend error envelope. Both /api/translate and /api/tts return this shape on
 * non-2xx responses:
 *   {"status": "error", "message": "..."}
 *
 * Used by TranslationRepositoryImpl to extract a friendly message from
 * HttpException.response()?.errorBody().
 */
@Serializable
data class ErrorEnvelope(
    val status: String,
    val message: String,
)
