package com.kelsos.mbrc.feature.library.playback

import com.google.common.truth.Truth.assertThat
import com.kelsos.mbrc.core.data.library.track.Track
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Test

class LocalPlaybackQueueCodecTest {
  @Test
  fun queueRoundTripsUnicodeAndControlCharacters() {
    val tracks = listOf(
      Track(
        artist = "周杰伦\u001fArtist",
        title = "晴天",
        src = "D:\\音乐\\晴天.flac",
        trackno = 3,
        disc = 1,
        albumArtist = "周杰伦",
        album = "叶惠美",
        genre = "流行",
        year = "2003",
        id = 42L
      )
    )
    val output = ByteArrayOutputStream()

    LocalPlaybackQueueCodec.write(output, tracks)
    val restored = LocalPlaybackQueueCodec.read(ByteArrayInputStream(output.toByteArray()))

    assertThat(restored).containsExactlyElementsIn(tracks)
  }

  @Test
  fun tenThousandTrackQueueRoundTrips() {
    val tracks = List(10_000) { index ->
      Track(
        artist = "Artist $index",
        title = "Title $index",
        src = "D:\\Music\\track-$index.flac",
        trackno = index,
        disc = 1,
        albumArtist = "Album Artist",
        album = "Album ${index / 10}",
        genre = "Genre",
        year = "2026",
        id = index.toLong()
      )
    }
    val output = ByteArrayOutputStream()

    LocalPlaybackQueueCodec.write(output, tracks)
    val restored = LocalPlaybackQueueCodec.read(ByteArrayInputStream(output.toByteArray()))

    assertThat(restored).containsExactlyElementsIn(tracks).inOrder()
  }
}
