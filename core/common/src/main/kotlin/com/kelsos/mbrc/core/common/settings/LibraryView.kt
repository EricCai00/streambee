package com.kelsos.mbrc.core.common.settings

/** Library tabs that can be shown on the main Library screen. */
enum class LibraryView(val value: String) {
  GENRES("genres"),
  ARTISTS("artists"),
  ALBUMS("albums"),
  TRACKS("tracks"),
  YEARS("years"),
  LANGUAGES("languages");

  companion object {
    /** Preserve the four tabs that existed before configurable library views. */
    val DEFAULT: Set<LibraryView> = linkedSetOf(GENRES, ARTISTS, ALBUMS, TRACKS)

    fun decode(values: Set<String>): Set<LibraryView> =
      entries.filterTo(linkedSetOf()) { it.value in values }.ifEmpty { DEFAULT }
  }
}
