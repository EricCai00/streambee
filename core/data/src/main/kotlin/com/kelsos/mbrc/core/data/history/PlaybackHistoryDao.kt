package com.kelsos.mbrc.core.data.history

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PlaybackHistoryDao {
  @Insert
  suspend fun insert(entry: PlaybackHistoryEntry): Long

  @Query("select * from playback_history order by played_at desc, id desc")
  fun getAll(): PagingSource<Int, PlaybackHistoryEntry>

  @Query("delete from playback_history")
  suspend fun deleteAll()

  @Query("select count(*) from playback_history")
  suspend fun count(): Long
}
