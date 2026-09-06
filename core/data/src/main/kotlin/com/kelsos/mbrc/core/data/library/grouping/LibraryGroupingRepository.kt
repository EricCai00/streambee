package com.kelsos.mbrc.core.data.library.grouping

import androidx.paging.PagingData
import com.kelsos.mbrc.core.common.settings.AlbumSortField
import com.kelsos.mbrc.core.common.settings.SortOrder
import com.kelsos.mbrc.core.data.library.album.Album
import kotlinx.coroutines.flow.Flow

interface LibraryGroupingRepository {
  fun getDecades(term: String, order: SortOrder): Flow<PagingData<DecadeGroup>>

  fun getYears(decade: Int, order: SortOrder): Flow<PagingData<YearGroup>>

  fun getLanguages(term: String, order: SortOrder): Flow<PagingData<LanguageGroup>>

  fun getLanguageCategories(
    language: String,
    order: SortOrder
  ): Flow<PagingData<LanguageCategoryGroup>>

  fun getAlbumsByYear(
    year: String,
    field: AlbumSortField,
    order: SortOrder
  ): Flow<PagingData<Album>>

  fun getAlbumsByLanguageCategory(
    language: String,
    category: String,
    field: AlbumSortField,
    order: SortOrder
  ): Flow<PagingData<Album>>
}
