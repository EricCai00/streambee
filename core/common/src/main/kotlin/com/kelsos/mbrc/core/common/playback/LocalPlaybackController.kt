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

  /** Path of the track currently loaded by the phone's local player, if any. */
  val currentTrackPath: String?

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
  /** Cycles through sequential, repeat-all, repeat-one, and shuffle. */
  fun cyclePlaybackMode()
  fun toggleShuffle()
  fun toggleRepeat()
  /** Toggles the loved state of the exact item currently loaded by the phone player. */
  fun toggleFavorite()
  /** Toggles the loved state for this exact MusicBee library path. */
  fun toggleFavorite(trackPath: String)
  fun setFavorite(loved: Boolean)
  /** Updates the loved state for this exact MusicBee library path. */
  fun setFavorite(trackPath: String, loved: Boolean)
}
