package com.kelsos.mbrc.feature.library.tracks

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.kelsos.mbrc.core.common.settings.LibrarySettings
import com.kelsos.mbrc.core.common.state.ConnectionStateFlow
import com.kelsos.mbrc.core.data.library.track.Track
import com.kelsos.mbrc.core.queue.Queue
import com.kelsos.mbrc.feature.library.BaseLibraryViewModel
import com.kelsos.mbrc.feature.library.queue.QueueHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

abstract class BaseTrackViewModel(
  private val queueHandler: QueueHandler,
  librarySettings: LibrarySettings,
  connectionStateFlow: ConnectionStateFlow
) : BaseLibraryViewModel<TrackUiMessage>(librarySettings, connectionStateFlow) {
  abstract val tracks: Flow<PagingData<Track>>

  open fun queue(action: Queue, track: Track) {
    viewModelScope.launch {
      if (!checkConnection()) {
        emit(TrackUiMessage.NetworkUnavailable)
        return@launch
      }

      // A library track click always starts playback on this device. The
      // queue menu actions are local as well; the plugin remains remote-capable
      // for other clients.
      val queueAction = if (action == Queue.Default) Queue.Local else action
      val result = queueHandler.queueTrack(track = track, type = queueAction)

      val message =
        if (result.isSuccess) {
          TrackUiMessage.QueueSuccess(result.getOrNull() ?: 0)
        } else {
          TrackUiMessage.QueueFailed
        }

      emit(message)
    }
  }
}
