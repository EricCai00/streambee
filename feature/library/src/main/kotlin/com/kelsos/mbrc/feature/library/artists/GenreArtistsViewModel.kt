package com.kelsos.mbrc.feature.library.artists

import androidx.lifecycle.viewModelScope
import com.kelsos.mbrc.core.common.settings.ArtistSortPreference
import com.kelsos.mbrc.core.common.settings.LibrarySettings
import com.kelsos.mbrc.core.common.settings.SortOrder
import com.kelsos.mbrc.core.common.state.ConnectionStateFlow
import com.kelsos.mbrc.core.data.library.artist.Artist
import com.kelsos.mbrc.core.data.library.artist.ArtistRepository
import com.kelsos.mbrc.feature.library.queue.QueueHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class GenreArtistsViewModel(
  private val repository: ArtistRepository,
  queueHandler: QueueHandler,
  private val librarySettings: LibrarySettings,
  connectionStateFlow: ConnectionStateFlow
) : BaseArtistViewModel(queueHandler, librarySettings, connectionStateFlow) {
  private val genre: MutableSharedFlow<Long> = MutableSharedFlow(replay = 1)

  val sortPreference: Flow<SortOrder> = librarySettings.artistSortPreferenceFlow
    .flatMapLatest { pref ->
      kotlinx.coroutines.flow.flowOf(pref.order)
    }

  override val artists: StateFlow<List<Artist>?> =
    combine(genre, librarySettings.artistSortPreferenceFlow) { id, sort ->
      id to sort.order
    }.flatMapLatest { (id, order) ->
      repository.getArtistByGenreList(id, order)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

  fun load(id: Long) {
    viewModelScope.launch {
      genre.emit(id)
    }
  }

  fun updateSortPreference(preference: ArtistSortPreference) {
    viewModelScope.launch {
      librarySettings.setArtistSortPreference(preference)
    }
  }
}
