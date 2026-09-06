package com.kelsos.mbrc.core.data.library.grouping

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo

@Immutable
data class DecadeGroup(val decade: Int, @ColumnInfo(name = "album_count") val albumCount: Int)

@Immutable
data class YearGroup(val year: String, @ColumnInfo(name = "album_count") val albumCount: Int)

@Immutable
data class LanguageGroup(
  val language: String,
  @ColumnInfo(name = "album_count") val albumCount: Int
)

@Immutable
data class LanguageCategoryGroup(
  val category: String,
  @ColumnInfo(name = "album_count") val albumCount: Int
)
