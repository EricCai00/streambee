package com.kelsos.mbrc.service.mediasession

import androidx.media3.session.MediaSession
import com.kelsos.mbrc.core.common.utilities.coroutines.AppCoroutineDispatchers
import com.kelsos.mbrc.feature.library.playback.DevicePlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

/** Exposes the library player's single local Media3 session to notifications and system controls. */
class MediaSessionManager(
  private val dispatchers: AppCoroutineDispatchers,
  private val devicePlaybackController: DevicePlaybackController
) {
  private var _mediaSession: MediaSession? = null
  private var sessionJob: Job = Job()
  var scope: CoroutineScope = CoroutineScope(sessionJob + dispatchers.main)
    private set

  fun initialize(): MediaSession {
    _mediaSession?.let { return it }
    if (sessionJob.isCancelled) {
      sessionJob = Job()
      scope = CoroutineScope(sessionJob + dispatchers.main)
    }
    _mediaSession = devicePlaybackController.mediaSession
    return devicePlaybackController.mediaSession
  }

  fun destroy() {
    scope.cancel()
    // DevicePlaybackController owns the player and session for the app process.
    _mediaSession = null
  }

  val mediaSession: MediaSession?
    get() = _mediaSession
}
