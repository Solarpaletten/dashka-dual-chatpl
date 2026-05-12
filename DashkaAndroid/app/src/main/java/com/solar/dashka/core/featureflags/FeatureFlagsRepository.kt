package com.solar.dashka.core.featureflags

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 4C — Single source of truth for feature availability.
 *
 * v1.0 (Lite): hardcoded Lite-tier policy. All Lite features ON, all Pro
 * features OFF.
 *
 * Sprint 4G (planned): inject BillingRepository — currentTier becomes
 * billing-driven (PRO unlocks when subscription is active).
 *
 * Future: inject RemoteConfigRepository for A/B testing and promotional
 * unlocks (e.g., "free Pro week" campaigns).
 *
 * Important: UI should NEVER call `tier ==` directly. Always go through
 * `isEnabled(gate)` so future changes (caching, remote overrides, debug
 * toggles) are transparent to callers.
 */
@Singleton
class FeatureFlagsRepository @Inject constructor() {

    /**
     * Sprint 4C: hardcoded LITE tier.
     * Sprint 4G: replace with billing-driven check.
     */
    private val currentTier: Tier = Tier.LITE

    /**
     * Returns true if the user has access to this feature.
     *
     * Delegates to [tierFor] mapping — single place to add new gates.
     */
    fun isEnabled(gate: FeatureGate): Boolean {
        val requiredTier = tierFor(gate)
        return currentTier.canAccess(requiredTier)
    }

    /**
     * Maps each FeatureGate to its minimum required tier.
     *
     * When adding a new gate, add it here. Compiler will warn via exhaustive
     * `when` (sealed interface guarantees this) — that's the safety net.
     */
    private fun tierFor(gate: FeatureGate): Tier = when (gate) {
        // Lite tier — always available
        FeatureGate.LiveTranslation,
        FeatureGate.TtsPlayback,
        FeatureGate.Autoplay,
        FeatureGate.VoicePersonas,
        FeatureGate.ShareText,
        FeatureGate.ShareCombo,
        FeatureGate.HistoryLight -> Tier.LITE

        // Pro tier — subscription required
        FeatureGate.DocumentTranslation,
        FeatureGate.PdfTranslation,
        FeatureGate.WordTranslation,
        FeatureGate.PhotoTranslation,
        FeatureGate.AdvancedHistory,
        FeatureGate.CloudSync,
        FeatureGate.AiMemory,
        FeatureGate.SubtitleExport,
        FeatureGate.BusinessMode,
        FeatureGate.UnlimitedStorage,
        FeatureGate.PremiumVoices,
        FeatureGate.SaveToDownloads -> Tier.PRO

        // Pro+ tier — future, voice cloning / video
        FeatureGate.VideoTranslation -> Tier.PRO_PLUS
    }
}
