package com.kelsos.mbrc.core.networking.api

import com.kelsos.mbrc.core.common.data.Progress
import com.kelsos.mbrc.core.networking.dto.NowPlayingDto
import com.kelsos.mbrc.core.networking.protocol.payloads.CoverPayload
import com.kelsos.mbrc.core.networking.protocol.payloads.NowPlayingDetailsPayload
import kotlinx.coroutines.flow.Flow

interface PlaybackApi {
  fun getNowPlayingList(progress: Progress?): Flow<List<NowPlayingDto>>

  suspend fun getCover(): CoverPayload

  /**
   * Gets track details for MusicBee's current track, or for [path] when the
   * Android app is playing a local library item.
   */
  suspend fun getTrackDetails(path: String? = null): NowPlayingDetailsPayload
}
