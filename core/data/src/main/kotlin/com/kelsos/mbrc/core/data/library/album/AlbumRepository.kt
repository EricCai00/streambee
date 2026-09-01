package com.kelsos.mbrc.core.data.library.album

import androidx.paging.PagingData
import com.kelsos.mbrc.core.common.settings.AlbumSortField
import com.kelsos.mbrc.core.common.settings.SortOrder
import com.kelsos.mbrc.core.data.Repository
import kotlinx.coroutines.flow.Flow

interface AlbumRepository : Repository<Album> {
  fun getAlbumsByArtist(
    artist: String,
    field: AlbumSortField,
    order: SortOrder
  ): Flow<PagingData<Album>>

  fun getAlbumsByGenre(
    genreId: Long,
    field: AlbumSortField,
    order: SortOrder
  ): Flow<PagingData<Album>>

  /** Returns the small set of covers used as a visual preview for a genre. */
  suspend fun getPreviewAlbumsByGenre(genreId: Long, limit: Int = 3): List<Album>

  /** Returns the small set of covers used as a visual preview for a genre category. */
  suspend fun getPreviewAlbumsByCategory(category: String, limit: Int = 3): List<Album>

  fun getAll(field: AlbumSortField, order: SortOrder): Flow<PagingData<Album>>

  fun search(term: String, field: AlbumSortField, order: SortOrder): Flow<PagingData<Album>>

  suspend fun updateCovers(updated: List<AlbumCover>)

  suspend fun getCovers(): List<AlbumCover>

  suspend fun coverCount(): Long
}
