package com.solar.dashka.domain.usecase

import com.solar.dashka.domain.repository.SpeechRecognitionRepository
import javax.inject.Inject

/**
 * Politely ends recording — recognizer will emit a Final result if speech was
 * captured, then the flow closes.
 */
class StopRecognitionUseCase @Inject constructor(
    private val repository: SpeechRecognitionRepository,
) {
    operator fun invoke() = repository.stopListening()
}
