package com.kelsos.mbrc.core.data.library.genre

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface GenreDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  fun insertAll(list: List<GenreEntity>)

  @Update
  fun update(list: List<GenreEntity>)

  @Query("select * from genre order by genre collate nocase asc")
  fun getAllAsc(): PagingSource<Int, GenreEntity>

  @Query("select * from genre order by genre collate nocase desc")
  fun getAllDesc(): PagingSource<Int, GenreEntity>

  @Query("select category from genre group by category order by category collate nocase asc")
  fun getCategoriesAsc(): PagingSource<Int, GenreCategory>

  @Query("select category from genre group by category order by category collate nocase desc")
  fun getCategoriesDesc(): PagingSource<Int, GenreCategory>

  @Query(
    """
    select category from genre
    where category like '%' || :term || '%'
    group by category
    order by category collate nocase asc
    """
  )
  fun searchCategoriesAsc(term: String): PagingSource<Int, GenreCategory>

  @Query(
    """
    select category from genre
    where category like '%' || :term || '%'
    group by category
    order by category collate nocase desc
    """
  )
  fun searchCategoriesDesc(term: String): PagingSource<Int, GenreCategory>

  @Query(
    "select * from genre where category = :category order by genre collate nocase asc"
  )
  fun getByCategoryAsc(category: String): PagingSource<Int, GenreEntity>

  @Query(
    "select * from genre where category = :category order by genre collate nocase desc"
  )
  fun getByCategoryDesc(category: String): PagingSource<Int, GenreEntity>

  @Query("select * from genre order by genre collate nocase")
  fun all(): List<GenreEntity>

  @Query("select id, genre, category from genre order by genre collate nocase")
  fun genres(): List<Genre>

  @Query(
    """
    select * from genre
    where genre like '%' || :term || '%'
    order by genre collate nocase asc
    """
  )
  fun searchAsc(term: String): PagingSource<Int, GenreEntity>

  @Query(
    """
    select * from genre
    where genre like '%' || :term || '%'
    order by genre collate nocase desc
    """
  )
  fun searchDesc(term: String): PagingSource<Int, GenreEntity>

  @Query("select count(*) from genre")
  fun count(): Long

  @Query("delete from genre where date_added < :added")
  fun removePreviousEntries(added: Long)

  @Query("select * from genre where id = :id")
  fun getById(id: Long): GenreEntity?

  @Query("select * from genre where genre = :name collate nocase limit 1")
  fun getByName(name: String): GenreEntity?
}
