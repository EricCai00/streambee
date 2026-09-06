package com.kelsos.mbrc.feature.library.grouping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.kelsos.mbrc.core.common.settings.AlbumSortField
import com.kelsos.mbrc.core.common.settings.AlbumSortPreference
import com.kelsos.mbrc.core.common.settings.AlbumViewMode
import com.kelsos.mbrc.core.common.settings.GenreSortField
import com.kelsos.mbrc.core.common.settings.GenreSortPreference
import com.kelsos.mbrc.core.common.settings.LibrarySettings
import com.kelsos.mbrc.core.common.settings.SortOrder
import com.kelsos.mbrc.core.common.settings.SortPreference
import com.kelsos.mbrc.core.common.state.ConnectionStateFlow
import com.kelsos.mbrc.core.data.library.album.Album
import com.kelsos.mbrc.core.data.library.grouping.LanguageCategoryGroup
import com.kelsos.mbrc.core.data.library.grouping.LibraryGroupingRepository
import com.kelsos.mbrc.core.data.library.grouping.YearGroup
import com.kelsos.mbrc.feature.library.albums.BaseAlbumViewModel
import com.kelsos.mbrc.feature.library.queue.QueueHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class DecadeYearsViewModel(private val repository: LibraryGroupingRepository) : ViewModel() {
  private val decadeFilter = MutableSharedFlow<Int>(replay = 1)
  private val _sortPreference = MutableStateFlow(
    SortPreference(AlbumSortField.YEAR, SortOrder.DESC)
  )
  val sortPreference: Flow<AlbumSortPreference> = _sortPreference

  val years: Flow<PagingData<YearGroup>> =
    combine(decadeFilter, sortPreference) { decade, sort -> decade to sort.order }
      .flatMapLatest { (decade, order) -> repository.getYears(decade, order) }
      .cachedIn(viewModelScope)

  fun load(decade: Int) {
    viewModelScope.launch { decadeFilter.emit(decade) }
  }

  fun updateSortPreference(preference: AlbumSortPreference) {
    _sortPreference.value = preference.copy(field = AlbumSortField.YEAR)
  }
}

class LanguageCategoriesViewModel(private val repository: LibraryGroupingRepository) : ViewModel() {
  private val languageFilter = MutableSharedFlow<String>(replay = 1)
  private val _sortPreference = MutableStateFlow(
    SortPreference(GenreSortField.NAME, SortOrder.ASC)
  )
  val sortPreference: Flow<GenreSortPreference> = _sortPreference

  val categories: Flow<PagingData<LanguageCategoryGroup>> =
    combine(languageFilter, sortPreference) { language, sort -> language to sort.order }
      .flatMapLatest { (language, order) ->
        repository.getLanguageCategories(language, order)
      }.cachedIn(viewModelScope)

  fun load(language: String) {
    viewModelScope.launch { languageFilter.emit(language) }
  }

  fun updateSortPreference(preference: GenreSortPreference) {
    _sortPreference.value = preference.copy(field = GenreSortField.NAME)
  }
}

sealed interface GroupedAlbumFilter {
  data class Year(val year: String) : GroupedAlbumFilter
  data class LanguageCategory(val language: String, val category: String) : GroupedAlbumFilter
}

class GroupedAlbumsViewModel(
  private val repository: LibraryGroupingRepository,
  queueHandler: QueueHandler,
  private val librarySettings: LibrarySettings,
  connectionStateFlow: ConnectionStateFlow
) : BaseAlbumViewModel(queueHandler, librarySettings, connectionStateFlow) {
  private val filter = MutableSharedFlow<GroupedAlbumFilter>(replay = 1)

  val sortPreference: Flow<AlbumSortPreference> = librarySettings.albumSortPreferenceFlow
  val albumViewMode: Flow<AlbumViewMode> = librarySettings.albumViewModeFlow

  override val albums: Flow<PagingData<Album>> =
    combine(filter, sortPreference) { group, sort -> group to sort }
      .flatMapLatest { (group, sort) ->
        when (group) {
          is GroupedAlbumFilter.Year ->
            repository.getAlbumsByYear(group.year, sort.field, sort.order)

          is GroupedAlbumFilter.LanguageCategory -> repository.getAlbumsByLanguageCategory(
            group.language,
            group.category,
            sort.field,
            sort.order
          )
        }
      }.cachedIn(viewModelScope)

  fun load(group: GroupedAlbumFilter) {
    viewModelScope.launch { filter.emit(group) }
  }

  fun updateSortPreference(preference: AlbumSortPreference) {
    viewModelScope.launch { librarySettings.setAlbumSortPreference(preference) }
  }

  fun toggleViewMode() {
    viewModelScope.launch {
      val next = when (librarySettings.albumViewModeFlow.first()) {
        AlbumViewMode.AUTO -> AlbumViewMode.LIST
        AlbumViewMode.LIST -> AlbumViewMode.GRID
        AlbumViewMode.GRID -> AlbumViewMode.LIST
      }
      librarySettings.setAlbumViewMode(next)
    }
  }
}
