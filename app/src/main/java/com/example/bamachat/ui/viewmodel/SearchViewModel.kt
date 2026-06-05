package com.example.bamachat.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bamachat.data.local.ChatDatabase
import com.example.bamachat.data.local.MessageFtsResult
import com.example.bamachat.data.repository.ChatRepository
import com.example.bamachat.util.AppTelemetry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchFilter(
    val isUserMessagesOnly: Boolean = false,
    val isAiMessagesOnly: Boolean = false,
    val dateRangeStartMs: Long? = null,
    val dateRangeEndMs: Long? = null,
    val sortBy: SearchSortBy = SearchSortBy.RECENT
)

enum class SearchSortBy {
    RECENT, OLDEST, RELEVANCE
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {
    private val repo = ChatRepository(ChatDatabase.getDatabase(application).chatDao())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _results = MutableStateFlow<List<MessageFtsResult>>(emptyList())
    val results: StateFlow<List<MessageFtsResult>> = _results

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _hasSearched = MutableStateFlow(false)
    val hasSearched: StateFlow<Boolean> = _hasSearched

    private val _selectedFilter = MutableStateFlow(SearchFilter())
    val selectedFilter: StateFlow<SearchFilter> = _selectedFilter

    private val _totalResultsCount = MutableStateFlow(0)
    val totalResultsCount: StateFlow<Int> = _totalResultsCount

    private val _highlightedResultId = MutableStateFlow<Long?>(null)
    val highlightedResultId: StateFlow<Long?> = _highlightedResultId

    private var searchJob: Job? = null

    fun setQuery(newQuery: String) {
        _query.value = newQuery
        performSearch(newQuery, _selectedFilter.value)
    }

    fun setFilter(filter: SearchFilter) {
        _selectedFilter.value = filter
        performSearch(_query.value, filter)
    }

    fun setUserMessagesOnly(only: Boolean) {
        val newFilter = _selectedFilter.value.copy(
            isUserMessagesOnly = only,
            isAiMessagesOnly = if (only) false else _selectedFilter.value.isAiMessagesOnly
        )
        setFilter(newFilter)
    }

    fun setAiMessagesOnly(only: Boolean) {
        val newFilter = _selectedFilter.value.copy(
            isAiMessagesOnly = only,
            isUserMessagesOnly = if (only) false else _selectedFilter.value.isUserMessagesOnly
        )
        setFilter(newFilter)
    }

    fun setSortBy(sortBy: SearchSortBy) {
        setFilter(_selectedFilter.value.copy(sortBy = sortBy))
    }

    fun highlightResult(resultId: Long) {
        _highlightedResultId.value = resultId
    }

    fun clearHighlight() {
        _highlightedResultId.value = null
    }

    fun clearSearch() {
        _query.value = ""
        _results.value = emptyList()
        _hasSearched.value = false
        _totalResultsCount.value = 0
        searchJob?.cancel()
    }

    private fun performSearch(q: String, filter: SearchFilter) {
        searchJob?.cancel()
        if (q.isBlank()) {
            _results.value = emptyList()
            _hasSearched.value = false
            _totalResultsCount.value = 0
            return
        }

        _loading.value = true
        searchJob = viewModelScope.launch {
            delay(250) // Debounce
            try {
                var results = repo.searchMessages(q, limit = 50)

                // Apply filters
                results = results.filter { result ->
                    val matchesType = when {
                        filter.isUserMessagesOnly -> result.is_user
                        filter.isAiMessagesOnly -> !result.is_user
                        else -> true
                    }

                    val matchesDateRange = when {
                        filter.dateRangeStartMs != null && filter.dateRangeEndMs != null ->
                            result.timestamp >= filter.dateRangeStartMs && result.timestamp <= filter.dateRangeEndMs
                        else -> true
                    }

                    matchesType && matchesDateRange
                }

                // Sort
                results = when (filter.sortBy) {
                    SearchSortBy.RECENT -> results.sortedByDescending { it.timestamp }
                    SearchSortBy.OLDEST -> results.sortedBy { it.timestamp }
                    SearchSortBy.RELEVANCE -> results // Already sorted by FTS relevance
                }

                _results.value = results
                _totalResultsCount.value = results.size
                _hasSearched.value = true

                AppTelemetry.logEvent("search_performed", mapOf(
                    "query" to q,
                    "results_count" to results.size.toString(),
                    "filter_user_only" to filter.isUserMessagesOnly.toString(),
                    "filter_ai_only" to filter.isAiMessagesOnly.toString()
                ))
            } catch (e: Exception) {
                AppTelemetry.logError("search_error", e)
                _results.value = emptyList()
                _hasSearched.value = true
            } finally {
                _loading.value = false
            }
        }
    }
}
