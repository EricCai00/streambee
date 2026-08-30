package com.kelsos.mbrc.feature.playback.player.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kelsos.mbrc.core.common.playback.LocalQueueTrack
import com.kelsos.mbrc.feature.playback.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerQueueBottomSheet(
  queue: List<LocalQueueTrack>,
  playingTrackPath: String,
  onTrackClick: (Int) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val sheetHeight = LocalConfiguration.current.screenHeightDp.dp / 2
  val listState = rememberLazyListState()
  val playingIndex = queue.indexOfFirst { it.path == playingTrackPath }

  LaunchedEffect(playingIndex, queue.size) {
    if (playingIndex >= 0) listState.scrollToItem(playingIndex)
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    dragHandle = null,
    modifier = modifier
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .height(sheetHeight)
    ) {
      QueueSheetHeader(trackCount = queue.size)
      HorizontalDivider()

      if (queue.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.QueueMusic,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(40.dp)
            )
            Text(
              text = stringResource(R.string.now_playing__empty_list),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              style = MaterialTheme.typography.bodyLarge
            )
          }
        }
      } else {
        LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize()
        ) {
          itemsIndexed(
            items = queue,
            key = { index, track -> "${track.path}#$index" }
          ) { index, track ->
            QuickQueueTrackRow(
              track = track,
              isPlaying = index == playingIndex,
              onClick = { onTrackClick(index) }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun QueueSheetHeader(trackCount: Int) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 16.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = stringResource(R.string.nav_queue),
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold
    )
    Text(
      text = pluralStringResource(
        R.plurals.now_playing__track_count,
        trackCount,
        trackCount
      ),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun QuickQueueTrackRow(track: LocalQueueTrack, isPlaying: Boolean, onClick: () -> Unit) {
  val background = if (isPlaying) {
    MaterialTheme.colorScheme.surfaceContainerHighest
  } else {
    MaterialTheme.colorScheme.surface
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(background)
      .clickable(onClick = onClick)
      .height(64.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .width(4.dp)
        .fillMaxHeight()
        .background(if (isPlaying) MaterialTheme.colorScheme.primary else Color.Transparent)
    )

    Icon(
      imageVector = Icons.AutoMirrored.Filled.QueueMusic,
      contentDescription = null,
      tint = if (isPlaying) {
        MaterialTheme.colorScheme.primary
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant
      },
      modifier = Modifier
        .padding(start = 16.dp)
        .size(24.dp)
    )

    Spacer(modifier = Modifier.width(12.dp))

    Column(
      modifier = Modifier
        .weight(1f)
        .padding(end = 20.dp)
    ) {
      Text(
        text = track.title,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = track.album.takeIf(String::isNotBlank)?.let { "${track.artist} · $it" }
          ?: track.artist,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}
