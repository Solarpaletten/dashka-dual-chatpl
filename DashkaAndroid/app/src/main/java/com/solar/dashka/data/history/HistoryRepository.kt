package com.solar.dashka.data.history

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sprint 4C — Domain-layer wrapper around [HistoryStorage].
 *
 * Why this layer exists: VM should not depend on storage implementation
 * directly. When Sprint 4G+ swaps to Room or remote backend, we replace
 * the [HistoryStorage] reference here and ViewModels stay untouched.
 *
 * The repository also flattens the snapshot's `entries` list into a Flow,
 * so callers don't need to think about schema versions.
 */
@Singleton
class HistoryRepository @Inject constructor(
    private val storage: HistoryStorage,
) {
    /** Live list of entries, newest first. Capped at [HistoryStorage.MAX_ENTRIES]. */
    val entriesFlow: Flow<List<HistoryEntry>> =
        storage.snapshotFlow.map { it.entries }

    /** Append [entry]. Rolling cap is enforced by [HistoryStorage]. */
    suspend fun save(entry: HistoryEntry) = storage.save(entry)

    /** Remove a single entry by id. */
    suspend fun delete(id: String) = storage.delete(id)
}
