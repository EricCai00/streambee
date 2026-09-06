package com.kelsos.mbrc.feature.library.artists

import androidx.lifecycle.viewModelScope
import com.kelsos.mbrc.core.common.settings.ArtistSortPreference
import com.kelsos.mbrc.core.common.settings.LibrarySettings
import com.kelsos.mbrc.core.common.settings.SortOrder
import com.kelsos.mbrc.core.common.state.ConnectionStateFlow
import com.kelsos.mbrc.core.data.library.artist.Artist
import com.kelsos.mbrc.core.data.library.artist.ArtistRepository
import com.kelsos.mbrc.feature.library.LibrarySearchModel
import com.kelsos.mbrc.feature.library.domain.LibrarySyncUseCase
import com.kelsos.mbrc.feature.library.queue.QueueHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class ArtistSearchParams(val keyword: String, val sortOrder: SortOrder)

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseArtistViewModel(
  private val repository: ArtistRepository,
  private val librarySyncUseCase: LibrarySyncUseCase,
  private val searchModel: LibrarySearchModel,
  private val librarySettings: LibrarySettings,
  queueHandler: QueueHandler,
  connectionStateFlow: ConnectionStateFlow
) : BaseArtistViewModel(queueHandler, librarySettings, connectionStateFlow) {
  val sortPreference: Flow<ArtistSortPreference> = librarySettings.artistSortPreferenceFlow

  override val artists: StateFlow<List<Artist>?> =
    combine(
      searchModel.term,
      sortPreference
    ) { keyword, sort ->
      ArtistSearchParams(keyword, sort.order)
    }.flatMapLatest { (keyword, sortOrder) ->
      if (keyword.isEmpty()) {
        repository.getAlbumArtistsOnlyList(sortOrder)
      } else {
        repository.searchAlbumArtistsList(keyword, sortOrder)
      }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

  val showSync = searchModel.term.map { it.isEmpty() }

  fun sync() {
    viewModelScope.launch {
      if (!checkConnection()) {
        emit(ArtistUiMessage.NetworkUnavailable)
        return@launch
      }
      librarySyncUseCase.sync()
    }
  }

  fun updateSortPreference(preference: ArtistSortPreference) {
    viewModelScope.launch {
      librarySettings.setArtistSortPreference(preference)
    }
  }
}
