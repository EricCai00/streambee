package com.kelsos.mbrc.feature.library.playback

import com.kelsos.mbrc.core.data.library.track.Track
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/** Compact, streaming queue format used instead of thousands of SharedPreferences entries. */
internal object LocalPlaybackQueueCodec {
  fun write(output: OutputStream, tracks: List<Track>) {
    val data = DataOutputStream(BufferedOutputStream(output))
    data.writeInt(FILE_MAGIC)
    data.writeInt(FILE_VERSION)
    data.writeInt(tracks.size)
    tracks.forEach { track ->
      data.writeString(track.artist)
      data.writeString(track.title)
      data.writeString(track.src)
      data.writeInt(track.trackno)
      data.writeInt(track.disc)
      data.writeString(track.albumArtist)
      data.writeString(track.album)
      data.writeString(track.genre)
      data.writeString(track.year)
      data.writeLong(track.id)
    }
    data.flush()
  }

  fun read(input: InputStream): List<Track> {
    val data = DataInputStream(BufferedInputStream(input))
    require(data.readInt() == FILE_MAGIC) { "Invalid local playback queue file" }
    require(data.readInt() == FILE_VERSION) { "Unsupported local playback queue version" }
    val count = data.readInt()
    require(count in 0..MAX_TRACK_COUNT) { "Invalid local playback queue size" }
    return List(count) {
      Track(
        artist = data.readString(),
        title = data.readString(),
        src = data.readString(),
        trackno = data.readInt(),
        disc = data.readInt(),
        albumArtist = data.readString(),
        album = data.readString(),
        genre = data.readString(),
        year = data.readString(),
        id = data.readLong()
      )
    }
  }

  private fun DataOutputStream.writeString(value: String) {
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    require(bytes.size <= MAX_STRING_BYTES) { "Track field is too large" }
    writeInt(bytes.size)
    write(bytes)
  }

  private fun DataInputStream.readString(): String {
    val length = readInt()
    require(length in 0..MAX_STRING_BYTES) { "Invalid track field size" }
    val bytes = ByteArray(length)
    readFully(bytes)
    return String(bytes, StandardCharsets.UTF_8)
  }

  private const val FILE_MAGIC = 0x4D425251
  private const val FILE_VERSION = 1
  private const val MAX_TRACK_COUNT = 1_000_000
  private const val MAX_STRING_BYTES = 1_048_576
}
