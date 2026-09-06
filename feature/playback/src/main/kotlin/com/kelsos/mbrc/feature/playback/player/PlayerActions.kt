package com.kelsos.mbrc.feature.playback.player

import com.kelsos.mbrc.core.common.playback.LocalPlaybackController
import com.kelsos.mbrc.core.common.state.LfmRating
import com.kelsos.mbrc.core.networking.protocol.actions.UserAction
import com.kelsos.mbrc.core.networking.protocol.base.Protocol
import com.kelsos.mbrc.core.networking.protocol.usecases.UserActionUseCase
import com.kelsos.mbrc.feature.settings.domain.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

interface IPlayerActions {
  val playPause: () -> Unit
  val previous: () -> Unit
  val next: () -> Unit
  val stop: () -> Unit
  val cyclePlaybackMode: () -> Unit
  val shuffle: () -> Unit
  val repeat: () -> Unit
  val mute: () -> Unit
  val changeVolume: (Int) -> Unit
  val seek: (Int) -> Unit
  val toggleFavorite: (trackPath: String, isFavorite: Boolean, isBanned: Boolean) -> Unit
  val toggleBan: (isBanned: Boolean, isFavorite: Boolean) -> Unit
  val toggleScrobbling: () -> Unit
}

class PlayerActions(
  private val userActionUseCase: UserActionUseCase,
  private val scope: CoroutineScope,
  private val devicePlaybackController: LocalPlaybackController,
  private val settingsManager: SettingsManager
) : IPlayerActions {

  override val playPause: () -> Unit = {
    devicePlaybackController.playPause()
  }

  override val previous: () -> Unit = {
    devicePlaybackController.previous()
  }

  override val next: () -> Unit = {
    devicePlaybackController.next()
  }

  override val stop: () -> Unit = {
    devicePlaybackController.stop()
  }

  override val cyclePlaybackMode: () -> Unit = {
    devicePlaybackController.cyclePlaybackMode()
  }

  override val shuffle: () -> Unit = {
    devicePlaybackController.toggleShuffle()
  }

  override val repeat: () -> Unit = {
    devicePlaybackController.toggleRepeat()
  }

  override val mute: () -> Unit = {
    devicePlaybackController.toggleMute()
  }

  override val changeVolume: (Int) -> Unit = { volume ->
    devicePlaybackController.setVolume(volume)
  }

  override val seek: (Int) -> Unit = { position ->
    devicePlaybackController.seekTo(position.toLong())
  }

  override val toggleFavorite: (String, Boolean, Boolean) -> Unit = {
    trackPath,
    isFavorite,
    isBanned,
  ->
    scope.launch {
      val localPath = devicePlaybackController.currentTrackPath
      Timber.d(
        "toggleFavorite: localPath=$localPath, hasLocalPlayback=" +
          "${devicePlaybackController.hasLocalPlayback}, " +
          "isFavorite=$isFavorite, isBanned=$isBanned"
      )

      // Always use a path-qualified request when the UI has a MusicBee file
      // path. This is important during player transitions: even if the
      // local-player flag has not propagated yet, falling back to the legacy
      // now-playing command would modify the computer's current track.
      val targetPath = (localPath ?: trackPath.takeIf { it.isNotBlank() })
        ?.takeUnless { it.isStreamPath() }
      if (targetPath != null) {
        devicePlaybackController.toggleFavorite(targetPath)
        return@launch
      }

      // A local player without a path is still local; do not fall through to
      // the command that targets the computer's current track.
      if (devicePlaybackController.hasLocalPlayback) {
        devicePlaybackController.toggleFavorite()
        return@launch
      }

      when {
        isFavorite -> {
          // Currently Loved, toggle to Normal
          userActionUseCase.perform(
            UserAction.create(Protocol.NowPlayingLfmRating, "toggle")
          )
        }

        isBanned -> {
          // Currently Banned, switch to Love
          // Workaround: Plugin has race condition when going Ban->Love->toggle
          // So we go Ban->Normal->Love to ensure clean state
          userActionUseCase.perform(
            UserAction.create(Protocol.NowPlayingLfmRating, "toggle")
          )
          userActionUseCase.perform(
            UserAction.create(Protocol.NowPlayingLfmRating, LfmRating.Loved.toActionString())
          )
        }

        else -> {
          // Currently Normal, set to Love
          userActionUseCase.perform(
            UserAction.create(Protocol.NowPlayingLfmRating, LfmRating.Loved.toActionString())
          )
        }
      }
    }
  }

  private fun String.isStreamPath(): Boolean = startsWith("http://", ignoreCase = true) ||
    startsWith("https://", ignoreCase = true) ||
    startsWith("rtsp://", ignoreCase = true) ||
    startsWith("mms://", ignoreCase = true)

  override val toggleBan: (Boolean, Boolean) -> Unit = { isBanned, isFavorite ->
    scope.launch {
      Timber.d("toggleBan: isBanned=$isBanned, isFavorite=$isFavorite")
      when {
        isBanned -> {
          // Currently Banned, toggle to Normal
          userActionUseCase.perform(
            UserAction.create(Protocol.NowPlayingLfmRating, "toggle")
          )
        }

        isFavorite -> {
          // Currently Loved, switch to Ban
          // Workaround: Plugin has race condition when going Love->Ban->toggle
          // So we go Love->Normal->Ban to ensure clean state
          userActionUseCase.perform(
            UserAction.create(Protocol.NowPlayingLfmRating, "toggle")
          )
          userActionUseCase.perform(
            UserAction.create(Protocol.NowPlayingLfmRating, LfmRating.Banned.toActionString())
          )
        }

        else -> {
          // Currently Normal, set to Ban
          userActionUseCase.perform(
            UserAction.create(Protocol.NowPlayingLfmRating, LfmRating.Banned.toActionString())
          )
        }
      }
    }
  }

  override val toggleScrobbling: () -> Unit = {
    scope.launch {
      val enabled = settingsManager.appScrobblingEnabledFlow.first()
      settingsManager.setAppScrobblingEnabled(!enabled)
    }
  }
}
