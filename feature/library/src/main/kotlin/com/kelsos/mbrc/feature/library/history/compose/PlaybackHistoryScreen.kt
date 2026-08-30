package com.kelsos.mbrc.feature.library.history.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import com.kelsos.mbrc.core.data.history.PlaybackHistoryEntry
import com.kelsos.mbrc.core.ui.compose.DoubleLineRow
import com.kelsos.mbrc.core.ui.compose.PagingListScreen
import com.kelsos.mbrc.core.ui.compose.ScreenScaffold
import com.kelsos.mbrc.feature.library.R
import com.kelsos.mbrc.feature.library.history.PlaybackHistoryUiMessage
import com.kelsos.mbrc.feature.library.history.PlaybackHistoryViewModel
import com.kelsos.mbrc.feature.minicontrol.MiniControl
import java.text.DateFormat
import java.util.Date
import org.koin.androidx.compose.koinViewModel

@Composable
fun PlaybackHistoryScreen(
  onOpenDrawer: () -> Unit,
  onNavigateToPlayer: () -> Unit,
  snackbarHostState: SnackbarHostState,
  modifier: Modifier = Modifier,
  viewModel: PlaybackHistoryViewModel = koinViewModel()
) {
  val history = viewModel.history.collectAsLazyPagingItems()
  val trackUnavailable = stringResource(R.string.history_track_unavailable)
  val playFailed = stringResource(R.string.history_play_failed)

  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      snackbarHostState.showSnackbar(
        message = when (event) {
          PlaybackHistoryUiMessage.TrackUnavailable -> trackUnavailable
          PlaybackHistoryUiMessage.PlayFailed -> playFailed
        },
        duration = SnackbarDuration.Short
      )
    }
  }

  ScreenScaffold(
    title = stringResource(R.string.history_title),
    snackbarHostState = snackbarHostState,
    onOpenDrawer = onOpenDrawer,
    modifier = modifier
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      PagingListScreen(
        items = history,
        modifier = Modifier.weight(1f),
        emptyMessage = stringResource(R.string.history_empty),
        emptyIcon = Icons.Default.History,
        key = PlaybackHistoryEntry::id
      ) { entry ->
        PlaybackHistoryRow(entry = entry, onClick = { viewModel.play(entry) })
      }

      MiniControl(
        onNavigateToPlayer = onNavigateToPlayer,
        snackbarHostState = snackbarHostState
      )
    }
  }
}

@Composable
private fun PlaybackHistoryRow(entry: PlaybackHistoryEntry, onClick: () -> Unit) {
  val artist = entry.artist.ifBlank { stringResource(R.string.unknown_artist) }
  val subtitle = entry.album.takeIf(String::isNotBlank)?.let { "$artist · $it" } ?: artist
  val playedAt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    .format(Date(entry.playedAt))

  DoubleLineRow(
    title = entry.title.ifBlank { stringResource(R.string.unknown_title) },
    subtitle = subtitle,
    onClick = onClick,
    leadingContent = {
      Icon(
        imageVector = Icons.Default.MusicNote,
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.primary
      )
    },
    trailingContent = {
      Text(
        text = playedAt,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
    }
  )
}
