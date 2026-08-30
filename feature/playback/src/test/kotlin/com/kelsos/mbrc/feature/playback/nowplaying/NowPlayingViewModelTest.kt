package com.kelsos.mbrc.feature.playback.nowplaying

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kelsos.mbrc.core.common.playback.LocalPlaybackController
import com.kelsos.mbrc.core.common.playback.LocalQueueTrack
import com.kelsos.mbrc.core.common.state.AppStateFlow
import com.kelsos.mbrc.core.common.state.BasicTrackInfo
import com.kelsos.mbrc.core.common.state.ConnectionStateFlow
import com.kelsos.mbrc.core.common.state.TrackInfo
import com.kelsos.mbrc.core.common.test.testDispatcher
import com.kelsos.mbrc.core.common.test.testDispatcherModule
import com.kelsos.mbrc.core.networking.protocol.SelfMutationConfig
import com.kelsos.mbrc.core.networking.protocol.SelfMutationTracker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
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
class NowPlayingViewModelTest : KoinTest {
  private val localQueue = MutableStateFlow<List<LocalQueueTrack>>(emptyList())

  private val testModule =
    module {
      single<NowPlayingRepository> { mockk(relaxed = true) }
      single<MoveManager> { mockk(relaxed = true) }
      single<LocalPlaybackController> { mockk(relaxed = true) }
      single<ConnectionStateFlow> { mockk(relaxed = true) }
      single<AppStateFlow> { mockk(relaxed = true) }
      single { SelfMutationTracker(clock = { 0L }, config = SelfMutationConfig()) }
      singleOf(::NowPlayingViewModel)
    }

  private val viewModel: NowPlayingViewModel by inject()
  private val repository: NowPlayingRepository by inject()
  private val moveManager: MoveManager by inject()
  private val localPlaybackController: LocalPlaybackController by inject()
  private val connectionStateFlow: ConnectionStateFlow by inject()
  private val appStateFlow: AppStateFlow by inject()

  @Before
  fun setUp() {
    startKoin {
      modules(listOf(testModule, testDispatcherModule))
    }

    every { localPlaybackController.queue } returns localQueue
    every { appStateFlow.playingTrack } returns MutableStateFlow<TrackInfo>(BasicTrackInfo())
    coEvery { connectionStateFlow.isConnected } returns true
  }

  @After
  fun tearDown() {
    stopKoin()
  }

  @Test
  fun reloadShouldEmitNetworkUnavailableWhenNotConnectedAndShowUserMessageTrue() {
    runTest(testDispatcher) {
      // Given
      coEvery { connectionStateFlow.isConnected } returns false

      // When & Then
      viewModel.events.test {
        viewModel.actions.reload(showUserMessage = true)
        testDispatcher.scheduler.advanceUntilIdle()

        val event = awaitItem()
        assertThat(event).isEqualTo(NowPlayingUiMessages.NetworkUnavailable)
      }

      // Verify repository is not called when not connected
      coVerify(exactly = 0) { repository.getRemote() }
    }
  }

  @Test
  fun reloadShouldNotEmitWhenNotConnectedAndShowUserMessageFalse() {
    runTest(testDispatcher) {
      // Given
      coEvery { connectionStateFlow.isConnected } returns false

      // When & Then
      viewModel.events.test {
        viewModel.actions.reload(showUserMessage = false)
        testDispatcher.scheduler.advanceUntilIdle()

        // Should not emit any events
        expectNoEvents()
      }

      // Verify repository is not called when not connected
      coVerify(exactly = 0) { repository.getRemote() }
    }
  }

