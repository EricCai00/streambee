package com.kelsos.mbrc.core.data.history

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A completed listen produced by playback on the Android device. */
@Immutable
@Entity(
  tableName = "playback_history",
  indices = [
    Index(value = ["played_at"], name = "playback_history_played_at_idx"),
    Index(value = ["path"], name = "playback_history_path_idx")
  ]
)
data class PlaybackHistoryEntry(
  @ColumnInfo
  val title: String,
  @ColumnInfo
  val artist: String,
  @ColumnInfo
  val album: String,
  @ColumnInfo(name = "album_artist")
  val albumArtist: String,
  @ColumnInfo
  val path: String,
  @ColumnInfo(name = "duration_ms")
  val durationMs: Long,
  @ColumnInfo(name = "listened_ms")
  val listenedMs: Long,
  @ColumnInfo(name = "started_at")
  val startedAt: Long,
  @ColumnInfo(name = "played_at")
  val playedAt: Long,
  @ColumnInfo(name = "scrobble_requested")
  val scrobbleRequested: Boolean,
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0
)
