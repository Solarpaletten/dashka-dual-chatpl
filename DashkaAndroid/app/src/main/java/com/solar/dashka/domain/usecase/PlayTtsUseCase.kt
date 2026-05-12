package com.solar.dashka.domain.usecase

import com.solar.dashka.domain.model.LangCode
import com.solar.dashka.domain.model.TtsState
import com.solar.dashka.domain.model.TtsVoice
import com.solar.dashka.domain.repository.TtsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Triggers TTS playback. Returns a Flow<TtsState> that walks Loading → Playing → Idle.
 */
class PlayTtsUseCase @Inject constructor(
    private val repository: TtsRepository,
) {
    operator fun invoke(
        text: String,
        language: LangCode,
        voice: TtsVoice,
    ): Flow<TtsState> = repository.play(text, language, voice)
}
