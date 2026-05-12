package com.solar.dashka.core.featureflags

/**
 * Sprint 4C — Feature gate registry for tier-based feature unlocking.
 *
 * Sealed interface (NOT enum) per Дашкин direction — allows future metadata
 * extensions like:
 *   - val tier: Tier  (Lite / Pro / ProPlus)
 *   - val analyticsName: String
 *   - val remoteConfigKey: String
 *
 * without painful enum migration.
 *
 * Usage:
 *   val flags: FeatureFlagsRepository = inject()
 *   if (flags.isEnabled(FeatureGate.DocumentTranslation)) { ... }
 *
 * Or in Compose:
 *   HasFeature(FeatureGate.DocumentTranslation) { DocumentButton() }
 */
sealed interface FeatureGate {
    /** Stable string id — for analytics, remote config keys, persistence. */
    val key: String

    // ─────────────── Lite tier (always on in v1.0) ───────────────

    data object LiveTranslation : FeatureGate {
        override val key = "live_translation"
    }

    data object TtsPlayback : FeatureGate {
        override val key = "tts_playback"
    }

    data object Autoplay : FeatureGate {
        override val key = "autoplay"
    }

    data object VoicePersonas : FeatureGate {
        override val key = "voice_personas"
    }

    data object ShareText : FeatureGate {
        override val key = "share_text"
    }

    data object ShareCombo : FeatureGate {
        override val key = "share_combo"
    }

    data object HistoryLight : FeatureGate {
        override val key = "history_light"
    }

    // ─────────────── Pro tier (off in v1.0, on with subscription) ───────────────

    data object DocumentTranslation : FeatureGate {
        override val key = "document_translation"
    }

    data object PdfTranslation : FeatureGate {
        override val key = "pdf_translation"
    }

    data object WordTranslation : FeatureGate {
        override val key = "word_translation"
    }

    data object PhotoTranslation : FeatureGate {
        override val key = "photo_translation"
    }

    data object AdvancedHistory : FeatureGate {
        override val key = "advanced_history"
    }

    data object CloudSync : FeatureGate {
        override val key = "cloud_sync"
    }

    data object AiMemory : FeatureGate {
        override val key = "ai_memory"
    }

    data object VideoTranslation : FeatureGate {
        override val key = "video_translation"
    }

    data object SubtitleExport : FeatureGate {
        override val key = "subtitle_export"
    }

    data object BusinessMode : FeatureGate {
        override val key = "business_mode"
    }

    data object UnlimitedStorage : FeatureGate {
        override val key = "unlimited_storage"
    }

    data object PremiumVoices : FeatureGate {
        override val key = "premium_voices"
    }

    data object SaveToDownloads : FeatureGate {
        override val key = "save_to_downloads"
    }
}
