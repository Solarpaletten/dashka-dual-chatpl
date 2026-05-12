package com.solar.dashka.domain.model

/**
 * Domain-level translation result. Returned by TranslationRepository — does
 * not leak the raw DTO into the presentation layer.
 */
data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val confidence: Double,
    val processingTimeMs: Long,
    val provider: String,
)
