package com.solar.dashka.data.history

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 4C — Local persistence of [HistorySnapshot] as JSON in DataStore
 * Preferences.
 *
 * Why DataStore Preferences (not Room): for a 30-entry capped list, single
 * JSON blob is simpler — atomic reads/writes, no migrations, no DAO. When
 * Sprint 4G+ moves to advanced history (Pro tier), we'll likely migrate to
 * Room or remote backend; the [HistoryEntry] DTO stays the same so it's a
 * data-source swap, not a schema rewrite.
 *
 * Why a separate DataStore (not [UserPreferencesRepository]'s store): history
 * is bigger and has different lifecycle than UI prefs. Keeping them separate
 * means a corrupted history blob can't take prefs down with it.
 *
 * Rolling-30 policy: when [save] would exceed [MAX_ENTRIES], the oldest is
 * dropped. Per Дашкин UX direction — Lite is "recent conversation memory",
 * not enterprise archive.
 *
 * Schema migration: [HistorySnapshot.schemaVersion] is read on every load.
 * Currently only v1 exists. Future versions will add a `when (snapshot
 * .schemaVersion)` branch in [parse] to upgrade payloads transparently.
 */
@Singleton
class HistoryStorage @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val Context.historyDataStore by preferencesDataStore(name = DATASTORE_NAME)

    private val json = Json {
        ignoreUnknownKeys = true   // forward-compat with future Pro fields
        encodeDefaults = true       // always write schemaVersion explicitly
    }

    /**
     * Reactive snapshot of the entire history. Emits a new value on every
     * change. UI layer collects this for live updates (e.g., HistoryBottomSheet).
     *
     * On corruption (malformed JSON, schema downgrade), emits an empty
     * snapshot rather than crashing — better UX than a black screen.
     */
    val snapshotFlow: Flow<HistorySnapshot> = context.historyDataStore.data
        .map { prefs ->
            prefs[KEY_HISTORY_JSON]
                ?.let { runCatching { parse(it) }.getOrNull() }
                ?: HistorySnapshot()
        }
        .catch { emit(HistorySnapshot()) }

    /**
     * Append a new entry to the front (newest first) and drop oldest if
     * over [MAX_ENTRIES].
     *
     * Atomic on DataStore level — concurrent saves serialize via DataStore's
     * internal queue. No explicit mutex needed.
     *
     * Sprint 4C.7A: deduplication. Skip the save if the most recent entry
     * has identical sourceText AND translatedText. This prevents duplicates
     * from the common workflow:
     *   - User taps orange "→ Польский" → save #1
     *   - User taps red mic stop → fires Final → would save #2 (DUPLICATE)
     *
     * Also handles edge cases like accidental double-tap on translate.
     * The duplicate is silently ignored — the original entry stays at the
     * top so the user still sees their conversation.
     */
    suspend fun save(entry: HistoryEntry) {
        context.historyDataStore.edit { prefs ->
            val current = prefs[KEY_HISTORY_JSON]?.let {
                runCatching { parse(it) }.getOrNull()
            } ?: HistorySnapshot()

            // Sprint 4C.7A dedup check: same text content as latest? Skip.
            val latest = current.entries.firstOrNull()
            if (latest != null &&
                latest.sourceText == entry.sourceText &&
                latest.translatedText == entry.translatedText
            ) {
                return@edit  // identical to most recent — no-op
            }

            val updated = HistorySnapshot(
                schemaVersion = HistorySnapshot.CURRENT_SCHEMA_VERSION,
                entries = (listOf(entry) + current.entries).take(MAX_ENTRIES),
            )
            prefs[KEY_HISTORY_JSON] = json.encodeToString(
                HistorySnapshot.serializer(),
                updated,
            )
        }
    }

    /** Remove a single entry by [id]. No-op if not found. */
    suspend fun delete(id: String) {
        context.historyDataStore.edit { prefs ->
            val current = prefs[KEY_HISTORY_JSON]?.let {
                runCatching { parse(it) }.getOrNull()
            } ?: return@edit

            val filtered = current.entries.filterNot { it.id == id }
            if (filtered.size == current.entries.size) return@edit  // not found

            val updated = current.copy(entries = filtered)
            prefs[KEY_HISTORY_JSON] = json.encodeToString(
                HistorySnapshot.serializer(),
                updated,
            )
        }
    }

    /**
     * Parse the stored JSON string into a [HistorySnapshot], applying
     * schema migrations if needed.
     *
     * v1 → v1: identity.
     * Future v1 → v2: implement migration here.
     */
    private fun parse(raw: String): HistorySnapshot {
        val snapshot = try {
            json.decodeFromString(HistorySnapshot.serializer(), raw)
        } catch (e: SerializationException) {
            return HistorySnapshot()  // corrupted — start fresh
        }

        return when (snapshot.schemaVersion) {
            1 -> snapshot
            // Future: 2 -> migrateV1ToV2(snapshot)
            else -> {
                // Unknown version (forward-compat from older app on newer data)
                // — drop to empty rather than risk inconsistency.
                HistorySnapshot()
            }
        }
    }

    companion object {
        /**
         * History Light cap. Per Дашкин direction: "recent conversation
         * memory, not enterprise archive". Increased only in AdvancedHistory
         * Pro feature.
         */
        const val MAX_ENTRIES = 30

        private const val DATASTORE_NAME = "dashka_history"
        private val KEY_HISTORY_JSON = stringPreferencesKey("history_snapshot_json")
    }
}
