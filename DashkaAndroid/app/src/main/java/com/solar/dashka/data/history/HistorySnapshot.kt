package com.solar.dashka.data.history

import kotlinx.serialization.Serializable

/**
 * Sprint 4C — Versioned wrapper around the history entries list.
 *
 * Per Дашкин direction: store schemaVersion from day 1 so future migrations
 * (cloud sync, AI memory, embeddings, summaries) don't run into "storage
 * hell". Adding fields later is fine — adding versioning later is painful.
 *
 * @property schemaVersion Bumped when migration logic is required. Migration
 *   handler in [HistoryStorage] reads this and upgrades older payloads.
 * @property entries Newest-first ordering. Maximum [HistoryStorage.MAX_ENTRIES].
 */
@Serializable
data class HistorySnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val entries: List<HistoryEntry> = emptyList(),
) {
    companion object {
        /**
         * Sprint 4C: v1 = `entries` is `List<HistoryEntry>`, newest first.
         *
         * When schema changes (e.g., adding `tags`, `pinned`, `cloudId`),
         * bump this and add a migration branch in HistoryStorage.parse().
         */
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
