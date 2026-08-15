package com.kelsos.mbrc.feature.library.queue

import com.google.common.truth.Truth.assertThat
import com.kelsos.mbrc.core.common.settings.LibrarySettings
import com.kelsos.mbrc.core.common.test.testDispatcher
import com.kelsos.mbrc.core.common.test.testDispatchers
import com.kelsos.mbrc.core.data.library.track.Track
import com.kelsos.mbrc.core.data.library.track.TrackRepository
import com.kelsos.mbrc.core.networking.api.QueueApi
import com.kelsos.mbrc.core.networking.dto.QueueResponse
import com.kelsos.mbrc.core.queue.Queue
import com.kelsos.mbrc.feature.library.playback.DevicePlaybackController
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class QueueHandlerTest {
  private val settings = mockk<LibrarySettings>(relaxed = true)
  private val trackRepository = mockk<TrackRepository>(relaxed = true)
  private val queueApi = mockk<QueueApi>(relaxed = true)
  private val devicePlayback = mockk<DevicePlaybackController>(relaxed = true)
  private val handler = QueueHandler(
    settings,
    trackRepository,
    queueApi,
    devicePlayback,
    testDispatchers
  )
  private val track = Track(
    artist = "Artist",
    title = "Title",
    src = "D:\\Music\\track.flac",
    trackno = 1,
    disc = 1,
    albumArtist = "Artist",
    album = "Album",
    genre = "Genre",
    year = "2026",
    id = 1
  )

  @Test
  fun `local playback uses device player without queueing MusicBee`() = runTest(testDispatcher) {
    coEvery { devicePlayback.playTracks(listOf(track), 0) } returns true

    val result = handler.queueTrack(track, Queue.Local)

    assertThat(result.isSuccess).isTrue()
    coVerify(exactly = 1) { devicePlayback.playTracks(listOf(track), 0) }
    coVerify(exactly = 0) { devicePlayback.stop() }
    coVerify(exactly = 0) { queueApi.queue(any()) }
  }

  @Test
  fun `local playback failure is reported without escaping the coroutine`() =
    runTest(testDispatcher) {
      coEvery { devicePlayback.playTracks(listOf(track), 0) } throws IllegalStateException("player unavailable")

      val result = handler.queueTrack(track, Queue.Local)

      assertThat(result.isFailure).isTrue()
      coVerify(exactly = 1) { devicePlayback.playTracks(listOf(track), 0) }
      coVerify(exactly = 0) { queueApi.queue(any()) }
    }

  @Test
  fun `now playback uses device player without queueing MusicBee`() = runTest(testDispatcher) {
    coEvery { devicePlayback.playTracks(listOf(track), 0) } returns true

    val result = handler.queueTrack(track, Queue.Now)

    assertThat(result.isSuccess).isTrue()
    coVerify(exactly = 1) { devicePlayback.playTracks(listOf(track), 0) }
    coVerify(exactly = 0) { queueApi.queue(any()) }
  }

  @Test
  fun `album playback uses device player without queueing MusicBee`() =
    runTest(testDispatcher) {
      every { trackRepository.getTrackPaths(any()) } returns listOf(track.src)
      coEvery { trackRepository.getByPath(track.src) } returns track
      coEvery { devicePlayback.playTracks(listOf(track), 0) } returns true

      val result = handler.queueAlbum(Queue.Now, track.album, track.albumArtist)

      assertThat(result.isSuccess).isTrue()
      coVerify(exactly = 1) { devicePlayback.playTracks(listOf(track), 0) }
      coVerify(exactly = 0) { queueApi.queue(any()) }
    }
}
