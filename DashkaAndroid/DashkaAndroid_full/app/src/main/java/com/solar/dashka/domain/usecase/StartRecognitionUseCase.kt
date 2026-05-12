package com.solar.dashka.domain.usecase

import com.solar.dashka.data.speech.SpeechRecognitionResult
import com.solar.dashka.domain.model.LangCode
import com.solar.dashka.domain.repository.SpeechRecognitionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Triggers a speech recognition session and exposes the resulting event Flow.
 * Stays thin — the repository owns the SpeechRecognizer lifecycle.
 */
class StartRecognitionUseCase @Inject constructor(
    private val repository: SpeechRecognitionRepository,
) {
    operator fun invoke(language: LangCode): Flow<SpeechRecognitionResult> =
        repository.startListening(language)

    fun isAvailable(): Boolean = repository.isAvailable()
}