  @Test
  fun reloadShouldEmitRefreshSuccessWhenConnectedAndRepositorySucceedsAndShowUserMessageTrue() {
    runTest(testDispatcher) {
      // Given
      coEvery { connectionStateFlow.isConnected } returns true
      coEvery { repository.getRemote() } returns Unit

      // When & Then
      viewModel.events.test {
        viewModel.actions.reload(showUserMessage = true)
        testDispatcher.scheduler.advanceUntilIdle()

        val event = awaitItem()
        assertThat(event).isEqualTo(NowPlayingUiMessages.RefreshSucceeded)
      }

      // Verify repository was called (once from init, once from test)
      coVerify(exactly = 2) { repository.getRemote() }
    }
  }

  @Test
  fun reloadShouldNotEmitWhenConnectedAndRepositorySucceedsAndShowUserMessageFalse() {
    runTest(testDispatcher) {
      // Given
      coEvery { connectionStateFlow.isConnected } returns true
      coEvery { repository.getRemote() } returns Unit

      // When & Then
      viewModel.events.test {
        viewModel.actions.reload(showUserMessage = false)
        testDispatcher.scheduler.advanceUntilIdle()

        // Should not emit any events on success when showUserMessage is false
        expectNoEvents()
      }

      // Verify repository was called (once from init, once from test)
      coVerify(exactly = 2) { repository.getRemote() }
    }
  }

  @Test
  fun reloadShouldEmitRefreshFailedWhenConnectedButRepositoryThrowsAndShowUserMessageTrue() {
    runTest(testDispatcher) {
      // Given
      val ioException = IOException("Network error")
      coEvery { connectionStateFlow.isConnected } returns true
      coEvery { repository.getRemote() } throws ioException

      // When & Then
      viewModel.events.test {
        viewModel.actions.reload(showUserMessage = true)
        testDispatcher.scheduler.advanceUntilIdle()

        val event = awaitItem()
        assertThat(event).isInstanceOf(NowPlayingUiMessages.RefreshFailed::class.java)
        assertThat((event as NowPlayingUiMessages.RefreshFailed).throwable).isEqualTo(ioException)
      }

      // Verify repository was called (once from init, once from test)
      coVerify(exactly = 2) { repository.getRemote() }
    }
  }

  @Test
  fun reloadShouldEmitRefreshSupersededWhenRefreshIsCancelledByANewerOne() {
    runTest(testDispatcher) {
      // Given — the shared single-flight refresh in the repository is cancelled because a newer
      // trigger (broadcast or another pull) superseded it; getRemote surfaces a CancellationException
      // while this view-model coroutine itself stays active.
      coEvery { connectionStateFlow.isConnected } returns true
      coEvery { repository.getRemote() } throws
        CancellationException("superseded by a newer refresh")

      // When & Then — must end the pull-to-refresh indicator without reporting success or failure.
      viewModel.events.test {
        viewModel.actions.reload(showUserMessage = true)
        testDispatcher.scheduler.advanceUntilIdle()

        val event = awaitItem()
        assertThat(event).isEqualTo(NowPlayingUiMessages.RefreshSuperseded)
      }
    }
  }

  @Test
  fun reloadShouldNotEmitWhenConnectedButRepositoryThrowsIOExceptionAndShowUserMessageFalse() {
    runTest(testDispatcher) {
      // Given
      coEvery { connectionStateFlow.isConnected } returns true
      coEvery { repository.getRemote() } throws IOException("Network error")

      // When & Then
      viewModel.events.test {
        viewModel.actions.reload(showUserMessage = false)
        testDispatcher.scheduler.advanceUntilIdle()

        // Should not emit any events on failure when showUserMessage is false
        expectNoEvents()
      }

      // Verify repository was called (once from init, once from test)
      coVerify(exactly = 2) { repository.getRemote() }
    }
  }

  @Test
  fun reloadWithoutParameterShouldDefaultToShowUserMessageTrue() {
    runTest(testDispatcher) {
      // Given
      coEvery { connectionStateFlow.isConnected } returns true
      coEvery { repository.getRemote() } returns Unit

      // When & Then
      viewModel.events.test {
        viewModel.actions.reload()
        testDispatcher.scheduler.advanceUntilIdle()

        val event = awaitItem()
        assertThat(event).isEqualTo(NowPlayingUiMessages.RefreshSucceeded)
      }

      // Verify repository was called (once from init, once from test)
      coVerify(exactly = 2) { repository.getRemote() }
    }
  }

