package com.kelsos.mbrc.core.data.library.grouping

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import com.kelsos.mbrc.core.data.library.album.AlbumBrowseRow

@Dao
interface LibraryGroupingDao {
  @Query(
    """
    WITH album_years AS (
      SELECT album.id AS album_id,
        COALESCE(MAX(NULLIF(track.sortable_year, '')), '') AS year
      FROM album
      INNER JOIN track
        ON album.album = track.album AND album.artist = track.album_artist
      WHERE album.album <> ''
      GROUP BY album.id
    ), grouped AS (
      SELECT CASE WHEN year = '' THEN -1 ELSE (CAST(year AS INTEGER) / 10) * 10 END AS decade,
        COUNT(*) AS album_count
      FROM album_years
      WHERE year LIKE '%' || :term || '%'
      GROUP BY CASE WHEN year = '' THEN -1 ELSE (CAST(year AS INTEGER) / 10) * 10 END
    )
    SELECT decade, album_count
    FROM grouped
    ORDER BY
      CASE WHEN decade = -1 THEN 1 ELSE 0 END,
      CASE WHEN :descending THEN decade END DESC,
      CASE WHEN NOT :descending THEN decade END ASC
    """
  )
  fun getDecades(term: String, descending: Boolean): PagingSource<Int, DecadeGroup>

  @Query(
    """
    WITH album_years AS (
      SELECT album.id AS album_id,
        COALESCE(MAX(NULLIF(track.sortable_year, '')), '') AS year
      FROM album
      INNER JOIN track
        ON album.album = track.album AND album.artist = track.album_artist
      WHERE album.album <> ''
      GROUP BY album.id
    ), grouped AS (
      SELECT year, COUNT(*) AS album_count
      FROM album_years
      WHERE CASE
        WHEN year = '' THEN -1
        ELSE (CAST(year AS INTEGER) / 10) * 10
      END = :decade
      GROUP BY year
    )
    SELECT year, album_count
    FROM grouped
    ORDER BY
      CASE WHEN year = '' THEN 1 ELSE 0 END,
      CASE WHEN :descending THEN year END DESC,
      CASE WHEN NOT :descending THEN year END ASC
    """
  )
  fun getYears(decade: Int, descending: Boolean): PagingSource<Int, YearGroup>

  @Query(
    """
    WITH RECURSIVE language_parts(album_id, language, remaining, source_empty) AS (
      SELECT album.id,
        TRIM(SUBSTR(RTRIM(track.language, '; ') || ';', 1,
          INSTR(RTRIM(track.language, '; ') || ';', ';') - 1)),
        SUBSTR(RTRIM(track.language, '; ') || ';',
          INSTR(RTRIM(track.language, '; ') || ';', ';') + 1),
        CASE WHEN TRIM(track.language) = '' THEN 1 ELSE 0 END
      FROM album
      INNER JOIN track
        ON album.album = track.album AND album.artist = track.album_artist
      WHERE album.album <> ''
      UNION ALL
      SELECT album_id,
        TRIM(SUBSTR(remaining, 1, INSTR(remaining, ';') - 1)),
        SUBSTR(remaining, INSTR(remaining, ';') + 1),
        source_empty
      FROM language_parts
      WHERE remaining <> ''
    ), album_languages AS (
      SELECT DISTINCT album_id, language
      FROM language_parts
      WHERE language <> '' OR source_empty = 1
    ), grouped AS (
      SELECT language, COUNT(*) AS album_count
      FROM album_languages
      WHERE language LIKE '%' || :term || '%'
      GROUP BY language
    )
    SELECT language, album_count
    FROM grouped
    ORDER BY
      CASE WHEN language = '' THEN 1 ELSE 0 END,
      CASE WHEN NOT :descending THEN language END COLLATE NOCASE ASC,
      CASE WHEN :descending THEN language END COLLATE NOCASE DESC
    """
  )
  fun getLanguages(term: String, descending: Boolean): PagingSource<Int, LanguageGroup>

