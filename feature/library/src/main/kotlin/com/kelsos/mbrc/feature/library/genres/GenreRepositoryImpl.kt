package com.kelsos.mbrc.feature.library.genres

import androidx.paging.PagingData
import com.kelsos.mbrc.core.common.data.Progress
import com.kelsos.mbrc.core.common.settings.SortOrder
import com.kelsos.mbrc.core.common.utilities.coroutines.AppCoroutineDispatchers
import com.kelsos.mbrc.core.common.utilities.epoch
import com.kelsos.mbrc.core.data.library.genre.Genre
import com.kelsos.mbrc.core.data.library.genre.GenreCategory
import com.kelsos.mbrc.core.data.library.genre.GenreDao
import com.kelsos.mbrc.core.data.library.genre.GenreRepository
import com.kelsos.mbrc.core.data.paged
import com.kelsos.mbrc.core.networking.api.LibraryApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext

class GenreRepositoryImpl(
  private val libraryApi: LibraryApi,
  private val dao: GenreDao,
  private val dispatchers: AppCoroutineDispatchers
) : GenreRepository {
  override suspend fun count(): Long = withContext(dispatchers.database) { dao.count() }

  override fun getAll(): Flow<PagingData<Genre>> = getAll(SortOrder.ASC)

  override fun getAll(sortOrder: SortOrder): Flow<PagingData<Genre>> = paged({
    when (sortOrder) {
      SortOrder.ASC -> dao.getAllAsc()
      SortOrder.DESC -> dao.getAllDesc()
    }
  }, enablePlaceholders = true) { it.toGenre() }

  override fun getCategories(sortOrder: SortOrder): Flow<PagingData<GenreCategory>> = paged({
    when (sortOrder) {
      SortOrder.ASC -> dao.getCategoriesAsc()
      SortOrder.DESC -> dao.getCategoriesDesc()
    }
  }, enablePlaceholders = true) { it }

  override fun searchCategories(
    term: String,
    sortOrder: SortOrder
  ): Flow<PagingData<GenreCategory>> = paged({
    when (sortOrder) {
      SortOrder.ASC -> dao.searchCategoriesAsc(term)
      SortOrder.DESC -> dao.searchCategoriesDesc(term)
    }
  }, enablePlaceholders = true) { it }

  override fun getByCategory(category: String, sortOrder: SortOrder): Flow<PagingData<Genre>> =
    paged({
      when (sortOrder) {
        SortOrder.ASC -> dao.getByCategoryAsc(category)
        SortOrder.DESC -> dao.getByCategoryDesc(category)
      }
    }, enablePlaceholders = true) { it.toGenre() }

  override suspend fun getRemote(progress: Progress?) {
    withContext(dispatchers.network) {
      val added = epoch()
      val stored =
        withContext(dispatchers.database) {
          dao.genres().associate { it.genre to it.id }
        }
      val allPages = libraryApi.getGenres(progress)

      allPages
        .onCompletion {
          withContext(dispatchers.database) {
            dao.removePreviousEntries(added)
          }
        }.collect { genres ->
          val items =
            genres.map {
              val id = stored[it.genre]
              if (id != null) {
                it.toEntity().copy(dateAdded = added, id = id)
              } else {
                it.toEntity().copy(dateAdded = added)
              }
            }
          withContext(dispatchers.database) {
            dao.insertAll(items.filter { it.id == 0L })
            dao.update(items.filter { it.id != 0L })
          }
        }
    }
  }

  override fun search(term: String): Flow<PagingData<Genre>> = search(term, SortOrder.ASC)

  override fun search(term: String, sortOrder: SortOrder): Flow<PagingData<Genre>> = paged({
    when (sortOrder) {
      SortOrder.ASC -> dao.searchAsc(term)
      SortOrder.DESC -> dao.searchDesc(term)
    }
  }, enablePlaceholders = true) { it.toGenre() }

  override suspend fun getById(id: Long): Genre? {
    return withContext(dispatchers.database) {
      val entity = dao.getById(id) ?: return@withContext null
      return@withContext entity.toGenre()
    }
  }

  override suspend fun getByName(name: String): Genre? = withContext(dispatchers.database) {
    dao.getByName(name)?.toGenre()
  }
}
