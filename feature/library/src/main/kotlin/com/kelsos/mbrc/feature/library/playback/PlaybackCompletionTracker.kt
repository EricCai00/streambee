package com.kelsos.mbrc.feature.library.playback

import android.os.SystemClock
import com.kelsos.mbrc.core.data.library.track.Track
import kotlin.math.min

internal data class QualifiedAppPlayback(
  val track: Track,
  val durationMs: Long,
  val listenedMs: Long,
  val startedAt: Long,
  val playedAt: Long
)

/** MusicBee's default play-count rule: half the track or four minutes, whichever is first. */
internal object PlaybackCompletionPolicy {
  private const val MAX_REQUIRED_PLAYBACK_MS = 240_000L
  const val LAST_FM_MIN_TRACK_DURATION_MS = 30_000L

  fun requiredPlaybackMs(durationMs: Long): Long = if (durationMs > 0L) {
    min(durationMs / 2L, MAX_REQUIRED_PLAYBACK_MS).coerceAtLeast(1L)
  } else {
    Long.MAX_VALUE
  }

  fun qualifies(listenedMs: Long, durationMs: Long): Boolean =
    listenedMs >= requiredPlaybackMs(durationMs)

  fun canScrobble(durationMs: Long): Boolean = durationMs > LAST_FM_MIN_TRACK_DURATION_MS
}

/**
 * Counts wall-clock time only while Media3 says audio is actually playing.
 * Seeking therefore never advances completion, while pause and buffering time are ignored.
 */
internal class PlaybackCompletionTracker(
  private val elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime,
  private val wallClockMs: () -> Long = System::currentTimeMillis,
  private val onQualified: (QualifiedAppPlayback) -> Unit
) {
  private var session: Session? = null

  fun transition(track: Track?, durationMs: Long, isPlaying: Boolean) {
    val now = elapsedRealtimeMs()
    sampleExisting(now)
    session = track?.let {
      Session(
        track = it,
        durationMs = durationMs.validDuration(),
        startedAt = wallClockMs(),
        lastSampleAt = now,
        wasPlaying = isPlaying
      )
    }
  }

  fun update(track: Track?, durationMs: Long, isPlaying: Boolean) {
    val current = session
    if (track == null) {
      if (current != null) transition(null, durationMs, isPlaying)
      return
    }
    if (current == null || current.track.src != track.src) {
      transition(track, durationMs, isPlaying)
      return
    }

    val now = elapsedRealtimeMs()
    sampleExisting(now)
    current.durationMs = durationMs.validDuration().takeIf { it > 0L } ?: current.durationMs
    current.wasPlaying = isPlaying
    maybeQualify(current)
  }

  private fun sampleExisting(now: Long) {
    val current = session ?: return
    if (current.wasPlaying) {
      current.listenedMs += (now - current.lastSampleAt).coerceAtLeast(0L)
    }
    current.lastSampleAt = now
    maybeQualify(current)
  }

  private fun maybeQualify(current: Session) {
    if (current.qualified || !PlaybackCompletionPolicy.qualifies(
        listenedMs = current.listenedMs,
        durationMs = current.durationMs
      )
    ) {
      return
    }
    current.qualified = true
    onQualified(
      QualifiedAppPlayback(
        track = current.track,
        durationMs = current.durationMs,
        listenedMs = current.listenedMs,
        startedAt = current.startedAt,
        playedAt = wallClockMs()
      )
    )
  }

  private fun Long.validDuration(): Long = takeIf { it > 0L } ?: 0L

  private class Session(
    val track: Track,
    var durationMs: Long,
    val startedAt: Long,
    var lastSampleAt: Long,
    var wasPlaying: Boolean,
    var listenedMs: Long = 0L,
    var qualified: Boolean = false
  )
}
