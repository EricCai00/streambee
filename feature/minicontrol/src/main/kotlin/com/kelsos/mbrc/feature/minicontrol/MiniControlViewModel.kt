package com.kelsos.mbrc.feature.minicontrol

import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import com.kelsos.mbrc.core.common.mvvm.BaseViewModel
import com.kelsos.mbrc.core.common.mvvm.UiMessageBase
import com.kelsos.mbrc.core.common.playback.LocalPlaybackController
import com.kelsos.mbrc.core.common.state.AppStateFlow
import com.kelsos.mbrc.core.common.state.BasicTrackInfo
import com.kelsos.mbrc.core.common.state.PlayerState
import com.kelsos.mbrc.core.common.state.PlayingPosition
import com.kelsos.mbrc.core.common.state.TrackInfo
import com.kelsos.mbrc.core.common.utilities.coroutines.AppCoroutineDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

sealed class MiniControlUiMessages : UiMessageBase {
  data object NetworkUnavailable : MiniControlUiMessages()

  data object ActionFailed : MiniControlUiMessages()
}

class MiniControlViewModel(
  appState: AppStateFlow,
  private val localPlaybackController: LocalPlaybackController,
  private val dispatchers: AppCoroutineDispatchers
) : BaseViewModel<MiniControlUiMessages>() {
  val state: Flow<MiniControlState> =
    combine(
      appState.playingTrack,
      appState.playingPosition,
      appState.playerStatus.map { it.state }.distinctUntilChanged()
    ) { playingTrack, playingPosition, playerState ->
      MiniControlState(
        playingTrack = playingTrack,
        playingPosition = playingPosition,
        playingState = playerState
      )
    }

  fun perform(action: MiniControlAction) {
    viewModelScope.launch(dispatchers.main) {
      try {
        when (action) {
          MiniControlAction.PlayNext -> localPlaybackController.next()
          MiniControlAction.PlayPause -> localPlaybackController.playPause()
          MiniControlAction.PlayPrevious -> localPlaybackController.previous()
          MiniControlAction.Stop -> localPlaybackController.stop()
        }
      } catch (e: Exception) {
        Timber.e(e)
        emit(MiniControlUiMessages.ActionFailed)
      }
    }
  }
}

@Stable
data class MiniControlState(
  val playingTrack: TrackInfo = BasicTrackInfo(),
  val playingPosition: PlayingPosition = PlayingPosition(),
  val playingState: PlayerState = PlayerState.Undefined
)
