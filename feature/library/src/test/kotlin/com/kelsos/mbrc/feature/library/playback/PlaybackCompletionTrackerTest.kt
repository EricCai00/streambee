package com.kelsos.mbrc.feature.library.playback

import com.google.common.truth.Truth.assertThat
import com.kelsos.mbrc.core.data.library.track.Track
import org.junit.Test

class PlaybackCompletionTrackerTest {
  @Test
  fun `qualifies after half of an ordinary track`() {
    val clock = TestClock()
    val completed = mutableListOf<QualifiedAppPlayback>()
    val tracker = tracker(clock, completed)

    tracker.transition(track(), durationMs = 200_000L, isPlaying = true)
    clock.elapsed = 99_999L
    tracker.update(track(), durationMs = 200_000L, isPlaying = true)
    assertThat(completed).isEmpty()

    clock.elapsed = 100_000L
    tracker.update(track(), durationMs = 200_000L, isPlaying = true)

    assertThat(completed).hasSize(1)
    assertThat(completed.single().listenedMs).isEqualTo(100_000L)
  }

  @Test
  fun `long tracks qualify at four minutes`() {
    assertThat(PlaybackCompletionPolicy.requiredPlaybackMs(60L * 60L * 1_000L))
      .isEqualTo(240_000L)
  }

  @Test
  fun `paused time does not count`() {
    val clock = TestClock()
    val completed = mutableListOf<QualifiedAppPlayback>()
    val tracker = tracker(clock, completed)

    tracker.transition(track(), durationMs = 200_000L, isPlaying = true)
    clock.elapsed = 50_000L
    tracker.update(track(), durationMs = 200_000L, isPlaying = false)
    clock.elapsed = 500_000L
    tracker.update(track(), durationMs = 200_000L, isPlaying = false)
    assertThat(completed).isEmpty()

    tracker.update(track(), durationMs = 200_000L, isPlaying = true)
    clock.elapsed = 550_000L
    tracker.update(track(), durationMs = 200_000L, isPlaying = true)

    assertThat(completed).hasSize(1)
    assertThat(completed.single().listenedMs).isEqualTo(100_000L)
  }

  @Test
  fun `a repeated copy of the same path starts a new listen`() {
    val clock = TestClock()
    val completed = mutableListOf<QualifiedAppPlayback>()
    val tracker = tracker(clock, completed)
    val track = track()

    tracker.transition(track, durationMs = 100_000L, isPlaying = true)
    clock.elapsed = 50_000L
    tracker.update(track, durationMs = 100_000L, isPlaying = true)
    tracker.transition(track, durationMs = 100_000L, isPlaying = true)
    clock.elapsed = 100_000L
    tracker.update(track, durationMs = 100_000L, isPlaying = true)

    assertThat(completed).hasSize(2)
  }

  @Test
  fun `unknown duration waits until Media3 resolves it`() {
    val clock = TestClock()
    val completed = mutableListOf<QualifiedAppPlayback>()
    val tracker = tracker(clock, completed)

    tracker.transition(track(), durationMs = 0L, isPlaying = true)
    clock.elapsed = 120_000L
    tracker.update(track(), durationMs = 0L, isPlaying = true)
    assertThat(completed).isEmpty()

    tracker.update(track(), durationMs = 200_000L, isPlaying = true)

    assertThat(completed).hasSize(1)
  }

  @Test
  fun `Last fm excludes tracks of thirty seconds or less`() {
    assertThat(PlaybackCompletionPolicy.canScrobble(30_000L)).isFalse()
    assertThat(PlaybackCompletionPolicy.canScrobble(30_001L)).isTrue()
  }

  private fun tracker(clock: TestClock, completed: MutableList<QualifiedAppPlayback>) =
    PlaybackCompletionTracker(
      elapsedRealtimeMs = { clock.elapsed },
      wallClockMs = { clock.wall },
      onQualified = completed::add
    )

  private fun track() = Track(
    artist = "Artist",
    title = "Title",
    src = "D:/Music/song.flac",
    trackno = 1,
    disc = 1,
    albumArtist = "Artist",
    album = "Album",
    genre = "Rock",
    year = "2026",
    id = 1L
  )

  private class TestClock(var elapsed: Long = 0L, var wall: Long = 1_000L)
}