  @Test
  fun networkCheckIsPerformedAtStartOfOperation() {
    runTest(testDispatcher) {
      // Given - init consumes first true, then true for first test call, false for second
      coEvery { connectionStateFlow.isConnected } returns true andThen true andThen false
      coEvery { repository.getRemote() } returns Unit

      // When & Then - First call should succeed, second should fail
      viewModel.events.test {
        viewModel.actions.reload(showUserMessage = true) // Should succeed (first call)
        testDispatcher.scheduler.advanceUntilIdle()

        val firstEvent = awaitItem()
        assertThat(firstEvent).isEqualTo(NowPlayingUiMessages.RefreshSucceeded)

        viewModel.actions.reload(showUserMessage = true) // Should fail (second call)
        testDispatcher.scheduler.advanceUntilIdle()

        val secondEvent = awaitItem()
        assertThat(secondEvent).isEqualTo(NowPlayingUiMessages.NetworkUnavailable)
      }
    }
  }

  @Test
  fun tracksShouldExposeLocalQueuePagingData() {
    localQueue.value = listOf(localTrack(title = "Queued track"))

    assertThat(viewModel.tracks).isNotNull()
  }

  @Test
  fun playingTrackShouldReturnAppStatePlayingTrack() {
    // Given
    val mockPlayingTrack: TrackInfo = BasicTrackInfo(path = "test/path")
    every { appStateFlow.playingTrack } returns MutableStateFlow(mockPlayingTrack)

    // Then
    assertThat(viewModel.playingTrack).isNotNull()
    // Note: Flow testing requires more setup, this verifies the flow is accessible
  }

  @Test
  fun multipleReloadCallsWithDifferentParametersShouldBehaveCorrectly() {
    runTest(testDispatcher) {
      // Given
      coEvery { connectionStateFlow.isConnected } returns true
      coEvery { repository.getRemote() } returns Unit

      // When & Then
      viewModel.events.test {
        viewModel.actions.reload(showUserMessage = true)
        viewModel.actions.reload(showUserMessage = false)
        testDispatcher.scheduler.advanceUntilIdle()

        // Only the first call should emit an event
        val event = awaitItem()
        assertThat(event).isEqualTo(NowPlayingUiMessages.RefreshSucceeded)
        expectNoEvents()
      }

      // Verify repository was called three times (once from init, twice from test)
      coVerify(exactly = 3) { repository.getRemote() }
    }
  }

  @Test
  fun playShouldEmitPlayFailedWhenPositionIsOutsideLocalQueue() {
    runTest(testDispatcher) {
      localQueue.value = emptyList()

      viewModel.events.test {
        viewModel.actions.play(5)
        testDispatcher.scheduler.advanceUntilIdle()

        val event = awaitItem()
        assertThat(event).isEqualTo(NowPlayingUiMessages.PlayFailed)
      }

      verify(exactly = 0) { localPlaybackController.playQueueItem(any()) }
    }
  }

  @Test
  fun playShouldUseLocalQueueItemWithoutEmittingOnSuccess() {
    runTest(testDispatcher) {
      localQueue.value = List(6) { index -> localTrack(title = "Track $index") }

      viewModel.events.test {
        viewModel.actions.play(5)
        testDispatcher.scheduler.advanceUntilIdle()

        expectNoEvents()
      }

      verify(exactly = 1) { localPlaybackController.playQueueItem(5) }
    }
  }

