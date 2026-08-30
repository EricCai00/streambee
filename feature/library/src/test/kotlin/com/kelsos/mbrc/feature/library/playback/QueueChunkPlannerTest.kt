package com.kelsos.mbrc.feature.library.playback

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QueueChunkPlannerTest {
  @Test
  fun chunksReconstructOriginalQueueAroundSelectedTrack() {
    val selected = 537
    val queue = mutableListOf(selected)

    planQueueChunks(itemCount = 1_003, selectedIndex = selected, chunkSize = 100)
      .forEach { chunk ->
        val values = (chunk.fromIndex until chunk.toIndexExclusive).toList()
        if (chunk.prepend) queue.addAll(0, values) else queue.addAll(values)
      }

    assertThat(queue).containsExactlyElementsIn(0 until 1_003).inOrder()
  }

  @Test
  fun firstChunkContainsTracksAfterSelection() {
    val first = planQueueChunks(itemCount = 1_000, selectedIndex = 500, chunkSize = 250).first()

    assertThat(first).isEqualTo(QueueChunkRange(501, 751, prepend = false))
  }

  @Test
  fun selectedOnlyQueueNeedsNoChunks() {
    assertThat(planQueueChunks(itemCount = 1, selectedIndex = 0, chunkSize = 250)).isEmpty()
  }

  @Test
  fun tenThousandTrackQueueUsesBoundedChunksAndPreservesOrder() {
    val itemCount = 10_000
    val selected = 8_731
    val chunks = planQueueChunks(itemCount, selected, chunkSize = 250)
    val queue = mutableListOf(selected)

    chunks.forEach { chunk ->
      assertThat(chunk.toIndexExclusive - chunk.fromIndex).isAtMost(250)
      val values = (chunk.fromIndex until chunk.toIndexExclusive).toList()
      if (chunk.prepend) queue.addAll(0, values) else queue.addAll(values)
    }

    assertThat(queue).containsExactlyElementsIn(0 until itemCount).inOrder()
  }
}
