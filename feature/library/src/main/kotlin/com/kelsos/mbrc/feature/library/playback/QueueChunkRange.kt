package com.kelsos.mbrc.feature.library.playback

internal data class QueueChunkRange(
  val fromIndex: Int,
  val toIndexExclusive: Int,
  val prepend: Boolean
)

/**
 * Plans queue loading around the selected item. The next tracks are appended first so playback
 * can advance immediately; preceding tracks are then prepended in alternating chunks.
 */
internal fun planQueueChunks(
  itemCount: Int,
  selectedIndex: Int,
  chunkSize: Int
): List<QueueChunkRange> {
  require(itemCount > 0)
  require(selectedIndex in 0 until itemCount)
  require(chunkSize > 0)

  val chunks = mutableListOf<QueueChunkRange>()
  var before = selectedIndex
  var after = selectedIndex + 1
  var appendNext = true

  while (before > 0 || after < itemCount) {
    if (appendNext && after < itemCount) {
      val end = (after + chunkSize).coerceAtMost(itemCount)
      chunks += QueueChunkRange(after, end, prepend = false)
      after = end
    } else if (!appendNext && before > 0) {
      val start = (before - chunkSize).coerceAtLeast(0)
      chunks += QueueChunkRange(start, before, prepend = true)
      before = start
    } else if (after < itemCount) {
      val end = (after + chunkSize).coerceAtMost(itemCount)
      chunks += QueueChunkRange(after, end, prepend = false)
      after = end
    } else {
      val start = (before - chunkSize).coerceAtLeast(0)
      chunks += QueueChunkRange(start, before, prepend = true)
      before = start
    }
    appendNext = !appendNext
  }

  return chunks
}
