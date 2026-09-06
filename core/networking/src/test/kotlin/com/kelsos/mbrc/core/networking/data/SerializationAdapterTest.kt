package com.kelsos.mbrc.core.networking.data

import com.google.common.truth.Truth.assertThat
import com.kelsos.mbrc.core.networking.client.SocketMessage
import com.kelsos.mbrc.core.networking.protocol.base.Protocol
import com.kelsos.mbrc.core.networking.protocol.payloads.AddTrackToPlaylistRequest
import com.squareup.moshi.Moshi
import org.junit.Test

class SerializationAdapterTest {

  @Test
  fun `playlist add request uses wire field names`() {
    val json = SerializationAdapterImpl(Moshi.Builder().build()).stringify(
      SocketMessage.create(
        Protocol.PlaylistAddTrack,
        AddTrackToPlaylistRequest(
          playlistUrl = "playlist://favorites",
          trackPath = "D:\\Music\\track.mp3"
        )
      )
    )

    assertThat(json).contains("\"playlist_url\"")
    assertThat(json).contains("\"track_path\"")
    assertThat(json).doesNotContain("playlistUrl")
    assertThat(json).doesNotContain("trackPath")
  }
}
