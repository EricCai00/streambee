package com.kelsos.mbrc.core.queue

import com.kelsos.mbrc.core.common.utilities.Outcome
import com.kelsos.mbrc.core.data.library.track.Track

/**
 * Interface for queuing paths (URLs, file paths) for playback.
 * This abstraction allows feature modules to queue content without
 * depending on the full QueueHandler implementation.
 */
interface PathQueueUseCase {
  /**
   * Queue a path for immediate playback.
   * @param path The path to queue (URL or file path)
   * @return Outcome with track count on success, or AppError on failure
   */
  suspend fun queuePath(path: String): Outcome<Int>

  /** Queue a set of library paths as one local playlist and start the first track. */
  suspend fun queuePaths(paths: List<String>): Outcome<Int>

  /** Queue a set of library paths and start at [startIndex], preserving the order. */
  suspend fun queuePaths(paths: List<String>, startIndex: Int): Outcome<Int> = queuePaths(paths)

  /** Queue fully described tracks, including files that are not in the local library cache. */
  suspend fun queueTracks(tracks: List<Track>): Outcome<Int> = queueTracks(tracks, 0)

  /** Queue fully described tracks and start at [startIndex], preserving playlist order. */
  suspend fun queueTracks(tracks: List<Track>, startIndex: Int): Outcome<Int> =
    queuePaths(tracks.map { it.src }, startIndex)
}
