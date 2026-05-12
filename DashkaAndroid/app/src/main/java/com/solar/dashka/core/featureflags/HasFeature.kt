package com.solar.dashka.core.featureflags

import androidx.compose.runtime.Composable

/**
 * Sprint 4C — Convenience wrapper for gating UI elements behind feature flags.
 *
 * Usage:
 *
 *   HasFeature(FeatureGate.DocumentTranslation, flags) {
 *       DocumentButton(onClick = ...)
 *   }
 *
 * If the gate is disabled, the content is simply not rendered. For Pro
 * features that should show a "locked" placeholder with upgrade prompt,
 * use [HasFeatureOrUpgrade] instead.
 */
@Composable
fun HasFeature(
    gate: FeatureGate,
    flags: FeatureFlagsRepository,
    content: @Composable () -> Unit,
) {
    if (flags.isEnabled(gate)) {
        content()
    }
}

/**
 * Sprint 4C — Renders [content] if gate is enabled, else [locked] (e.g., a
 * disabled-looking button with a 🔒 badge that opens the paywall).
 *
 * In v1.0 [locked] is unused (all Pro features simply hidden), but the helper
 * is here for Sprint 4G+ when we add upgrade CTAs.
 */
@Composable
fun HasFeatureOrUpgrade(
    gate: FeatureGate,
    flags: FeatureFlagsRepository,
    locked: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    if (flags.isEnabled(gate)) {
        content()
    } else {
        locked()
    }
}
