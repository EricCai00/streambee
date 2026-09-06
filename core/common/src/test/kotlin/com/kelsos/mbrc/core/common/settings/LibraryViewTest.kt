package com.kelsos.mbrc.core.common.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LibraryViewTest {
  @Test
  fun `default views preserve the original four library tabs`() {
    assertThat(LibraryView.DEFAULT)
      .containsExactly(
        LibraryView.GENRES,
        LibraryView.ARTISTS,
        LibraryView.ALBUMS,
        LibraryView.TRACKS
      ).inOrder()
  }

  @Test
  fun `decode keeps canonical tab order and ignores unknown values`() {
    val decoded = LibraryView.decode(setOf("languages", "genres", "future"))

    assertThat(decoded)
      .containsExactly(LibraryView.GENRES, LibraryView.LANGUAGES)
      .inOrder()
  }

  @Test
  fun `decode falls back to defaults when no known view remains`() {
    assertThat(LibraryView.decode(setOf("future"))).isEqualTo(LibraryView.DEFAULT)
  }
}
