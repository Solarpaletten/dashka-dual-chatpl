package com.solar.dashka.domain.model

/**
 * Sprint 4B.1 — defines what gets attached when sharing a translation
 * via the Text Share button bottom sheet.
 *
 * VoiceOnly was removed in Sprint 4B.1 — it's now a dedicated 🔊 button on the
 * output card for immediate one-tap voice sharing (Дашкин UX refinement —
 * "voice deserves dedicated action").
 *
 * Sealed interface (not enum) to allow future extensions like:
 *   - VoiceWithTranscriptPdf (Sprint 4D+)
 *   - SourceAndTranslationBilingual
 */
sealed interface ShareMode {
    /** Plain text only — quick text-share use case. */
    data object TextOnly : ShareMode

    /** Both attached — full deliverable for business correspondence. */
    data object TextAndVoice : ShareMode
}