  @Query(
    """
    WITH RECURSIVE language_parts(album_id, genre_name, language, remaining, source_empty) AS (
      SELECT album.id, track.genre,
        TRIM(SUBSTR(RTRIM(track.language, '; ') || ';', 1,
          INSTR(RTRIM(track.language, '; ') || ';', ';') - 1)),
        SUBSTR(RTRIM(track.language, '; ') || ';',
          INSTR(RTRIM(track.language, '; ') || ';', ';') + 1),
        CASE WHEN TRIM(track.language) = '' THEN 1 ELSE 0 END
      FROM album
      INNER JOIN track
        ON album.album = track.album AND album.artist = track.album_artist
      WHERE album.album <> ''
      UNION ALL
      SELECT album_id, genre_name,
        TRIM(SUBSTR(remaining, 1, INSTR(remaining, ';') - 1)),
        SUBSTR(remaining, INSTR(remaining, ';') + 1),
        source_empty
      FROM language_parts
      WHERE remaining <> ''
    ), album_categories AS (
      SELECT DISTINCT language_parts.album_id,
        COALESCE(NULLIF(TRIM(genre.category), ''), '') AS category
      FROM language_parts
      LEFT JOIN genre ON LOWER(genre.genre) = LOWER(language_parts.genre_name)
        OR INSTR(
          ';' || LOWER(REPLACE(REPLACE(language_parts.genre_name, '; ', ';'), ' ;', ';')) || ';',
          ';' || LOWER(genre.genre) || ';'
        ) > 0
      WHERE language_parts.language = :language
        AND (language_parts.language <> '' OR language_parts.source_empty = 1)
    ), grouped AS (
      SELECT category, COUNT(*) AS album_count
      FROM album_categories
      GROUP BY category
    )
    SELECT category, album_count
    FROM grouped
    ORDER BY
      CASE WHEN category = '' THEN 1 ELSE 0 END,
      CASE WHEN NOT :descending THEN category END COLLATE NOCASE ASC,
      CASE WHEN :descending THEN category END COLLATE NOCASE DESC
    """
  )
  fun getLanguageCategories(
    language: String,
    descending: Boolean
  ): PagingSource<Int, LanguageCategoryGroup>

  @Query(
    """
    WITH album_years AS (
      SELECT album.id AS album_id,
        COALESCE(MAX(NULLIF(track.sortable_year, '')), '') AS year
      FROM album
      INNER JOIN track
        ON album.album = track.album AND album.artist = track.album_artist
      WHERE album.album <> ''
      GROUP BY album.id
    )
    SELECT album.artist AS artist, album.album AS album, album.cover AS cover,
      album.date_added AS date_added, album.id AS id, album_years.year AS year
    FROM album_years
    INNER JOIN album ON album.id = album_years.album_id
    WHERE album_years.year = :year
    ORDER BY
      CASE WHEN :sortField = 'year' AND album_years.year = '' THEN 1 ELSE 0 END,
      CASE WHEN :sortField = 'year' AND :sortOrder = 'asc' THEN album_years.year END ASC,
      CASE WHEN :sortField = 'year' AND :sortOrder = 'desc' THEN album_years.year END DESC,
      CASE WHEN :sortField = 'artist' AND :sortOrder = 'asc' THEN
        CASE WHEN LOWER(album.artist) LIKE 'the %' THEN SUBSTR(album.artist, 5)
          ELSE album.artist END
      END COLLATE NOCASE ASC,
      CASE WHEN :sortField = 'artist' AND :sortOrder = 'desc' THEN
        CASE WHEN LOWER(album.artist) LIKE 'the %' THEN SUBSTR(album.artist, 5)
          ELSE album.artist END
      END COLLATE NOCASE DESC,
      CASE WHEN :sortField = 'name' AND :sortOrder = 'asc' THEN album.album END COLLATE NOCASE ASC,
      CASE WHEN :sortField = 'name' AND :sortOrder = 'desc' THEN album.album END COLLATE NOCASE DESC,
      album.album COLLATE NOCASE ASC
    """
  )
  fun getAlbumsByYear(
    year: String,
    sortField: String,
    sortOrder: String
  ): PagingSource<Int, AlbumBrowseRow>

