package com.kelsos.mbrc.core.common.data

/**
 * Splits a MusicBee artist value into its individual artist names.
 *
 * MusicBee uses a semicolon to separate multiple artists. Empty entries are
 * ignored and names are trimmed so values such as "Artist A; Artist B" can be
 * used consistently by library queries and navigation.
 */
fun String.splitArtistNames(): List<String> = split(';')
  .map(String::trim)
  .filter(String::isNotEmpty)
  .distinct()