  @Test
  fun playShouldEmitPlayFailedWhenLocalPlaybackThrowsIOException() {
    runTest(testDispatcher) {
      localQueue.value = listOf(localTrack())
      every { localPlaybackController.playQueueItem(0) } throws IOException("Playback error")

      viewModel.events.test {
        viewModel.actions.play(0)
        testDispatcher.scheduler.advanceUntilIdle()

        val event = awaitItem()
        assertThat(event).isEqualTo(NowPlayingUiMessages.PlayFailed)
      }

      verify(exactly = 1) { localPlaybackController.playQueueItem(0) }
    }
  }

  @Test
  fun removeTrackShouldWorkWhileDisconnectedBecauseQueueIsLocal() {
    runTest(testDispatcher) {
      coEvery { connectionStateFlow.isConnected } returns false

      viewModel.events.test {
        viewModel.actions.removeTrack(3)
        testDispatcher.scheduler.advanceUntilIdle()

        expectNoEvents()
      }

      verify(exactly = 1) { localPlaybackController.removeQueueItem(3) }
    }
  }

  @Test
  fun removeTrackShouldNotEmitWhenLocalRemovalSucceeds() {
    runTest(testDispatcher) {
      viewModel.events.test {
        viewModel.actions.removeTrack(3)
        testDispatcher.scheduler.advanceUntilIdle()

        expectNoEvents()
      }

      verify(exactly = 1) { localPlaybackController.removeQueueItem(3) }
    }
  }

  @Test
  fun removeTrackShouldEmitRemoveFailedWhenLocalRemovalThrowsIOException() {
    runTest(testDispatcher) {
      every { localPlaybackController.removeQueueItem(3) } throws IOException("Removal error")

      viewModel.events.test {
        viewModel.actions.removeTrack(3)
        testDispatcher.scheduler.advanceUntilIdle()

        val event = awaitItem()
        assertThat(event).isEqualTo(NowPlayingUiMessages.RemoveFailed)
      }

      verify(exactly = 1) { localPlaybackController.removeQueueItem(3) }
    }
  }

  @Test
  fun searchShouldPlayMatchingLocalTrackAndEmitSuccess() {
    runTest(testDispatcher) {
      val query = "test song"
      val trackTitle = "Test Song Title"
      localQueue.value = listOf(
        localTrack(title = "Other"),
        localTrack(title = trackTitle)
      )

      viewModel.events.test {
        viewModel.actions.search(query)
        testDispatcher.scheduler.advanceUntilIdle()

        val event = awaitItem()
        assertThat(event).isEqualTo(NowPlayingUiMessages.SearchSuccess(trackTitle))
      }

      verify(exactly = 1) { localPlaybackController.playQueueItem(1) }
    }
  }

  @Test
  fun searchShouldEmitNotFoundWhenNoTrackMatches() {
    runTest(testDispatcher) {
      val query = "nonexistent song"
      localQueue.value = listOf(localTrack(title = "Different title", artist = "Different artist"))

      viewModel.events.test {
        viewModel.actions.search(query)
        testDispatcher.scheduler.advanceUntilIdle()

        val event = awaitItem()
        assertThat(event).isEqualTo(NowPlayingUiMessages.SearchNotFound)
      }

      verify(exactly = 0) { localPlaybackController.playQueueItem(any()) }
    }
  }

  @Test
  fun searchShouldMatchArtistAndPlayWhileDisconnected() {
    runTest(testDispatcher) {
      val query = "matching artist"
      val trackTitle = "Local title"
      localQueue.value = listOf(localTrack(title = trackTitle, artist = "Matching Artist"))
      coEvery { connectionStateFlow.isConnected } returns false

      viewModel.events.test {
        viewModel.actions.search(query)
        testDispatcher.scheduler.advanceUntilIdle()

        val event = awaitItem()
        assertThat(event).isEqualTo(NowPlayingUiMessages.SearchSuccess(trackTitle))
      }

      verify(exactly = 1) { localPlaybackController.playQueueItem(0) }
    }
  }

