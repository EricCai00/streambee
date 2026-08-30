package com.kelsos.mbrc.feature.playback.player

import androidx.lifecycle.viewModelScope
import com.kelsos.mbrc.core.common.mvvm.BaseViewModel
import com.kelsos.mbrc.core.common.playback.LocalPlaybackController
import com.kelsos.mbrc.core.common.playback.LocalQueueTrack
import com.kelsos.mbrc.core.common.settings.ChangeLogChecker
import com.kelsos.mbrc.core.common.state.AppStateFlow
import com.kelsos.mbrc.core.common.state.BasicTrackInfo
import com.kelsos.mbrc.core.common.state.PlayingPosition
import com.kelsos.mbrc.core.common.state.TrackDetails
import com.kelsos.mbrc.core.common.state.TrackInfo
import com.kelsos.mbrc.core.common.state.TrackRating
import com.kelsos.mbrc.core.networking.protocol.usecases.UserActionUseCase
import com.kelsos.mbrc.feature.settings.domain.SettingsManager
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class PlayerViewModel(
  changeLogChecker: ChangeLogChecker,
  appState: AppStateFlow,
  private val userActionUseCase: UserActionUseCase,
  settingsManager: SettingsManager,
  private val devicePlaybackController: LocalPlaybackController
) : BaseViewModel<PlayerUiMessage>() {
  // Separate flows for granular recomposition
  val playingTrack: StateFlow<TrackInfo> = appState.playingTrack
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BasicTrackInfo())

  val playingPosition: StateFlow<PlayingPosition> = appState.playingPosition
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayingPosition())

  val trackRating: StateFlow<TrackRating> = appState.playingTrackRating
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrackRating())

  val volumeState: StateFlow<VolumeState> = appState.playerStatus
    .map { VolumeState(volume = it.volume, mute = it.mute) }
    .distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VolumeState())

  val playbackState: StateFlow<PlaybackState> = appState.playerStatus
    .map { PlaybackState(playerState = it.state, shuffle = it.shuffle, repeat = it.repeat) }
    .distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaybackState())

  val isScrobbling: StateFlow<Boolean> = settingsManager.appScrobblingEnabledFlow
    .distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

  val trackDetails: StateFlow<TrackDetails> = appState.playingTrackDetails
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrackDetails.EMPTY)

  val showRatingOnPlayer: StateFlow<Boolean> = settingsManager.showRatingOnPlayerFlow
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

  val queue: StateFlow<List<LocalQueueTrack>> = devicePlaybackController.queue

  val actions: IPlayerActions = PlayerActions(
    userActionUseCase = userActionUseCase,
    scope = viewModelScope,
    devicePlaybackController = devicePlaybackController,
    settingsManager = settingsManager
  )

  init {
    viewModelScope.launch {
      if (changeLogChecker.checkShouldShowChangeLog()) {
        emit(PlayerUiMessage.ShowChangelog)
      }
    }
  }

  fun playQueueItem(index: Int) {
    devicePlaybackController.playQueueItem(index)
  }

  companion object {
  }
}
