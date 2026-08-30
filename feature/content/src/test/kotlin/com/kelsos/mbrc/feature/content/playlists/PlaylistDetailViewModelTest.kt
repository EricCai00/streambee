package com.kelsos.mbrc.feature.content.playlists

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kelsos.mbrc.core.common.state.ConnectionStateFlow
import com.kelsos.mbrc.core.common.test.testDispatcher
import com.kelsos.mbrc.core.common.test.testDispatcherModule
import com.kelsos.mbrc.core.common.utilities.Outcome
import com.kelsos.mbrc.core.data.library.track.Track
import com.kelsos.mbrc.core.data.library.track.TrackRepository
import com.kelsos.mbrc.core.data.playlist.PlaylistRepository
import com.kelsos.mbrc.core.queue.PathQueueUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
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
@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailViewModelTest : KoinTest {
  private val outsideLibraryTrack = Track(
    artist = "Artist",
    title = "Outside Library",
    src = "D:\\Playlists\\outside.flac",
    trackno = 1,
    disc = 1,
    albumArtist = "Artist",
    album = "Playlist Album",
    genre = "",
    year = "2026",
    id = 0
  )

  private val testModule = module {
    single<PlaylistRepository> { mockk(relaxed = true) }
    single<TrackRepository> { mockk(relaxed = true) }
    single<PathQueueUseCase> { mockk(relaxed = true) }
    single<ConnectionStateFlow> { mockk(relaxed = true) }
    singleOf(::PlaylistDetailViewModel)
  }

  private val viewModel: PlaylistDetailViewModel by inject()
  private val playlistRepository: PlaylistRepository by inject()
  private val trackRepository: TrackRepository by inject()
  private val queueUseCase: PathQueueUseCase by inject()
  private val connectionStateFlow: ConnectionStateFlow by inject()

  @Before
  fun setUp() {
    startKoin { modules(testModule, testDispatcherModule) }
    coEvery { connectionStateFlow.isConnected } returns true
  }

  @After
  fun tearDown() {
    stopKoin()
  }

  @Test
  fun loadKeepsTracksThatCannotBeResolvedFromLibrary() = runTest(testDispatcher) {
    coEvery { playlistRepository.getTracks("mixed") } returns listOf(outsideLibraryTrack)
    coEvery { trackRepository.getByPath(outsideLibraryTrack.src) } returns null

    viewModel.load("mixed")
    testDispatcher.scheduler.advanceUntilIdle()

    assertThat(viewModel.loaded.value).isTrue()
    assertThat(viewModel.tracks.value).containsExactly(outsideLibraryTrack)
  }

  @Test
  fun playQueuesFullPlaylistMetadataAtSelectedIndex() = runTest(testDispatcher) {
    coEvery { playlistRepository.getTracks("mixed") } returns listOf(outsideLibraryTrack)
    coEvery { trackRepository.getByPath(outsideLibraryTrack.src) } returns null
    coEvery { queueUseCase.queueTracks(listOf(outsideLibraryTrack), 0) } returns Outcome.Success(1)
    viewModel.load("mixed")
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.play(0)
    testDispatcher.scheduler.advanceUntilIdle()

    coVerify(exactly = 1) { queueUseCase.queueTracks(listOf(outsideLibraryTrack), 0) }
  }

  @Test
  fun playExposesSelectedTrackAsStartingUntilQueueAcceptsIt() = runTest(testDispatcher) {
    coEvery { playlistRepository.getTracks("mixed") } returns listOf(outsideLibraryTrack)
    coEvery { trackRepository.getByPath(outsideLibraryTrack.src) } returns null
    coEvery { queueUseCase.queueTracks(listOf(outsideLibraryTrack), 0) } coAnswers {
      delay(1_000L)
      Outcome.Success(1)
    }
    viewModel.load("mixed")
    testDispatcher.scheduler.advanceUntilIdle()

    viewModel.play(0)
    runCurrent()

    assertThat(viewModel.startingTrackIndex.value).isEqualTo(0)

    advanceTimeBy(1_000L)
    runCurrent()

    assertThat(viewModel.startingTrackIndex.value).isNull()
  }
}
