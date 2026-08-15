package com.kelsos.mbrc.feature.content.playlists

import androidx.lifecycle.viewModelScope
import com.kelsos.mbrc.core.common.mvvm.BaseViewModel
import com.kelsos.mbrc.core.common.state.ConnectionStateFlow
import com.kelsos.mbrc.core.common.utilities.Outcome
import com.kelsos.mbrc.core.common.utilities.coroutines.AppCoroutineDispatchers
import com.kelsos.mbrc.core.data.library.track.Track
import com.kelsos.mbrc.core.data.library.track.TrackRepository
import com.kelsos.mbrc.core.data.playlist.PlaylistRepository
import com.kelsos.mbrc.core.queue.PathQueueUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistDetailViewModel(
  private val playlistRepository: PlaylistRepository,
  private val trackRepository: TrackRepository,
  private val queueUseCase: PathQueueUseCase,
  private val connectionStateFlow: ConnectionStateFlow,
  private val dispatchers: AppCoroutineDispatchers
) : BaseViewModel<PlaylistUiMessages>() {
  private val _tracks = MutableStateFlow<List<Track>>(emptyList())
  val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()
  private val _loaded = MutableStateFlow(false)
  val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

  private var loadedUrl: String? = null
  private var playlistPaths: List<String> = emptyList()

  fun load(url: String) {
    if (loadedUrl == url) return
    loadedUrl = url
    _loaded.value = false
    viewModelScope.launch(dispatchers.network) {
      playlistPaths = playlistRepository.getTrackPaths(url)
      _tracks.value = withContext(dispatchers.database) {
        playlistPaths.mapNotNull { trackRepository.getByPath(it) }
      }
      _loaded.value = true
    }
  }

  fun play(index: Int) {
    viewModelScope.launch(dispatchers.network) {
      if (!connectionStateFlow.isConnected) {
        emit(PlaylistUiMessages.NetworkUnavailable)
        return@launch
      }
      // Queue only tracks that can be resolved from the local library. This keeps
      // the clicked row index aligned with the queue start index when a playlist
      // contains a file that is no longer present in the library.
      val paths = _tracks.value.map { it.src }
      if (paths.isEmpty()) {
        emit(PlaylistUiMessages.PlayFailed)
        return@launch
      }
      val result = queueUseCase.queuePaths(paths, index)
      if (result is Outcome.Failure) emit(PlaylistUiMessages.PlayFailed)
    }
  }
}
