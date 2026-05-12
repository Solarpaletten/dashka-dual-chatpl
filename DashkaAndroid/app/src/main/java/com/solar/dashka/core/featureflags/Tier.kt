package com.solar.dashka.core.featureflags

/**
 * Sprint 4C — App tier hierarchy.
 *
 * Currently only LITE exists in v1.0. Pro will be unlocked via Google Play
 * Billing subscription in Sprint 4G.
 *
 * Hierarchy: LITE < PRO < PRO_PLUS — higher tier unlocks all lower tiers.
 */
enum class Tier {
    /** Default — bundled with free app install. */
    LITE,

    /** Subscription-unlocked. Document/Photo translation, advanced history. */
    PRO,

    /** Future. Video translation, voice cloning, enterprise features. */
    PRO_PLUS;

    /** True if this tier can access [other] tier's features. */
    fun canAccess(other: Tier): Boolean = this.ordinal >= other.ordinal
}
