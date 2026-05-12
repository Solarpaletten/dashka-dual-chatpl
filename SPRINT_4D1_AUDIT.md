# 🛰️ DASHKA TRANSLATE LITE — Baseline Audit

**Sprint:** 4D.1 — Repository Re-Alignment + Lite Current State Audit
**Performed by:** Claude (Engineer AI)
**Snapshot:** `DashkaAndroid.zip` (extracted 11 May 2026 evening)
**Mode:** READ-ONLY — no files generated, no code proposed, no changes
**Production version:** v0.4.6-history-dedup (running on Samsung S921B per yesterday's screenshots)
**Companion backend:** `dashka-dual-chatpl-api` (audited earlier today separately)

---

## 1. STACK CONFIRMED

```
Project:          DashkaAndroid (root module: :app)
Package:          com.solar.dashka
applicationId:    com.solar.dashka
Build system:     Gradle 9.0, AGP 8.13.0, Version Catalog
Language:         Kotlin 2.1.20
JVM target:       17
UI:               Jetpack Compose + Material3
DI:               Hilt 2.57 + KSP
Networking:       Retrofit 3.0 + OkHttp 5.3 + kotlinx.serialization 1.8.1
Persistence:      DataStore Preferences 1.1.7 (NO Room, NO SQLite)
Coroutines:       1.10.2
compileSdk:       36
targetSdk:        36
minSdk:           26                 (Android 8.0+, ~98% device coverage)
versionCode:      20
versionName:      0.4.6-history-dedup ← in production right now
```

**Architectural style:** Clean Architecture (data / domain / presentation layers),
MVI pattern in presentation (Intent → ViewModel → State), single-Activity Compose app.

---

## 2. BUILD ARTIFACTS — BuildConfig fields

These three constants are baked into the APK at build time and read at runtime:

```
DASHKA_BASE_URL   = "https://dashka-chatpl.vercel.app/"   ← backend
DASHKA_API_TOKEN  = "" (or whatever is set via gradle.properties / -P flag)
PARTNER_LANG      = "PL"                                   ← Sprint 1 decision
```

`PARTNER_LANG` is the **single-source-of-truth** for which non-Russian language
the app pairs with. It mirrors the web `config.ts` `PARTNER_LANG`. Changing
this one line + recompile gives you Russian↔German, Russian↔English etc.

---

## 3. ROUTES MAP — what the app actually displays

This app has **exactly one screen**: `TranslatorScreen`.

```
MainActivity
  └── DashkaTheme
       └── TranslatorScreen   (598 lines, single Composable host)
            ├── TopAppBar           — title "Dashka", Polski ↔ Русский,
            │                         Auto-TTS switch, voice picker, history button
            ├── Direction row       — flag PL/RU, "PL → RU" / "RU → PL" label,
            │                         swap icon (⇄ SwapHoriz)
            ├── Input pane card     — language flag header, copy/paste/clear,
            │                         OutlinedTextField, char counter (N/5000),
            │                         mic button (bottom-right inside card)
            ├── Translate row       — large orange "→ Русский" / "→ Польский" button
            │                         + secondary mic button outside
            ├── Output pane card    — language flag header, copy/share/play-TTS,
            │                         translated text
            └── HistoryBottomSheet  — modal sheet, opens on history icon tap
```

No `/privacy`, no `/terms`, no settings screen, no onboarding. Single screen
is intentional per Sprint 1 Decision 3 (mobile UX = single active pane).

---

## 4. PERMISSIONS (AndroidManifest.xml)

```
✓ INTERNET                   — required for backend translate + TTS
✓ ACCESS_NETWORK_STATE       — for online/offline detection
✓ RECORD_AUDIO               — STT via SpeechRecognizer
✓ <queries> RecognitionService — Android 11+ visibility for STT service
```

**Note:** README still says "Sprint 1: only network. Sprint 2A adds RECORD_AUDIO" —
the README is stale relative to current code. `RECORD_AUDIO` is present
(post-Sprint 2A). README-vs-code drift noted but not blocking.

---

## 5. FEATURE INVENTORY — where each capability lives

### 5.1 Microphone (Speech-to-Text)

| File | Role |
|------|------|
| `data/speech/SpeechRecognitionRepositoryImpl.kt` (449 lines) | **The heart of Conversation Mode.** Wraps `android.speech.SpeechRecognizer`. Silently restarts recognizer on NO_MATCH / TIMEOUT / CLIENT / BUSY / SERVER / UNKNOWN. Surfaces only truly fatal errors (NETWORK, AUDIO, INSUFFICIENT_PERMISSIONS, language unavailable, or 50+ consecutive silent errors). Emits Partial + SilenceDetected + Final events. |
| `data/speech/SpeechRecognitionResult.kt` | Sealed result hierarchy: ReadyForSpeech / BeginningOfSpeech / Partial / SilenceDetected / Final / Error |
| `domain/repository/SpeechRecognitionRepository.kt` | Interface boundary |
| `domain/usecase/StartRecognitionUseCase.kt` | Pure delegation wrapper |
| `domain/usecase/StopRecognitionUseCase.kt` | Pure delegation wrapper |
| `domain/model/MicState.kt` | UI states: Idle / RequestingPermission / Listening / etc. |
| `presentation/translator/components/MicButton.kt` | Compose UI component |

**Engineering quality observation:** This is **production-grade STT integration**.
The header docstring explicitly documents the design invariant — sessions end
exactly once per turn, on user tap OR genuinely fatal error OR 50+ consecutive
silent errors. Silence is **never** an end-of-session signal. Tuned for
"50-minute YouTube news streams" and "live interpreter mode for meetings".

### 5.2 Translation

| File | Role |
|------|------|
| `data/repository/TranslationRepositoryImpl.kt` | Wraps Retrofit `DashkaApi`. Maps HTTP/IO exceptions → DashkaResult. |
| `data/api/DashkaApi.kt` | Retrofit interface: `POST /api/translate`, `POST /api/tts`, `GET /api/health` |
| `data/api/DashkaTokenInterceptor.kt` | REC-001 — injects `X-Dashka-Token` header from `BuildConfig.DASHKA_API_TOKEN` |
| `data/api/dto/TranslateRequest.kt` | `{text, source_language, target_language}` |
| `data/api/dto/TranslateResponse.kt` | `{translated_text}` |
| `data/api/dto/ErrorEnvelope.kt` | Backend error shape |
| `domain/usecase/TranslateUseCase.kt` | Validates input, delegates to repository |

**Backend dependency:** every translate call hits `https://dashka-chatpl.vercel.app/api/translate`.
That is the `dashka-dual-chatpl-api` Next.js backend audited earlier today.

### 5.3 Text-to-Speech (TTS)

| File | Role |
|------|------|
| `data/tts/TtsRepositoryImpl.kt` | Orchestrates: cache lookup → backend `/api/tts` → file → AudioPlayer |
| `data/tts/TtsCache.kt` | LRU cache of MP3 byte arrays keyed by (text, voice, lang) |
| `data/tts/AudioPlayerWrapper.kt` | Wraps MediaPlayer (no ExoPlayer yet — defer to later sprint if needed) |
| `domain/usecase/PlayTtsUseCase.kt` | Public entry point |
| `domain/usecase/StopTtsUseCase.kt` | Stop playback |
| `domain/model/TtsState.kt` | UI states: Idle / Loading / Playing / Error |
| `domain/model/TtsVoice.kt` | Enum: EVE, ARA, LEO, REX, SAL |
| `presentation/translator/components/PlayTtsButton.kt` | Compose UI |
| `presentation/translator/components/VoicePickerMenu.kt` | Compose dropdown |

### 5.4 History (the v0.4.6 named feature)

| File | Role |
|------|------|
| `data/history/HistoryStorage.kt` | DataStore Preferences JSON blob; **dedup-on-save**; rolling cap 30; schema versioning |
| `data/history/HistoryRepository.kt` | Domain-layer wrapper over storage |
| `data/history/HistoryEntry.kt` | `@Serializable` data class: id (UUID) + timestampMillis + sourceText + translatedText + sourceLang + targetLang + voice |
| `data/history/HistorySnapshot.kt` | Versioned container — `schemaVersion` + `entries[]`, ready for future migrations |
| `presentation/history/HistoryBottomSheet.kt` | Modal Compose sheet |
| `presentation/history/HistoryViewModel.kt` | State + intents for history view |
| `presentation/history/components/HistoryEntryCard.kt` | Single entry card UI |

**Sprint 4C.7A dedup logic** lives in `HistoryStorage.save()`:

> If the most recent entry has identical `sourceText` AND identical
> `translatedText`, the new save is a no-op. Prevents the
> "tap → Russian → translate, then tap red mic stop → fires Final → save again"
> double-save pattern.

Cap **30 entries**. Per Дашкин direction: "recent conversation memory,
not enterprise archive". Pro tier `AdvancedHistory` will lift this.

### 5.5 User Preferences

| File | Role |
|------|------|
| `data/preferences/UserPreferencesRepository.kt` | DataStore Preferences for: selected voice (persistent), autoplay toggle (persistent) |

Separate DataStore file from history — intentional. Corrupted history blob
cannot take prefs down with it.

### 5.6 Share

| File | Role |
|------|------|
| `data/share/ShareFileBuilder.kt` | Build content:// URIs for sharing MP3 + transcript text |
| `domain/model/ShareMode.kt` | Enum: text-only / audio-only / combo |
| `presentation/translator/components/SharePopoverMenu.kt` | Compose popover |
| `res/xml/file_paths.xml` | FileProvider paths |
| `AndroidManifest.xml` provider | `${applicationId}.fileprovider` |

### 5.7 Feature Flags / Tier System (NEW — Sprint 4C infrastructure)

| File | Role |
|------|------|
| `core/featureflags/Tier.kt` | Enum: LITE < PRO < PRO_PLUS, with `canAccess()` |
| `core/featureflags/FeatureGate.kt` | Sealed interface — 21 feature gates declared |
| `core/featureflags/FeatureFlagsRepository.kt` | Maps gates to required tier; v1.0 hardcoded `currentTier = LITE` |
| `core/featureflags/HasFeature.kt` | Compose helper (`HasFeature(gate) { ... }`) |

**Currently in code (declared, not yet user-visible):**

| Gate | Tier | Status in v0.4.6 |
|------|------|------------------|
| LiveTranslation | LITE | active |
| TtsPlayback | LITE | active |
| Autoplay | LITE | active |
| VoicePersonas | LITE | active |
| ShareText | LITE | active |
| ShareCombo | LITE | active |
| HistoryLight | LITE | active |
| DocumentTranslation | PRO | declared, gate exists, no UI yet |
| PdfTranslation | PRO | declared, no UI |
| WordTranslation | PRO | declared, no UI |
| PhotoTranslation | PRO | declared, no UI |
| AdvancedHistory | PRO | declared, no UI |
| CloudSync | PRO | declared, no UI |
| AiMemory | PRO | declared, no UI |
| SubtitleExport | PRO | declared, no UI |
| BusinessMode | PRO | declared, no UI |
| UnlimitedStorage | PRO | declared, no UI |
| PremiumVoices | PRO | declared, no UI |
| SaveToDownloads | PRO | declared, no UI |
| VideoTranslation | PRO_PLUS | declared, no UI |

**Architectural significance:** the Lite vs Pro vs Pro+ split is **already
wired into the architecture**. v1.0 ships with all Pro features hidden but
the gates exist. Sprint 4G will swap `currentTier = LITE` for a billing-driven
check (Google Play Billing subscription). No widespread code changes needed
when that happens — UI sites already use `flags.isEnabled(gate)`.

---

## 6. CONFIGURATION FILES

```
build.gradle.kts (root)           — Plugin declarations only, never apply
app/build.gradle.kts              — Full app config (above)
gradle/libs.versions.toml         — Version Catalog (single source of dep versions)
gradle.properties                 — JVM args + DASHKA_API_TOKEN slot
settings.gradle.kts               — include(":app"), repository declarations
proguard-rules.pro                — Empty placeholder (R8 disabled in current builds)
.gitignore                        — Standard Android (build/, .idea/, .gradle/, local.properties)
```

---

## 7. UI ENTRY POINT

```
DashkaApplication.kt          @HiltAndroidApp  ← Hilt root
MainActivity.kt               @AndroidEntryPoint, enableEdgeToEdge, hosts TranslatorScreen
```

Edge-to-edge enabled — modern Android UI extending under system bars.

---

## 8. STABILITY MAP

### Stable / shipping in v0.4.6
- ✓ Single-pane translation, manual swap via SwapHoriz button
- ✓ Mic input with continuous Conversation Mode (50-min sessions tested)
- ✓ Silence detection that drives incremental translation
- ✓ TTS playback with 5 voices
- ✓ Auto-TTS toggle (persistent across restarts)
- ✓ Selected voice persistence
- ✓ Share (text-only / audio / combo)
- ✓ History light (30 entries, dedup)
- ✓ Material3 theming
- ✓ Edge-to-edge layout
- ✓ Backend authentication via `X-Dashka-Token`
- ✓ Backend pinned to `dashka-chatpl.vercel.app`

### Architecturally present but not user-visible (Pro tier, gated off)
- · DocumentTranslation, PdfTranslation, WordTranslation, PhotoTranslation
- · AdvancedHistory, CloudSync, AiMemory
- · SubtitleExport, BusinessMode, UnlimitedStorage
- · PremiumVoices, SaveToDownloads
- · VideoTranslation (Pro+)

These have **no UI affordances yet** — just `FeatureGate` declarations and
`FeatureFlagsRepository.isEnabled()` returning false. **No code paths invoke
them.** Adding them is a forward-compatible task per gate.

### Parked / mentioned in transcript history but not in current code
- Sprint 4C.7B (composition field / TextFieldValue migration) — referenced
  in earlier transcript as "parked for engineering session"; no current
  branch / TODO in code
- Conversation Mode landscape split-screen — Pro feature, not in code

### NOT present in this codebase
- · Tests (no `src/test/`, no `src/androidTest/`, no test scripts in Gradle)
- · CI/CD (no `.github/workflows/`)
- · R8 / ProGuard (disabled, placeholder rules file)
- · Crash reporting (no Crashlytics, no Sentry)
- · Analytics
- · Settings screen
- · Multi-partner-language switch UI (PARTNER_LANG is a build constant, not
  user-toggleable)
- · `.env.example` or equivalent dev-onboarding doc beyond README
- · Lint baseline file

---

## 9. SIZE METRICS

```
Active source files (Kotlin):   56
Total active source lines:      ~3,400 (Kotlin only, rough)
Largest files:
  TranslatorViewModel.kt        786 lines  ← orchestrator
  TranslatorScreen.kt           598 lines  ← UI
  SpeechRecognitionRepositoryImpl.kt  449 lines  ← STT
  HistoryStorage.kt             ~165 lines
  (others all < 150 lines)
Resources:                      6 res files (strings, themes, colors, launcher icons)
Manifest:                       1
```

Compact, well-organized. Three large files concentrate complexity; rest is
small focused units.

---

## 10. OBSERVATIONS (NOT sprint proposals — only Coordinator opens sprints)

These are observations from this read-only pass. Each is flagged with risk
level. **None of these are sprint recommendations.** Only Dashka decides what
becomes a sprint.

| # | Observation | Risk |
|---|-------------|------|
| 1 | README is from Sprint 1 baseline — predates Sprint 2 (STT), Sprint 4 (history). Anyone onboarding reads "Sprint 1: text-only translation" but actually finds full Conversation Mode. | LOW (cosmetic) |
| 2 | No tests at all (no unit, no instrumented). The state machine in `SpeechRecognitionRepositoryImpl` has subtle invariants worth pinning. | MEDIUM (long-term) |
| 3 | `proguard-rules.pro` is empty; R8/ProGuard disabled. Release builds ship un-shrunk. Larger APK + reverse-engineerable. | LOW now, becomes MEDIUM at Play Store launch |
| 4 | No crash reporting. Production crashes will only be visible via Play Console's built-in ANR/crash reports (after store launch). | LOW now |
| 5 | History is 30-entry DataStore Preferences JSON. Fine for Lite. AdvancedHistory (Pro) will need Room or remote. Migration path is documented in `HistoryStorage` header. | LOW (architecture acknowledges it) |
| 6 | `DashkaAndroid_full/` directory in zip is an **older snapshot** (no `core/featureflags/`, no `data/history/`, has old `ShareBottomSheet` instead of `SharePopoverMenu`). `settings.gradle.kts` only includes `:app`, so it's ignored by Gradle — but it bloats the zip and could confuse next person. | LOW (housekeeping) |
| 7 | `BuildConfig.DASHKA_API_TOKEN` defaults to empty string. With backend's auth enabled, empty token → 401 on every request. Onboarding fragility unless the dev knows to set the gradle property. | LOW–MEDIUM |
| 8 | `package-lock.json` AND `pnpm-lock.yaml` both present in companion *backend* repo (audited earlier today). Not a concern for this Android repo. | N/A here |
| 9 | `versionCode = 20` for `versionName = 0.4.6-history-dedup` — suggests ~20 internal builds since v1. Healthy iteration cadence. | informational |
| 10 | TTS uses `MediaPlayer`. For more advanced playback (seeking, gapless, multiple concurrent), would need ExoPlayer/Media3. Not blocking. | LOW |
| 11 | `Direction.kt` is hardcoded `RU_TO_PARTNER / PARTNER_TO_RU` — Russian is anchored. Adding a non-Russian-paired mode (e.g. PL↔EN) would require domain model change. | LOW (Lite scope is Russian-anchored intentionally) |
| 12 | TranslatorViewModel is 786 lines — large. Holds STT orchestration + translate orchestration + TTS orchestration + history saving + UI state. Could be split (TranslatorViewModel → STT mediator + Translate mediator + TTS mediator) for testability. Не сейчас. | LOW (organic split candidate after Pro features arrive) |
| 13 | All Pro `FeatureGate` declarations exist with no UI — confirms architectural commitment to tiered product *without* leaking Pro UI in Lite builds. Clean. | (positive observation) |
| 14 | Backend URL is hardcoded in `app/build.gradle.kts`. Changing backend requires rebuild + reinstall. No remote config. Per Lite scope, fine. | LOW (correctly scoped) |

---

## 11. RELATIONSHIP TO COMPANION BACKEND

```
DashkaAndroid (this repo)              dashka-dual-chatpl-api (audited earlier)
  └── DashkaApi (Retrofit) ───────────► POST /api/translate
  └── DashkaApi (Retrofit) ───────────► POST /api/tts
  └── DashkaApi (Retrofit) ───────────► GET  /api/health
                                          │
                                          ├─► OpenAI (GPT-4.1-mini) for translate
                                          └─► Grok / xAI for TTS audio synthesis
```

**Two-repo architecture** with clean HTTPS boundary. Sprint can target either
repo without touching the other. Audit baselines exist for both as of today.

---

## 12. WHAT THIS AUDIT IS NOT

- ❌ Not a feature proposal
- ❌ Not a sprint recommendation
- ❌ Not a code change
- ❌ Not a Pro v1.0 spec
- ❌ Not a refactor blueprint
- ❌ Not a critique
- ❌ Not a UX change suggestion

This is **the baseline reference document** for future Lite sprints. Every
sprint says "per audit 4D.1, file X at path Y does Z — this sprint touches
only W within scope."

---

## 13. AUDIT VERDICT

```
═══════════════════════════════════════════════════════════════
DASHKA TRANSLATE LITE — v0.4.6-history-dedup
═══════════════════════════════════════════════════════════════

State:           Production-stable, ready for verification call tomorrow
Stack:           Modern (Kotlin 2.1, Compose, Hilt, Retrofit 3)
Architecture:    Clean Architecture, well-separated layers
Code quality:    High — production-grade STT lifecycle handling,
                 explicit invariants documented in code
Backend:         Stable, separate repo, auth in place
Tier system:     Wired in architecturally; Lite scope fully realized;
                 Pro gates declared but inert (correct for v1.0)
Tests:           Missing (largest gap)
Production:      Untouched by today's work — yesterday's screenshots
                 show BUILD SUCCESSFUL on the same codebase

═══════════════════════════════════════════════════════════════
```

🛰️ Audit complete. Standby for sprint TЗ from Coordinator (Dashka).
