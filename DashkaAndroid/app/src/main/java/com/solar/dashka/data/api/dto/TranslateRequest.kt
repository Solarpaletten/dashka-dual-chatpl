package com.solar.dashka.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire request for POST /api/translate.
 *
 * Field names mirror the backend post-REC-001…005 contract:
 *   - text             — required, max 5000 chars (REC-003)
 *   - source_language  — optional; if absent, backend auto-detects
 *   - target_language  — required, must be in ALLOWED_LANGS (REC-005)
 */
@Serializable
data class TranslateRequest(
    val text: String,
    @SerialName("source_language") val sourceLanguage: String? = null,
    @SerialName("target_language") val targetLanguage: String,
)
