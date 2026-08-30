package com.kelsos.mbrc.adapters

import com.google.common.truth.Truth.assertThat
import com.kelsos.mbrc.core.common.playback.LocalPlaybackController
import com.kelsos.mbrc.core.common.state.AppState
import com.kelsos.mbrc.core.common.state.TrackDetails
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class PlayerStateHandlerImplTest {
  private val appState = AppState()
  private val localPlaybackController: LocalPlaybackController = mockk(relaxed = true)
  private val handler = PlayerStateHandlerImpl(appState, localPlaybackController)

  @Test
  fun `computer track details are ignored during local playback`() {
    every { localPlaybackController.hasLocalPlayback } returns true

    handler.updateTrackDetails(TrackDetails(genre = "Computer Genre"))

    assertThat(appState.playingTrackDetails.value).isEqualTo(TrackDetails.EMPTY)
  }

  @Test
  fun `computer track details are published when local playback is inactive`() {
    every { localPlaybackController.hasLocalPlayback } returns false
    val details = TrackDetails(genre = "Computer Genre")

    handler.updateTrackDetails(details)

    assertThat(appState.playingTrackDetails.value).isEqualTo(details)
  }
}
