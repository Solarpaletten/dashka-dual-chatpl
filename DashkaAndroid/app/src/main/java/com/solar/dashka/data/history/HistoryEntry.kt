package com.solar.dashka.data.history

import com.solar.dashka.domain.model.LangCode
import com.solar.dashka.domain.model.TtsVoice
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Sprint 4C — Single translation history entry.
 *
 * Designed backend-ready from day 1: UUID + ISO timestamp + serializable
 * primitives only. When Sprint 4G+ adds cloud sync, no schema migration
 * needed — same DTO travels to backend.
 *
 * @property id Stable UUID. Generated locally on save. Used as primary key
 *   in future cloud sync (last-write-wins) and as React-style key in lists.
 * @property timestampMillis Unix epoch ms (UTC). Display formatting happens
 *   in UI layer.
 * @property sourceText Original input text (in [sourceLang]).
 * @property translatedText Result text (in [targetLang]).
 * @property sourceLang Auto-detected or user-selected source language.
 * @property targetLang User-selected target language.
 * @property voice TtsVoice persona used for this translation. Reload restores
 *   it so the user gets the same audio character.
 */
@Serializable
data class HistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestampMillis: Long,
    val sourceText: String,
    val translatedText: String,
    val sourceLang: LangCode,
    val targetLang: LangCode,
    val voice: TtsVoice,
)
