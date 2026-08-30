package com.kelsos.mbrc.feature.library.genres

import com.kelsos.mbrc.core.common.mvvm.UiMessageBase
import com.kelsos.mbrc.core.data.library.genre.Genre
import com.kelsos.mbrc.core.data.library.genre.GenreCategory

sealed class GenreUiMessage : UiMessageBase {
  data class OpenGenres(val category: GenreCategory) : GenreUiMessage()

  data class OpenAlbums(val genre: Genre) : GenreUiMessage()

  data class QueueSuccess(val tracksCount: Int) : GenreUiMessage()

  object QueueFailed : GenreUiMessage()

  object NetworkUnavailable : GenreUiMessage()
}
