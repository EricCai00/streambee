package com.kelsos.mbrc.core.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Shows a track's 1-based position, or the animated playing indicator for the
 * currently playing track. The fixed leading slot keeps multi-digit positions
 * aligned without taking space from the track title.
 */
@Composable
fun TrackPositionIndicator(position: Int, isPlaying: Boolean, modifier: Modifier = Modifier) {
  Box(
    // Keep the row height unchanged while giving the title column more room.
    modifier = modifier
      .width(35.dp)
      .height(48.dp),
    contentAlignment = Alignment.Center
  ) {
    if (isPlaying) {
      AudioBarsIndicator(
        color = MaterialTheme.colorScheme.primary,
        barMaxHeight = 18.dp,
        modifier = Modifier.size(24.dp)
      )
    } else {
      Text(
        text = position.toString(),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        style = when {
          // Keep the larger type for normal track counts, while stepping down
          // only when needed so four- and five-digit playlist positions fit.
          position >= 10_000 -> MaterialTheme.typography.bodySmall

          position >= 1_000 -> MaterialTheme.typography.bodyMedium

          else -> MaterialTheme.typography.bodyLarge
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false
      )
    }
  }
}
