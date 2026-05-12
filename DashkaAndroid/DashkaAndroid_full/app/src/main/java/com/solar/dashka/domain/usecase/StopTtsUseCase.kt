package com.solar.dashka.domain.usecase

import com.solar.dashka.domain.repository.TtsRepository
import javax.inject.Inject

/** Stops any active TTS playback. Idempotent. */
class StopTtsUseCase @Inject constructor(
    private val repository: TtsRepository,
) {
    operator fun invoke() = repository.stop()
}
