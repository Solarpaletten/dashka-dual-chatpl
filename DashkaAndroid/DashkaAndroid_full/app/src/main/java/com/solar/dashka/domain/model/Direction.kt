package com.solar.dashka.domain.model

/**
 * Translation direction. Mobile-only: while web has two simultaneous panes,
 * mobile has a single active pane that flips between these two directions.
 *
 * Sprint 1: hardcoded to RU ↔ PL (Decision 2). Partner language is sourced
 * from BuildConfig.PARTNER_LANG and resolved in the ViewModel.
 */
enum class Direction {
    /** User speaks Russian, output is in the partner language. */
    RU_TO_PARTNER,

    /** Partner speaks their language, output is Russian. */
    PARTNER_TO_RU;

    fun toggled(): Direction = when (this) {
        RU_TO_PARTNER -> PARTNER_TO_RU
        PARTNER_TO_RU -> RU_TO_PARTNER
    }
}
