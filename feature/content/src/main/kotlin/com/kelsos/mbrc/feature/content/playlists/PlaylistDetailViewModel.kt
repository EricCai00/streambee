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
import kotlinx.coroutines.Job
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
  private val _startingTrackIndex = MutableStateFlow<Int?>(null)
  val startingTrackIndex: StateFlow<Int?> = _startingTrackIndex.asStateFlow()

  private var loadedUrl: String? = null
  private var playJob: Job? = null
  private var playRequestId = 0L

  fun load(url: String) {
    if (loadedUrl == url) return
    loadedUrl = url
    _loaded.value = false
    viewModelScope.launch(dispatchers.network) {
      val playlistTracks = playlistRepository.getTracks(url)
      _tracks.value = withContext(dispatchers.database) {
        playlistTracks.map { track -> trackRepository.getByPath(track.src) ?: track }
      }
      _loaded.value = true
    }
  }

  fun play(index: Int) {
    playJob?.cancel()
    val requestId = ++playRequestId
    _startingTrackIndex.value = index
    playJob = viewModelScope.launch(dispatchers.network) {
      try {
        if (!connectionStateFlow.isConnected) {
          emit(PlaylistUiMessages.NetworkUnavailable)
          return@launch
        }
        val tracks = _tracks.value
        if (tracks.isEmpty() || index !in tracks.indices) {
          emit(PlaylistUiMessages.PlayFailed)
          return@launch
        }
        val result = queueUseCase.queueTracks(tracks, index)
        if (result is Outcome.Failure) emit(PlaylistUiMessages.PlayFailed)
      } finally {
        if (playRequestId == requestId) _startingTrackIndex.value = null
      }
    }
  }
}
