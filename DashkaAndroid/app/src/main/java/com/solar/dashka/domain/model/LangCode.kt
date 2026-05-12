package com.solar.dashka.domain.model

import kotlinx.serialization.Serializable

/**
 * Mirrors the `LangCode` type union from web `features/translator/types.ts`.
 *
 * Sprint 1 only uses RU and PL, but the full set is declared so that:
 *   - It stays in sync with the backend's ALLOWED_LANGS guard (REC-005).
 *   - Adding a new partner language later requires no enum change.
 *
 * The `code` property is the wire format sent to the backend (uppercase ISO-like).
 * `displayName` is shown in Russian UI labels.
 * `nativeName` is the language's self-name.
 * `flag` is a Unicode flag emoji (matches web LANG_META).
 * `speechLocale` is BCP-47 for Android SpeechRecognizer (used in later sprints).
 *
 * Sprint 4C: @Serializable so HistoryEntry can persist by name.
 */
@Serializable
enum class LangCode(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val flag: String,
    val speechLocale: String,
) {
    RU(code = "RU", displayName = "Русский", nativeName = "Русский", flag = "🇷🇺", speechLocale = "ru-RU"),
    DE(code = "DE", displayName = "Немецкий", nativeName = "Deutsch", flag = "🇩🇪", speechLocale = "de-DE"),
    EN(code = "EN", displayName = "English", nativeName = "English", flag = "🇺🇸", speechLocale = "en-US"),
    PL(code = "PL", displayName = "Польский", nativeName = "Polski", flag = "🇵🇱", speechLocale = "pl-PL"),
    ZH(code = "ZH", displayName = "Китайский", nativeName = "中文", flag = "🇨🇳", speechLocale = "zh-CN"),
    FR(code = "FR", displayName = "Французский", nativeName = "Français", flag = "🇫🇷", speechLocale = "fr-FR"),
    IT(code = "IT", displayName = "Итальянский", nativeName = "Italiano", flag = "🇮🇹", speechLocale = "it-IT"),
    ES(code = "ES", displayName = "Испанский", nativeName = "Español", flag = "🇪🇸", speechLocale = "es-ES"),
    LV(code = "LV", displayName = "Латышский", nativeName = "Latviešu", flag = "🇱🇻", speechLocale = "lv-LV"),
    LT(code = "LT", displayName = "Литовский", nativeName = "Lietuvių", flag = "🇱🇹", speechLocale = "lt-LT"),
    UA(code = "UA", displayName = "Украинский", nativeName = "Українська", flag = "🇺🇦", speechLocale = "uk-UA");

    companion object {
        fun fromCode(code: String): LangCode? =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }
}
