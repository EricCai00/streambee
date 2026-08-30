package com.kelsos.mbrc.feature.library.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.kelsos.mbrc.core.data.history.PlaybackHistoryDao
import com.kelsos.mbrc.core.data.history.PlaybackHistoryEntry
import com.kelsos.mbrc.core.data.library.track.TrackRepository
import com.kelsos.mbrc.feature.library.playback.DevicePlaybackController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

sealed interface PlaybackHistoryUiMessage {
  data object TrackUnavailable : PlaybackHistoryUiMessage
  data object PlayFailed : PlaybackHistoryUiMessage
}

class PlaybackHistoryViewModel(
  playbackHistoryDao: PlaybackHistoryDao,
  private val trackRepository: TrackRepository,
  private val devicePlaybackController: DevicePlaybackController
) : ViewModel() {
  private val _events = MutableSharedFlow<PlaybackHistoryUiMessage>()
  val events = _events.asSharedFlow()

  val history = Pager(
    config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
    pagingSourceFactory = playbackHistoryDao::getAll
  ).flow.cachedIn(viewModelScope)

  fun play(entry: PlaybackHistoryEntry) {
    viewModelScope.launch {
      val track = trackRepository.getByPath(entry.path)
      if (track == null) {
        _events.emit(PlaybackHistoryUiMessage.TrackUnavailable)
      } else if (!devicePlaybackController.play(track)) {
        _events.emit(PlaybackHistoryUiMessage.PlayFailed)
      }
    }
  }

  private companion object {
    const val PAGE_SIZE = 50
  }
}