  @Query(
    """
    WITH RECURSIVE language_parts(
      album_id, genre_name, sortable_year, language, remaining, source_empty
    ) AS (
      SELECT album.id, track.genre, track.sortable_year,
        TRIM(SUBSTR(RTRIM(track.language, '; ') || ';', 1,
          INSTR(RTRIM(track.language, '; ') || ';', ';') - 1)),
        SUBSTR(RTRIM(track.language, '; ') || ';',
          INSTR(RTRIM(track.language, '; ') || ';', ';') + 1),
        CASE WHEN TRIM(track.language) = '' THEN 1 ELSE 0 END
      FROM album
      INNER JOIN track
        ON album.album = track.album AND album.artist = track.album_artist
      WHERE album.album <> ''
      UNION ALL
      SELECT album_id, genre_name, sortable_year,
        TRIM(SUBSTR(remaining, 1, INSTR(remaining, ';') - 1)),
        SUBSTR(remaining, INSTR(remaining, ';') + 1),
        source_empty
      FROM language_parts
      WHERE remaining <> ''
    ), matching_albums AS (
      SELECT language_parts.album_id,
        COALESCE(MAX(NULLIF(language_parts.sortable_year, '')), '') AS year
      FROM language_parts
      LEFT JOIN genre ON LOWER(genre.genre) = LOWER(language_parts.genre_name)
        OR INSTR(
          ';' || LOWER(REPLACE(REPLACE(language_parts.genre_name, '; ', ';'), ' ;', ';')) || ';',
          ';' || LOWER(genre.genre) || ';'
        ) > 0
      WHERE language_parts.language = :language
        AND (language_parts.language <> '' OR language_parts.source_empty = 1)
        AND COALESCE(NULLIF(TRIM(genre.category), ''), '') = :category
      GROUP BY language_parts.album_id
    )
    SELECT album.artist AS artist, album.album AS album, album.cover AS cover,
      album.date_added AS date_added, album.id AS id, matching_albums.year AS year
    FROM matching_albums
    INNER JOIN album ON album.id = matching_albums.album_id
    ORDER BY
      CASE WHEN :sortField = 'year' AND matching_albums.year = '' THEN 1 ELSE 0 END,
      CASE WHEN :sortField = 'year' AND :sortOrder = 'asc' THEN matching_albums.year END ASC,
      CASE WHEN :sortField = 'year' AND :sortOrder = 'desc' THEN matching_albums.year END DESC,
      CASE WHEN :sortField = 'artist' AND :sortOrder = 'asc' THEN
        CASE WHEN LOWER(album.artist) LIKE 'the %' THEN SUBSTR(album.artist, 5)
          ELSE album.artist END
      END COLLATE NOCASE ASC,
      CASE WHEN :sortField = 'artist' AND :sortOrder = 'desc' THEN
        CASE WHEN LOWER(album.artist) LIKE 'the %' THEN SUBSTR(album.artist, 5)
          ELSE album.artist END
      END COLLATE NOCASE DESC,
      CASE WHEN :sortField = 'name' AND :sortOrder = 'asc' THEN album.album END COLLATE NOCASE ASC,
      CASE WHEN :sortField = 'name' AND :sortOrder = 'desc' THEN album.album END COLLATE NOCASE DESC,
      album.album COLLATE NOCASE ASC
    """
  )
  fun getAlbumsByLanguageCategory(
    language: String,
    category: String,
    sortField: String,
    sortOrder: String
  ): PagingSource<Int, AlbumBrowseRow>
}
