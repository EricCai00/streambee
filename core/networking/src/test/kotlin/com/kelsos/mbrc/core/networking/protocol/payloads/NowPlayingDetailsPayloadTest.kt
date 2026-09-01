package com.kelsos.mbrc.core.networking.protocol.payloads

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import org.junit.Test

class NowPlayingDetailsPayloadTest {

  private val adapter = Moshi.Builder().build().adapter(NowPlayingDetailsPayload::class.java)

  @Test
  fun `maps extended MusicBee tags to track details`() {
    val payload = adapter.fromJson(
      """
      {
        "conductor":"Conductor",
        "originalArtist":"Original Artist",
        "originalAlbum":"Original Album",
        "originalYear":"1999",
        "bpm":"120",
        "tempo":"Upbeat",
        "mood":"Energetic",
        "occasion":"Workout",
        "keywords":"rock, live",
        "language":"English",
        "origin":"Collection",
        "region":"North America",
        "continent":"North America",
        "custom3":"Custom 3",
        "custom8":"Custom 8",
        "volumeLeveling":"Not calculated"
      }
      """.trimIndent()
    )!!

    val details = payload.toTrackDetails()

    assertThat(details.conductor).isEqualTo("Conductor")
    assertThat(details.originalArtist).isEqualTo("Original Artist")
    assertThat(details.originalAlbum).isEqualTo("Original Album")
    assertThat(details.originalYear).isEqualTo("1999")
    assertThat(details.bpm).isEqualTo("120")
    assertThat(details.tempo).isEqualTo("Upbeat")
    assertThat(details.mood).isEqualTo("Energetic")
    assertThat(details.occasion).isEqualTo("Workout")
    assertThat(details.keywords).isEqualTo("rock, live")
    assertThat(details.language).isEqualTo("English")
    assertThat(details.origin).isEqualTo("Collection")
    assertThat(details.region).isEqualTo("North America")
    assertThat(details.continent).isEqualTo("North America")
    assertThat(details.custom3).isEqualTo("Custom 3")
    assertThat(details.custom8).isEqualTo("Custom 8")
    assertThat(details.volumeLeveling).isEqualTo("Not calculated")
  }

  @Test
  fun `missing extended tags remain empty for older plugins`() {
    val details = adapter.fromJson("{}")!!.toTrackDetails()

    assertThat(details.conductor).isEmpty()
    assertThat(details.originalArtist).isEmpty()
    assertThat(details.originalAlbum).isEmpty()
    assertThat(details.originalYear).isEmpty()
    assertThat(details.bpm).isEmpty()
    assertThat(details.tempo).isEmpty()
    assertThat(details.mood).isEmpty()
    assertThat(details.occasion).isEmpty()
    assertThat(details.keywords).isEmpty()
    assertThat(details.language).isEmpty()
    assertThat(details.origin).isEmpty()
    assertThat(details.region).isEmpty()
    assertThat(details.continent).isEmpty()
    assertThat(details.custom3).isEmpty()
    assertThat(details.custom8).isEmpty()
    assertThat(details.volumeLeveling).isEmpty()
  }
}
