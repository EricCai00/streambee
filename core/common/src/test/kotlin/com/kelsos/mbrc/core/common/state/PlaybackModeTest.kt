package com.kelsos.mbrc.core.common.state

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackModeTest {

  @Test
  fun `cycle follows the four user-facing modes`() {
    assertThat(
      listOf(
        PlaybackMode.Sequential,
        PlaybackMode.Sequential.next(),
        PlaybackMode.Sequential.next().next(),
        PlaybackMode.Sequential.next().next().next(),
        PlaybackMode.Sequential.next().next().next().next()
      )
    ).containsExactly(
      PlaybackMode.Sequential,
      PlaybackMode.RepeatAll,
      PlaybackMode.RepeatOne,
      PlaybackMode.Shuffle,
      PlaybackMode.Sequential
    ).inOrder()
  }

  @Test
  fun `raw shuffle and repeat state maps to one mode`() {
    assertThat(PlaybackMode.from(ShuffleMode.Off, Repeat.None))
      .isEqualTo(PlaybackMode.Sequential)
    assertThat(PlaybackMode.from(ShuffleMode.Off, Repeat.All))
      .isEqualTo(PlaybackMode.RepeatAll)
    assertThat(PlaybackMode.from(ShuffleMode.Off, Repeat.One))
      .isEqualTo(PlaybackMode.RepeatOne)
    assertThat(PlaybackMode.from(ShuffleMode.Shuffle, Repeat.None))
      .isEqualTo(PlaybackMode.Shuffle)
    assertThat(PlaybackMode.from(ShuffleMode.AutoDJ, Repeat.All))
      .isEqualTo(PlaybackMode.Shuffle)
  }
}
