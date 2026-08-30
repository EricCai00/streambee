package com.kelsos.mbrc.feature.playback.lyrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kelsos.mbrc.core.common.playback.LocalPlaybackController
import com.kelsos.mbrc.core.common.state.AppStateFlow
import com.kelsos.mbrc.core.common.state.PlayerState
import com.kelsos.mbrc.core.common.state.PlayingPosition
import com.kelsos.mbrc.core.common.state.TrackDetails
import com.kelsos.mbrc.core.common.state.TrackInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class LyricsViewModel(
  appState: AppStateFlow,
  private val devicePlaybackController: LocalPlaybackController
) : ViewModel() {
  private val parsedLyrics = appState.lyrics
    .map(::parseLyrics)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ParsedLyrics())

  val lyrics: Flow<List<String>> = parsedLyrics.map { it.plainLines }
  val synchronizedLyrics: Flow<List<TimedLyricLine>> = parsedLyrics.map { it.timedLines }
  val playingTrack: StateFlow<TrackInfo> = appState.playingTrack
  val playingPosition: StateFlow<PlayingPosition> = appState.playingPosition
  val trackDetails: StateFlow<TrackDetails> = appState.playingTrackDetails
  val isPlaying: StateFlow<Boolean> = appState.playerStatus
    .map { it.state == PlayerState.Playing }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

  fun playPause() {
    devicePlaybackController.playPause()
  }

  fun seek(position: Int) {
    devicePlaybackController.seekTo(position.toLong())
  }
}