  @Test
  fun moveTrackShouldAllowLocalMovement() {
    runTest(testDispatcher) {
      // Given - moveTrack should work regardless of connection
      coEvery { connectionStateFlow.isConnected } returns false

      // When & Then
      viewModel.events.test {
        viewModel.actions.moveTrack(from = 2, to = 5)
        testDispatcher.scheduler.advanceUntilIdle()

        // Should not emit any events since this is just local UI update
        expectNoEvents()
      }

      // Verify moveManager.move was called
      coVerify(exactly = 1) { moveManager.move(2, 5) }
    }
  }

  @Test
  fun moveShouldCommitWhileDisconnectedBecauseQueueIsLocal() {
    runTest(testDispatcher) {
      coEvery { connectionStateFlow.isConnected } returns false

      viewModel.events.test {
        viewModel.actions.move()
        testDispatcher.scheduler.advanceUntilIdle()

        expectNoEvents()
      }

      coVerify(exactly = 1) { moveManager.commit() }
    }
  }

  @Test
  fun moveShouldCommitWhenConnected() {
    runTest(testDispatcher) {
      // Given
      coEvery { connectionStateFlow.isConnected } returns true

      // When & Then
      viewModel.events.test {
        viewModel.actions.move()
        testDispatcher.scheduler.advanceUntilIdle()

        // Should not emit any events on successful commit
        expectNoEvents()
      }

      // Verify moveManager.commit was called
      coVerify(exactly = 1) { moveManager.commit() }
    }
  }

  @Test
  fun multipleMoveTrackCallsBatchIntoASingleNetworkMessageOnCommit() {
    runTest(testDispatcher) {
      // Given — a real MoveManagerImpl batches every per-row swap during the drag
      // and submits one (originalPosition, finalPosition) tuple on commit().
      val realMoveManager = MoveManagerImpl()
      val realLocalQueue = MutableStateFlow<List<LocalQueueTrack>>(emptyList())
      val realLocalPlaybackController: LocalPlaybackController = mockk(relaxed = true) {
        every { queue } returns realLocalQueue
      }
      val realModule = module {
        single<NowPlayingRepository> { mockk(relaxed = true) }
        single<MoveManager> { realMoveManager }
        single<LocalPlaybackController> { realLocalPlaybackController }
        single<ConnectionStateFlow> { mockk(relaxed = true) }
        single<AppStateFlow> { mockk(relaxed = true) }
        single { SelfMutationTracker(clock = { 0L }, config = SelfMutationConfig()) }
        singleOf(::NowPlayingViewModel)
      }

      stopKoin()
      startKoin { modules(listOf(realModule, testDispatcherModule)) }

      val realRepository: NowPlayingRepository by inject()
      val realConnectionState: ConnectionStateFlow by inject()
      val realAppState: AppStateFlow by inject()
      val realViewModel: NowPlayingViewModel by inject()

      every { realAppState.playingTrack } returns MutableStateFlow<TrackInfo>(BasicTrackInfo())
      coEvery { realConnectionState.isConnected } returns true
      coEvery { realRepository.getRemote() } returns Unit

      // When — simulate a drag through several intermediate positions
      realViewModel.actions.moveTrack(from = 10, to = 9)
      realViewModel.actions.moveTrack(from = 9, to = 8)
      realViewModel.actions.moveTrack(from = 8, to = 7)
      realViewModel.actions.moveTrack(from = 7, to = 6)
      realViewModel.actions.move()
      testDispatcher.scheduler.advanceUntilIdle()

      // Then — exactly one local queue move carrying the original and final positions.
      verify(exactly = 1) { realLocalPlaybackController.moveQueueItem(10, 6) }
    }
  }

  private fun localTrack(
    title: String = "Title",
    artist: String = "Artist",
    album: String = "Album",
    path: String = "D:/Music/song.flac"
  ) = LocalQueueTrack(title = title, artist = artist, album = album, path = path)
}
