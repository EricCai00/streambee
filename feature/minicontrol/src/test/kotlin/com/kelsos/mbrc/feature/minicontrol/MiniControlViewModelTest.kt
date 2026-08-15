package com.kelsos.mbrc.feature.minicontrol

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kelsos.mbrc.core.common.playback.LocalPlaybackController
import com.kelsos.mbrc.core.common.state.AppStateFlow
import com.kelsos.mbrc.core.common.state.BasicTrackInfo
import com.kelsos.mbrc.core.common.state.PlayerState
import com.kelsos.mbrc.core.common.state.PlayerStatusModel
import com.kelsos.mbrc.core.common.state.PlayingPosition
import com.kelsos.mbrc.core.common.test.testDispatcher
import com.kelsos.mbrc.core.common.test.testDispatcherModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject

@RunWith(AndroidJUnit4::class)
class MiniControlViewModelTest : KoinTest {
  private val appState = mockk<AppStateFlow>(relaxed = true)
  private val playbackController = mockk<LocalPlaybackController>(relaxed = true)

  private val testModule = module {
    single<AppStateFlow> { appState }
    single<LocalPlaybackController> { playbackController }
    singleOf(::MiniControlViewModel)
  }

  private val viewModel: MiniControlViewModel by inject()

  @Before
  fun setUp() {
    startKoin { modules(testModule, testDispatcherModule) }
    every { appState.playingTrack } returns MutableStateFlow(
      BasicTrackInfo("Artist", "Title", "Album", path = "path")
    )
    every { appState.playingPosition } returns MutableStateFlow(PlayingPosition(30L, 100L))
    every { appState.playerStatus } returns MutableStateFlow(
      PlayerStatusModel(volume = 50, state = PlayerState.Playing)
    )
  }

  @After
  fun tearDown() = stopKoin()

  @Test
  fun stateShouldCombineLocalPlayerState() = runTest(testDispatcher) {
    viewModel.state.test {
      val state = awaitItem()
      assertThat(state.playingTrack.title).isEqualTo("Title")
      assertThat(state.playingPosition.current).isEqualTo(30)
      assertThat(state.playingState).isEqualTo(PlayerState.Playing)
    }
  }

  @Test
  fun controlsShouldUseLocalPlaybackController() = runTest(testDispatcher) {
    viewModel.perform(MiniControlAction.PlayPrevious)
    viewModel.perform(MiniControlAction.PlayPause)
    viewModel.perform(MiniControlAction.PlayNext)
    viewModel.perform(MiniControlAction.Stop)
    testDispatcher.scheduler.advanceUntilIdle()

    verify(exactly = 1) { playbackController.previous() }
    verify(exactly = 1) { playbackController.playPause() }
    verify(exactly = 1) { playbackController.next() }
    verify(exactly = 1) { playbackController.stop() }
  }

  @Test
  fun controllerFailureEmitsActionFailed() = runTest(testDispatcher) {
    every { playbackController.next() } throws IllegalStateException("player error")

    viewModel.events.test {
      viewModel.perform(MiniControlAction.PlayNext)
      testDispatcher.scheduler.advanceUntilIdle()
      assertThat(awaitItem()).isEqualTo(MiniControlUiMessages.ActionFailed)
    }
  }
}
