package com.solar.dashka.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solar.dashka.data.history.HistoryEntry
import com.solar.dashka.data.history.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sprint 4C — ViewModel for the History bottom sheet.
 *
 * Owns the live entries stream and exposes [delete] for swipe-to-delete.
 *
 * Reload-into-translator flow does NOT live here — that intent is dispatched
 * to TranslatorViewModel via a callback wired in the Composable layer, so
 * the two ViewModels stay decoupled. (The History UI is presentational; the
 * Translator is the source of truth.)
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: HistoryRepository,
) : ViewModel() {

    /**
     * Entries newest-first. Backed by [HistoryRepository.entriesFlow] and
     * cold-started; collected only while sheet is visible.
     */
    val entries: StateFlow<List<HistoryEntry>> = repository.entriesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList(),
        )

    /** Swipe-to-delete handler. */
    fun delete(id: String) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }
}
