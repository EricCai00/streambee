package com.kelsos.mbrc.core.common.playback

import kotlinx.coroutines.flow.StateFlow

data class LocalQueueTrack(
  val title: String,
  val artist: String,
  val album: String,
  val path: String
)

interface LocalPlaybackController {
  val queue: StateFlow<List<LocalQueueTrack>>
  val hasLocalPlayback: Boolean
  fun play()
  fun pause()
  fun playPause()
  fun previous()
  fun next()
  fun playQueueItem(index: Int)
  fun removeQueueItem(index: Int)
  fun moveQueueItem(from: Int, to: Int)
  fun stop()
  fun seekTo(positionMs: Long)
  fun setVolume(percent: Int)
  fun adjustVolume(deltaPercent: Int)
  fun toggleMute()
  fun toggleShuffle()
  fun toggleRepeat()
  fun setFavorite(loved: Boolean)
}
