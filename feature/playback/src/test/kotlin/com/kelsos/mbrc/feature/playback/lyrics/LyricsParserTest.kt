package com.kelsos.mbrc.feature.playback.lyrics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LyricsParserTest {
  @Test
  fun `parses timestamped lyrics and applies offset`() {
    val parsed = parseLyrics(
      listOf(
        "[ar:Artist]",
        "[offset:250]",
        "[00:01.50]First line",
        "[00:03.005]Second line"
      )
    )

    assertThat(parsed.plainLines).containsExactly("First line", "Second line").inOrder()
    assertThat(parsed.timedLines).containsExactly(
      TimedLyricLine("First line", 1_750L),
      TimedLyricLine("Second line", 3_255L)
    ).inOrder()
  }

  @Test
  fun `expands multiple timestamps and sorts the timeline`() {
    val parsed = parseLyrics(
      listOf(
        "[00:10.00][00:20.00]Chorus",
        "[00:05.00]Verse"
      )
    )

    assertThat(parsed.timedLines).containsExactly(
      TimedLyricLine("Verse", 5_000L),
      TimedLyricLine("Chorus", 10_000L),
      TimedLyricLine("Chorus", 20_000L)
    ).inOrder()
  }

  @Test
  fun `plain lyrics remain available without a synchronized timeline`() {
    val parsed = parseLyrics(listOf("First line", "", "Second line"))

    assertThat(parsed.plainLines).containsExactly("First line", "", "Second line").inOrder()
    assertThat(parsed.timedLines).isEmpty()
  }

  @Test
  fun `active line follows playback position`() {
    val lines = listOf(
      TimedLyricLine("First", 1_000L),
      TimedLyricLine("Second", 2_500L),
      TimedLyricLine("Third", 4_000L)
    )

    assertThat(activeLyricLineIndex(lines, 999L)).isEqualTo(-1)
    assertThat(activeLyricLineIndex(lines, 1_000L)).isEqualTo(0)
    assertThat(activeLyricLineIndex(lines, 3_000L)).isEqualTo(1)
    assertThat(activeLyricLineIndex(lines, 10_000L)).isEqualTo(2)
  }
}
