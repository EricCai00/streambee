@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.kelsos.mbrc.feature.library.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kelsos.mbrc.core.common.test.testDispatcher
import com.kelsos.mbrc.core.common.test.testDispatchers
import com.kelsos.mbrc.core.data.library.album.AlbumRepository
import com.kelsos.mbrc.core.data.library.track.TrackRepository
import com.kelsos.mbrc.core.networking.api.LibraryApi
import com.kelsos.mbrc.core.networking.dto.AlbumCoverDto
import com.kelsos.mbrc.core.networking.dto.CoverDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.File
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoverCacheTest {
  @Test
  fun albumEndpointMissFallsBackToTrackArtworkAndCachesThumbnail() = runTest(testDispatcher) {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val albumRepository = mockk<AlbumRepository>(relaxed = true)
    val libraryApi = mockk<LibraryApi>()
    val trackRepository = mockk<TrackRepository>()
    val highResolutionCoverCache = mockk<HighResolutionCoverCache>()
    val cache = CoverCache(
      albumRepository = albumRepository,
      libraryApi = libraryApi,
      trackRepository = trackRepository,
      highResolutionCoverCache = highResolutionCoverCache,
      dispatchers = testDispatchers,
      app = application
    )

    val key = "fallback-${UUID.randomUUID()}"
    val original = File(application.cacheDir, "original-${UUID.randomUUID()}")
    val bytes = byteArrayOf(1, 2, 3, 4)
    original.writeBytes(bytes)

    coEvery { libraryApi.getCover(AlbumCoverDto("Artist", "Album", null)) } returns
      CoverDto(status = 404, cover = null, hash = null)
    coEvery {
      highResolutionCoverCache.load("Album", "Artist", key, trackRepository)
    } returns original

    try {
      val result = cache.load("Artist", "Album", key)

      assertThat(result).isNotNull()
      assertThat(result!!.readBytes()).isEqualTo(bytes)
      coVerify(exactly = 1) {
        highResolutionCoverCache.load("Album", "Artist", key, trackRepository)
      }
    } finally {
      resultFile(application, key).delete()
      File(File(application.cacheDir, "covers"), "$key.tmp").delete()
      original.delete()
    }
  }

  private fun resultFile(application: Application, key: String): File =
    File(File(application.cacheDir, "covers"), key)
}
