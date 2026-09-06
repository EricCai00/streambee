package com.kelsos.mbrc.feature.library.grouping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.kelsos.mbrc.core.common.settings.AlbumSortField
import com.kelsos.mbrc.core.common.settings.AlbumSortPreference
import com.kelsos.mbrc.core.common.settings.GenreSortField
import com.kelsos.mbrc.core.common.settings.GenreSortPreference
import com.kelsos.mbrc.core.common.settings.SortOrder
import com.kelsos.mbrc.core.common.settings.SortPreference
import com.kelsos.mbrc.core.data.library.grouping.DecadeGroup
import com.kelsos.mbrc.core.data.library.grouping.LanguageGroup
import com.kelsos.mbrc.core.data.library.grouping.LibraryGroupingRepository
import com.kelsos.mbrc.feature.library.LibrarySearchModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

class BrowseYearViewModel(repository: LibraryGroupingRepository, searchModel: LibrarySearchModel) :
  ViewModel() {
  private val _sortPreference = MutableStateFlow(
    SortPreference(AlbumSortField.YEAR, SortOrder.DESC)
  )
  val sortPreference: Flow<AlbumSortPreference> = _sortPreference

  val decades: Flow<PagingData<DecadeGroup>> =
    combine(searchModel.term, sortPreference) { term, sort -> term to sort.order }
      .flatMapLatest { (term, order) -> repository.getDecades(term, order) }
      .cachedIn(viewModelScope)

  val showSync: Flow<Boolean> = searchModel.term.map { it.isEmpty() }

  fun updateSortPreference(preference: AlbumSortPreference) {
    _sortPreference.value = preference.copy(field = AlbumSortField.YEAR)
  }
}

class BrowseLanguageViewModel(
  repository: LibraryGroupingRepository,
  searchModel: LibrarySearchModel
) : ViewModel() {
  private val _sortPreference = MutableStateFlow(
    SortPreference(GenreSortField.NAME, SortOrder.ASC)
  )
  val sortPreference: Flow<GenreSortPreference> = _sortPreference

  val languages: Flow<PagingData<LanguageGroup>> =
    combine(searchModel.term, sortPreference) { term, sort -> term to sort.order }
      .flatMapLatest { (term, order) -> repository.getLanguages(term, order) }
      .cachedIn(viewModelScope)

  val showSync: Flow<Boolean> = searchModel.term.map { it.isEmpty() }

  fun updateSortPreference(preference: GenreSortPreference) {
    _sortPreference.value = preference.copy(field = GenreSortField.NAME)
  }
}
