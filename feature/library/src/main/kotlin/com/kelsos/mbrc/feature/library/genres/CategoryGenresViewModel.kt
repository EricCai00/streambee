package com.kelsos.mbrc.feature.library.genres

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.kelsos.mbrc.core.common.settings.GenreSortPreference
import com.kelsos.mbrc.core.common.settings.LibrarySettings
import com.kelsos.mbrc.core.common.state.ConnectionStateFlow
import com.kelsos.mbrc.core.data.library.genre.Genre
import com.kelsos.mbrc.core.data.library.genre.GenreRepository
import com.kelsos.mbrc.feature.library.queue.QueueHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryGenresViewModel(
  private val repository: GenreRepository,
  queueHandler: QueueHandler,
  private val librarySettings: LibrarySettings,
  connectionStateFlow: ConnectionStateFlow
) : BaseGenreViewModel(queueHandler, librarySettings, connectionStateFlow) {
  private val categoryFilter = MutableSharedFlow<String>(replay = 1)

  val sortPreference: Flow<GenreSortPreference> = librarySettings.genreSortPreferenceFlow

  override val genres: Flow<PagingData<Genre>> =
    combine(categoryFilter, sortPreference) { category, sort -> category to sort.order }
      .flatMapLatest { (category, order) -> repository.getByCategory(category, order) }
      .cachedIn(viewModelScope)

  fun load(category: String) {
    viewModelScope.launch {
      categoryFilter.emit(category)
    }
  }

  fun updateSortPreference(preference: GenreSortPreference) {
    viewModelScope.launch {
      librarySettings.setGenreSortPreference(preference)
    }
  }
}
