package com.kelsos.mbrc.feature.library.compose

import com.google.common.truth.Truth.assertThat
import com.kelsos.mbrc.core.common.settings.LibraryView
import org.junit.Test

class LibraryTabTest {
  @Test
  fun `enabled includes selected views in canonical order`() {
    val tabs = LibraryTab.enabled(
      setOf(LibraryView.LANGUAGES, LibraryView.ALBUMS, LibraryView.YEARS)
    )

    assertThat(tabs)
      .containsExactly(LibraryTab.ALBUMS, LibraryTab.YEARS, LibraryTab.LANGUAGES)
      .inOrder()
  }

  @Test
  fun `enabled falls back to original tabs for an empty selection`() {
    assertThat(LibraryTab.enabled(emptySet()))
      .containsExactly(
        LibraryTab.GENRES,
        LibraryTab.ARTISTS,
        LibraryTab.ALBUMS,
        LibraryTab.TRACKS
      ).inOrder()
  }
}
