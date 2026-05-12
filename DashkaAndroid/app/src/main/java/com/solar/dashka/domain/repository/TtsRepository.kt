package com.solar.dashka.domain.repository

import com.solar.dashka.data.api.DashkaResult
import com.solar.dashka.domain.model.LangCode
import com.solar.dashka.domain.model.TtsState
import com.solar.dashka.domain.model.TtsVoice
import kotlinx.coroutines.flow.Flow

/**
 * Domain abstraction over the TTS pipeline (fetch + cache + playback).
 *
 * Sprint 3A: only manual play. Sprint 3C will add autoplay; Sprint 3D will
 * add interruption handling.
 */
interface TtsRepository {

    /**
     * Stream of playback state events. Cold flow — collecting starts the
     * fetch+play chain, stopping cancels playback and tears down the player.
     *
     * @param text the text to synthesize (max 5000 chars per backend cap)
     * @param language target language for voice selection
     * @param voice the voice character to use
     */
    fun play(
        text: String,
        language: LangCode,
        voice: TtsVoice,
    ): Flow<TtsState>

    /**
     * Stop any active playback.
     */
    fun stop()

    /**
     * Pre-fetch MP3 to cache without playing. Reserved for Sprint 3C autoplay
     * pre-warming. Returns Success(Unit) when cached, Error otherwise.
     */
    suspend fun prefetch(
        text: String,
        language: LangCode,
        voice: TtsVoice,
    ): DashkaResult<Unit>
}
