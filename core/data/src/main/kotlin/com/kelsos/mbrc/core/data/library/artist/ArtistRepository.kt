package com.kelsos.mbrc.core.data.library.artist

import androidx.paging.PagingData
import com.kelsos.mbrc.core.common.settings.SortOrder
import com.kelsos.mbrc.core.data.Repository
import kotlinx.coroutines.flow.Flow

interface ArtistRepository : Repository<Artist> {
  fun getArtistByGenre(genreId: Long, sortOrder: SortOrder): Flow<PagingData<Artist>>

  /** Emits the complete genre artist list in one database result. */
  fun getArtistByGenreList(genreId: Long, sortOrder: SortOrder): Flow<List<Artist>>

  fun getAll(sortOrder: SortOrder): Flow<PagingData<Artist>>

  fun getAlbumArtistsOnly(sortOrder: SortOrder): Flow<PagingData<Artist>>

  /** Emits all album artists in one database result; artwork is loaded separately by the UI. */
  fun getAlbumArtistsOnlyList(sortOrder: SortOrder): Flow<List<Artist>>

  fun searchAlbumArtists(term: String, sortOrder: SortOrder): Flow<PagingData<Artist>>

  /** Emits all matching album artists in one database result. */
  fun searchAlbumArtistsList(term: String, sortOrder: SortOrder): Flow<List<Artist>>

  fun search(term: String, sortOrder: SortOrder): Flow<PagingData<Artist>>
}
