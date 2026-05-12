package com.solar.dashka.domain.repository

import com.solar.dashka.data.api.DashkaResult
import com.solar.dashka.domain.model.LangCode
import com.solar.dashka.domain.model.TranslationResult

/**
 * Domain abstraction over the translation API. The presentation layer never
 * calls Retrofit directly — it goes through this interface.
 *
 * Sprint 1: the only operation. Health check is wired separately via
 * DashkaApi for app-startup connectivity.
 */
interface TranslationRepository {

    suspend fun translate(
        text: String,
        source: LangCode?,
        target: LangCode,
    ): DashkaResult<TranslationResult>
}
