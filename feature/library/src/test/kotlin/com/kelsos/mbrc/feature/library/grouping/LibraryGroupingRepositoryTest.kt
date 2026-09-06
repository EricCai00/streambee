@file:OptIn(ExperimentalCoroutinesApi::class)

package com.kelsos.mbrc.feature.library.grouping

import androidx.paging.testing.asSnapshot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kelsos.mbrc.core.common.settings.AlbumSortField
import com.kelsos.mbrc.core.common.settings.SortOrder
import com.kelsos.mbrc.core.common.test.testDispatcher
import com.kelsos.mbrc.core.common.test.testDispatcherModule
import com.kelsos.mbrc.core.data.Database
import com.kelsos.mbrc.core.data.library.album.AlbumEntity
import com.kelsos.mbrc.core.data.library.genre.GenreEntity
import com.kelsos.mbrc.core.data.library.grouping.LibraryGroupingRepository
import com.kelsos.mbrc.core.data.library.track.TrackEntity
import com.kelsos.mbrc.core.data.test.testDatabaseModule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject

@RunWith(AndroidJUnit4::class)
class LibraryGroupingRepositoryTest : KoinTest {
  private val testModule = module {
    singleOf(::LibraryGroupingRepositoryImpl) { bind<LibraryGroupingRepository>() }
  }

  private val database: Database by inject()
  private val repository: LibraryGroupingRepository by inject()

  @Before
  fun setUp() {
    startKoin { modules(testModule, testDatabaseModule, testDispatcherModule) }
    database.genreDao().insertAll(
      listOf(
        GenreEntity(genre = "Rock", category = "Rock"),
        GenreEntity(genre = "Synthpop", category = "Pop"),
        GenreEntity(genre = "Ambient")
      )
    )
    database.albumDao().insert(
      listOf(
        AlbumEntity(artist = "Artist A", album = "First Album"),
        AlbumEntity(artist = "Artist B", album = "Second Album"),
        AlbumEntity(artist = "Artist C", album = "Third Album"),
        AlbumEntity(artist = "Artist D", album = "Mystery Album")
      )
    )
    database.trackDao().insertAll(
      listOf(
        track(
          artist = "Artist A",
          album = "First Album",
          src = "first-1.flac",
          genre = "Rock",
          year = "1965",
          language = "English; French"
        ),
        track(
          artist = "Artist A",
          album = "First Album",
          src = "first-2.flac",
          genre = "Rock",
          year = "1965",
          language = "English"
        ),
        track(
          artist = "Artist B",
          album = "Second Album",
          src = "second.flac",
          genre = "Synthpop",
          year = "1968",
          language = "French"
        ),
        track(
          artist = "Artist C",
          album = "Third Album",
          src = "third.flac",
          genre = "Rock",
          year = "1972",
          language = "Japanese"
        ),
        track(
          artist = "Artist D",
          album = "Mystery Album",
          src = "mystery.flac",
          genre = "Ambient",
          year = "",
          language = ""
        )
      )
    )
  }

  @After
  fun tearDown() {
    database.close()
    stopKoin()
  }

  @Test
  fun `year hierarchy groups albums by decade then year`() = runTest(testDispatcher) {
    val decades = repository.getDecades("", SortOrder.DESC).asSnapshot()
    val years = repository.getYears(1960, SortOrder.DESC).asSnapshot()

    assertThat(decades.map { it.decade }).containsExactly(1970, 1960, -1).inOrder()
    assertThat(decades.map { it.albumCount }).containsExactly(1, 2, 1).inOrder()
    assertThat(years.map { it.year }).containsExactly("1968", "1965").inOrder()
    assertThat(years.map { it.albumCount }).containsExactly(1, 1).inOrder()
  }

  @Test
  fun `language hierarchy splits multiple values and counts each album once`() =
    runTest(testDispatcher) {
      val languages = repository.getLanguages("", SortOrder.ASC).asSnapshot()
      val englishCategories =
        repository.getLanguageCategories("English", SortOrder.ASC).asSnapshot()
      val unknownCategories = repository.getLanguageCategories("", SortOrder.ASC).asSnapshot()

      assertThat(languages.map { it.language })
        .containsExactly("English", "French", "Japanese", "")
        .inOrder()
      assertThat(languages.map { it.albumCount }).containsExactly(1, 2, 1, 1).inOrder()
      assertThat(englishCategories.map { it.category }).containsExactly("Rock")
      assertThat(unknownCategories.map { it.category }).containsExactly("")
    }

  @Test
  fun `leaf queries return only albums in the selected year or language category`() =
    runTest(testDispatcher) {
      val yearAlbums = repository.getAlbumsByYear(
        year = "1965",
        field = AlbumSortField.NAME,
        order = SortOrder.ASC
      ).asSnapshot()
      val frenchRockAlbums = repository.getAlbumsByLanguageCategory(
        language = "French",
        category = "Rock",
        field = AlbumSortField.NAME,
        order = SortOrder.ASC
      ).asSnapshot()
      val frenchPopAlbums = repository.getAlbumsByLanguageCategory(
        language = "French",
        category = "Pop",
        field = AlbumSortField.NAME,
        order = SortOrder.ASC
      ).asSnapshot()

      assertThat(yearAlbums.map { it.album }).containsExactly("First Album")
      assertThat(frenchRockAlbums.map { it.album }).containsExactly("First Album")
      assertThat(frenchPopAlbums.map { it.album }).containsExactly("Second Album")
    }

  private fun track(
    artist: String,
    album: String,
    src: String,
    genre: String,
    year: String,
    language: String
  ) = TrackEntity(
    artist = artist,
    title = src,
    src = src,
    trackno = 1,
    disc = 1,
    albumArtist = artist,
    album = album,
    genre = genre,
    year = year,
    sortableYear = year,
    language = language
  )
}
