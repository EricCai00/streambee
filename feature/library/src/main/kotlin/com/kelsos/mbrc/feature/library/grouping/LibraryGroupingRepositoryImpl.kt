package com.kelsos.mbrc.feature.library.grouping

import androidx.paging.PagingData
import com.kelsos.mbrc.core.common.settings.AlbumSortField
import com.kelsos.mbrc.core.common.settings.SortOrder
import com.kelsos.mbrc.core.data.library.album.Album
import com.kelsos.mbrc.core.data.library.grouping.DecadeGroup
import com.kelsos.mbrc.core.data.library.grouping.LanguageCategoryGroup
import com.kelsos.mbrc.core.data.library.grouping.LanguageGroup
import com.kelsos.mbrc.core.data.library.grouping.LibraryGroupingDao
import com.kelsos.mbrc.core.data.library.grouping.LibraryGroupingRepository
import com.kelsos.mbrc.core.data.library.grouping.YearGroup
import com.kelsos.mbrc.core.data.paged
import com.kelsos.mbrc.feature.library.albums.toAlbum
import kotlinx.coroutines.flow.Flow

class LibraryGroupingRepositoryImpl(private val dao: LibraryGroupingDao) :
  LibraryGroupingRepository {
  override fun getDecades(term: String, order: SortOrder): Flow<PagingData<DecadeGroup>> = paged(
    pagingSourceFactory = { dao.getDecades(term, order == SortOrder.DESC) },
    enablePlaceholders = true,
    transform = { it }
  )

  override fun getYears(decade: Int, order: SortOrder): Flow<PagingData<YearGroup>> = paged(
    pagingSourceFactory = { dao.getYears(decade, order == SortOrder.DESC) },
    enablePlaceholders = true,
    transform = { it }
  )

  override fun getLanguages(term: String, order: SortOrder): Flow<PagingData<LanguageGroup>> =
    paged(
      pagingSourceFactory = { dao.getLanguages(term, order == SortOrder.DESC) },
      enablePlaceholders = true,
      transform = { it }
    )

  override fun getLanguageCategories(
    language: String,
    order: SortOrder
  ): Flow<PagingData<LanguageCategoryGroup>> = paged(
    pagingSourceFactory = { dao.getLanguageCategories(language, order == SortOrder.DESC) },
    enablePlaceholders = true,
    transform = { it }
  )

  override fun getAlbumsByYear(
    year: String,
    field: AlbumSortField,
    order: SortOrder
  ): Flow<PagingData<Album>> = paged(
    pagingSourceFactory = { dao.getAlbumsByYear(year, field.value, order.value) },
    enablePlaceholders = true,
    transform = { it.toAlbum() }
  )

  override fun getAlbumsByLanguageCategory(
    language: String,
    category: String,
    field: AlbumSortField,
    order: SortOrder
  ): Flow<PagingData<Album>> = paged(
    pagingSourceFactory = {
      dao.getAlbumsByLanguageCategory(language, category, field.value, order.value)
    },
    enablePlaceholders = true,
    transform = { it.toAlbum() }
  )
}
