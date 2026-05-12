package com.solar.dashka.domain.model

/**
 * Single-pane UI state. In Sprint 1 mobile shows ONE active pane at a time
 * (Decision 3); the user toggles direction via DirectionToggle.
 *
 * Sprint 2A: added micState for voice input flow.
 *
 * Mirrors the web `PaneState` interface conceptually but is simpler — no
 * separate isPlaying / voice yet (those land in Sprint 3 with TTS).
 */
data class PaneState(
    val direction: Direction = Direction.RU_TO_PARTNER,
    val inputText: String = "",
    val translatedText: String = "",
    val isTranslating: Boolean = false,
    val voice: TtsVoice = TtsVoice.LEO,
    val errorMessage: String? = null,
    val micState: MicState = MicState.Idle,
    val ttsState: TtsState = TtsState.Idle,
    val autoplayEnabled: Boolean = false,
    val isPreparingShareVoice: Boolean = false,
)
