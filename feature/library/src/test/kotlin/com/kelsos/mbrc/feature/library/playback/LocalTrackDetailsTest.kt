package com.kelsos.mbrc.feature.library.playback

import com.google.common.truth.Truth.assertThat
import com.kelsos.mbrc.core.data.library.track.Track
import org.junit.Test

class LocalTrackDetailsTest {
  @Test
  fun `local track metadata is mapped to playing track details`() {
    val track = Track(
      artist = "Track Artist",
      title = "Track Title",
      src = "/music/track.flac",
      trackno = 7,
      disc = 2,
      albumArtist = "Album Artist",
      album = "Album",
      genre = "Progressive Rock",
      year = "1973",
      id = 42L
    )

    val details = track.toTrackDetails()

    assertThat(details.albumArtist).isEqualTo("Album Artist")
    assertThat(details.genre).isEqualTo("Progressive Rock")
    assertThat(details.trackNo).isEqualTo("7")
    assertThat(details.discNo).isEqualTo("2")
  }

  @Test
  fun `missing local track and disc numbers remain blank`() {
    val track = Track(
      artist = "Artist",
      title = "Title",
      src = "/music/track.mp3",
      trackno = 0,
      disc = 0,
      albumArtist = "",
      album = "",
      genre = "",
      year = "",
      id = 1L
    )

    val details = track.toTrackDetails()

    assertThat(details.trackNo).isEmpty()
    assertThat(details.discNo).isEmpty()
  }
}
