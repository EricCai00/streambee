package com.kelsos.mbrc.core.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.kelsos.mbrc.core.data.playlist.PlaylistRepository
import com.kelsos.mbrc.core.networking.protocol.usecases.UserActionUseCase
import com.kelsos.mbrc.core.networking.protocol.usecases.addTrackToPlaylist
import com.kelsos.mbrc.core.ui.R
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Shows the locally cached MusicBee playlists and sends the selected track to
 * the desktop plugin. The playlist list is deliberately read from Room, so
 * opening this picker never waits for a fresh network query.
 */
@Composable
fun AddToPlaylistDialog(
  trackPath: String,
  onDismiss: () -> Unit,
  playlistRepository: PlaylistRepository = koinInject(),
  userActionUseCase: UserActionUseCase = koinInject()
) {
  val playlistFlow = remember(playlistRepository) { playlistRepository.getAll() }
  val playlists = playlistFlow.collectAsLazyPagingItems()
  val scope = rememberCoroutineScope()
  var selectedPlaylistUrl by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = {
      if (selectedPlaylistUrl == null) onDismiss()
    },
    title = { Text(stringResource(R.string.add_to_playlist_title)) },
    text = {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 48.dp, max = 360.dp),
        contentAlignment = Alignment.Center
      ) {
        when {
          playlists.itemCount == 0 && playlists.loadState.refresh is LoadState.Loading -> {
            CircularProgressIndicator()
          }

          playlists.itemCount == 0 -> {
            Text(stringResource(R.string.add_to_playlist_empty))
          }

          else -> {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
              items(
                count = playlists.itemCount,
                key = { index -> playlists.peek(index)?.id ?: index }
              ) { index ->
                playlists[index]?.let { playlist ->
                  TextButton(
                    onClick = {
                      if (selectedPlaylistUrl == null) {
                        selectedPlaylistUrl = playlist.url
                        scope.launch {
                          userActionUseCase.addTrackToPlaylist(
                            playlistUrl = playlist.url,
                            trackPath = trackPath
                          )
                          onDismiss()
                        }
                      }
                    },
                    enabled = selectedPlaylistUrl == null,
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Text(text = playlist.name, modifier = Modifier.fillMaxWidth())
                  }
                }
              }

              if (playlists.loadState.append is LoadState.Loading) {
                item {
                  Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                  ) {
                    CircularProgressIndicator()
                  }
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = onDismiss,
        enabled = selectedPlaylistUrl == null
      ) {
        Text(stringResource(R.string.add_to_playlist_cancel))
      }
    }
  )
}
