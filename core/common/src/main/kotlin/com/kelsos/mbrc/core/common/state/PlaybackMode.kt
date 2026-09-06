package com.kelsos.mbrc.core.common.state

/** The four playback modes exposed by the phone player. */
enum class PlaybackMode {
  Sequential,
  RepeatAll,
  RepeatOne,
  Shuffle;

  fun next(): PlaybackMode = when (this) {
    Sequential -> RepeatAll
    RepeatAll -> RepeatOne
    RepeatOne -> Shuffle
    Shuffle -> Sequential
  }

  companion object {
    /** Collapses the raw shuffle/repeat combination into one user-facing mode. */
    fun from(shuffle: ShuffleMode, repeat: Repeat): PlaybackMode = when {
      shuffle != ShuffleMode.Off -> Shuffle
      repeat == Repeat.One -> RepeatOne
      repeat == Repeat.All -> RepeatAll
      else -> Sequential
    }
  }
}
