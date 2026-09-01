package com.kelsos.mbrc.core.networking.protocol.payloads

import com.kelsos.mbrc.core.common.state.TrackDetails
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Payload for the nowplayingdetails protocol message.
 * Contains extended track metadata and file properties.
 */
@JsonClass(generateAdapter = true)
data class NowPlayingDetailsPayload(
  // Tag metadata
  @Json(name = "albumArtist")
  val albumArtist: String = "",
  @Json(name = "genre")
  val genre: String = "",
  @Json(name = "trackNo")
  val trackNo: String = "",
  @Json(name = "trackCount")
  val trackCount: String = "",
  @Json(name = "discNo")
  val discNo: String = "",
  @Json(name = "discCount")
  val discCount: String = "",
  @Json(name = "grouping")
  val grouping: String = "",
  @Json(name = "publisher")
  val publisher: String = "",
  @Json(name = "ratingAlbum")
  val ratingAlbum: String = "",
  @Json(name = "composer")
  val composer: String = "",
  @Json(name = "conductor")
  val conductor: String = "",
  @Json(name = "comment")
  val comment: String = "",
  @Json(name = "encoder")
  val encoder: String = "",

  // Additional MusicBee tags
  @Json(name = "originalArtist")
  val originalArtist: String = "",
  @Json(name = "originalAlbum")
  val originalAlbum: String = "",
  @Json(name = "originalYear")
  val originalYear: String = "",
  @Json(name = "bpm")
  val bpm: String = "",
  @Json(name = "tempo")
  val tempo: String = "",
  @Json(name = "mood")
  val mood: String = "",
  @Json(name = "occasion")
  val occasion: String = "",
  @Json(name = "keywords")
  val keywords: String = "",
  @Json(name = "language")
  val language: String = "",
  @Json(name = "origin")
  val origin: String = "",
  @Json(name = "region")
  val region: String = "",
  @Json(name = "continent")
  val continent: String = "",
  @Json(name = "custom3")
  val custom3: String = "",
  @Json(name = "custom4")
  val custom4: String = "",
  @Json(name = "custom5")
  val custom5: String = "",
  @Json(name = "custom6")
  val custom6: String = "",
  @Json(name = "custom7")
  val custom7: String = "",
  @Json(name = "custom8")
  val custom8: String = "",
  @Json(name = "volumeLeveling")
  val volumeLeveling: String = "",

  // File properties
  @Json(name = "kind")
  val kind: String = "",
  @Json(name = "format")
  val format: String = "",
  @Json(name = "size")
  val size: String = "",
  @Json(name = "channels")
  val channels: String = "",
  @Json(name = "sampleRate")
  val sampleRate: String = "",
  @Json(name = "bitrate")
  val bitrate: String = "",
  @Json(name = "dateModified")
  val dateModified: String = "",
  @Json(name = "dateAdded")
  val dateAdded: String = "",
  @Json(name = "lastPlayed")
  val lastPlayed: String = "",
  @Json(name = "playCount")
  val playCount: String = "",
  @Json(name = "skipCount")
  val skipCount: String = "",
  @Json(name = "duration")
  val duration: String = ""
) {
  /**
   * Converts this payload to a [TrackDetails] domain model.
   */
  fun toTrackDetails(): TrackDetails = TrackDetails(
    albumArtist = albumArtist,
    genre = genre,
    trackNo = trackNo,
    trackCount = trackCount,
    discNo = discNo,
    discCount = discCount,
    grouping = grouping,
    publisher = publisher,
    ratingAlbum = ratingAlbum,
    composer = composer,
    conductor = conductor,
    comment = comment,
    encoder = encoder,
    originalArtist = originalArtist,
    originalAlbum = originalAlbum,
    originalYear = originalYear,
    bpm = bpm,
    tempo = tempo,
    mood = mood,
    occasion = occasion,
    keywords = keywords,
    language = language,
    origin = origin,
    region = region,
    continent = continent,
    custom3 = custom3,
    custom4 = custom4,
    custom5 = custom5,
    custom6 = custom6,
    custom7 = custom7,
    custom8 = custom8,
    volumeLeveling = volumeLeveling,
    kind = kind,
    format = format,
    size = size,
    channels = channels,
    sampleRate = sampleRate,
    bitrate = bitrate,
    dateModified = dateModified,
    dateAdded = dateAdded,
    lastPlayed = lastPlayed,
    playCount = playCount,
    skipCount = skipCount,
    duration = duration
  )
}
