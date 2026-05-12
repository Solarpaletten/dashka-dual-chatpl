package com.solar.dashka.domain.model

import kotlinx.serialization.Serializable

/**
 * Mirrors `TtsVoice` from web `features/translator/types-runtime.ts`.
 *
 * Sprint 1 stores the voice in PaneState for forward compatibility but does not
 * play audio. TTS lands in a later sprint.
 *
 * Sprint 4C: @Serializable so HistoryEntry can persist by name.
 */
@Serializable
enum class TtsVoice(val id: String, val displayName: String, val isFemale: Boolean) {
    EVE(id = "eve", displayName = "Eve", isFemale = true),
    ARA(id = "ara", displayName = "Ara", isFemale = true),
    LEO(id = "leo", displayName = "Leo", isFemale = false),
    REX(id = "rex", displayName = "Rex", isFemale = false),
    SAL(id = "sal", displayName = "Sal", isFemale = false);

    companion object {
        val DEFAULT_LEFT = LEO    // mirror DEFAULT_VOICE_LEFT in web /config.ts
        val DEFAULT_RIGHT = EVE   // mirror DEFAULT_VOICE_RIGHT in web /config.ts
    }
}
