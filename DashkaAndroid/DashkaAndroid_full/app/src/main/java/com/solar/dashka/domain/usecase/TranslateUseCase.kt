package com.solar.dashka.domain.usecase

import com.solar.dashka.data.api.DashkaResult
import com.solar.dashka.domain.model.LangCode
import com.solar.dashka.domain.model.TranslationResult
import com.solar.dashka.domain.repository.TranslationRepository
import javax.inject.Inject

/**
 * Single translation operation. Sprint 1 mostly delegates to the repository
 * but keeps client-side validation (empty/long text) here so the ViewModel
 * stays UI-only.
 *
 * Backend post-REC-003 also enforces these limits, but failing fast on the
 * client avoids a network round trip.
 */
class TranslateUseCase @Inject constructor(
    private val repository: TranslationRepository,
) {
    suspend operator fun invoke(
        text: String,
        source: LangCode?,
        target: LangCode,
    ): DashkaResult<TranslationResult> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return DashkaResult.Error.Server(code = 400, message = "Текст не указан")
        }
        if (trimmed.length > MAX_TEXT_LENGTH) {
            return DashkaResult.Error.Server(
                code = 400,
                message = "Слишком длинный текст (максимум $MAX_TEXT_LENGTH символов)",
            )
        }
        return repository.translate(trimmed, source, target)
    }

    companion object {
        // Mirrors backend MAX_TEXT_LENGTH (REC-003).
        const val MAX_TEXT_LENGTH = 5000
    }
}
