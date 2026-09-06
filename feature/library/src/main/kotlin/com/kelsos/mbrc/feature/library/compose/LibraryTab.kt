package com.kelsos.mbrc.feature.library.compose

import com.kelsos.mbrc.core.common.settings.LibraryView
import com.kelsos.mbrc.feature.library.R

enum class LibraryTab(val view: LibraryView, val titleResId: Int) {
  GENRES(LibraryView.GENRES, R.string.media__genres),
  ARTISTS(LibraryView.ARTISTS, R.string.media__artists),
  ALBUMS(LibraryView.ALBUMS, R.string.media__albums),
  TRACKS(LibraryView.TRACKS, R.string.media__tracks),
  YEARS(LibraryView.YEARS, R.string.media__years),
  LANGUAGES(LibraryView.LANGUAGES, R.string.media__languages);

  companion object {
    fun enabled(enabledViews: Set<LibraryView>): List<LibraryTab> =
      entries.filter { it.view in enabledViews }.ifEmpty {
        entries.filter { it.view in LibraryView.DEFAULT }
      }
  }
}
