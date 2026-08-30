package com.kelsos.mbrc.core.common.settings

import kotlinx.coroutines.flow.Flow

/** Preferences that affect playback performed by the Android app itself. */
interface PlaybackSettings {
  /** Whether qualifying tracks played on this device should be scrobbled through MusicBee. */
  val appScrobblingEnabledFlow: Flow<Boolean>
}
