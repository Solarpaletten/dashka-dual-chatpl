package com.solar.dashka.data.api.dto

import kotlinx.serialization.Serializable

/**
 * Wire request for POST /api/tts.
 *
 * Sprint 1: declared but not used (TTS deferred to a later sprint).
 * Kept here so the API surface is complete and a future TtsRepository
 * doesn't need to touch this file.
 *
 *   - text     — required, max 5000 chars
 *   - language — RU, DE, EN, PL, ZH, FR, IT, ES, LV, LT, UA (post-REC-002)
 *   - voice    — eve | leo | ara | rex | sal
 */
@Serializable
data class TtsRequest(
    val text: String,
    val language: String,
    val voice: String,
)
