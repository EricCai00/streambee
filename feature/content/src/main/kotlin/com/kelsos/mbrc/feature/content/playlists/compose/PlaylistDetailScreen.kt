package com.kelsos.mbrc.feature.content.playlists.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kelsos.mbrc.core.common.state.AppStateFlow
import com.kelsos.mbrc.core.data.library.track.Track
import com.kelsos.mbrc.core.ui.R as CoreUiR
import com.kelsos.mbrc.core.ui.compose.DoubleLineRow
import com.kelsos.mbrc.core.ui.compose.EmptyScreen
import com.kelsos.mbrc.core.ui.compose.LoadingScreen
import com.kelsos.mbrc.core.ui.compose.NavigationIconType
import com.kelsos.mbrc.core.ui.compose.ScreenScaffold
import com.kelsos.mbrc.core.ui.compose.TrackPositionIndicator
import com.kelsos.mbrc.feature.content.R
import com.kelsos.mbrc.feature.content.playlists.PlaylistDetailViewModel
import com.kelsos.mbrc.feature.content.playlists.PlaylistUiMessages
import com.kelsos.mbrc.feature.minicontrol.MiniControl
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun PlaylistDetailScreen(
  playlistName: String,
  playlistUrl: String,
  onNavigateBack: () -> Unit,
  onNavigateToPlayer: () -> Unit,
  onNavigateToAlbum: (album: String, artist: String) -> Unit,
  onNavigateToArtist: (artist: String) -> Unit,
  snackbarHostState: SnackbarHostState,
  modifier: Modifier = Modifier,
  viewModel: PlaylistDetailViewModel = koinViewModel()
) {
  val tracks by viewModel.tracks.collectAsStateWithLifecycle()
  val appState: AppStateFlow = koinInject()
  val playingTrack by appState.playingTrack.collectAsStateWithLifecycle()
  val loaded by viewModel.loaded.collectAsStateWithLifecycle()
  val startingTrackIndex by viewModel.startingTrackIndex.collectAsStateWithLifecycle()
  val playFailedMessage = stringResource(R.string.playlist_play_failed)
  val networkUnavailableMessage =
    stringResource(CoreUiR.string.connection_error_network_unavailable)

  LaunchedEffect(playlistUrl) {
    viewModel.load(playlistUrl)
  }
  LaunchedEffect(Unit) {
    viewModel.events.collectLatest { event ->
      val message = when (event) {
        PlaylistUiMessages.NetworkUnavailable -> networkUnavailableMessage
        PlaylistUiMessages.PlayFailed -> playFailedMessage
        else -> return@collectLatest
      }
      snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
    }
  }

  BackHandler(onBack = onNavigateBack)
  ScreenScaffold(
    title = playlistName,
    snackbarHostState = snackbarHostState,
    navigationIcon = NavigationIconType.Back(onNavigateBack),
    modifier = modifier
  ) { paddingValues ->
    Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
      when {
        !loaded -> LoadingScreen(modifier = Modifier.weight(1f))

        tracks.isEmpty() -> EmptyScreen(
          message = stringResource(R.string.playlist_tracks_empty),
          icon = Icons.Default.MusicNote,
          modifier = Modifier.weight(1f)
        )

        else -> LazyColumn(modifier = Modifier.weight(1f)) {
          itemsIndexed(tracks, key = { _, track -> track.src }) { index, track ->
            PlaylistTrackRow(
              track = track,
              trackNumber = index + 1,
              isPlaying = track.src == playingTrack.path,
              isStarting = startingTrackIndex == index,
              onClick = { viewModel.play(index) },
              onNavigateToAlbum = onNavigateToAlbum,
              onNavigateToArtist = onNavigateToArtist
            )
          }
        }
      }
      MiniControl(
        onNavigateToPlayer = onNavigateToPlayer,
        snackbarHostState = snackbarHostState
      )
    }
  }
}

@Composable
private fun PlaylistTrackRow(
  track: Track,
  trackNumber: Int,
  isPlaying: Boolean,
  isStarting: Boolean,
  onClick: () -> Unit,
  onNavigateToAlbum: (album: String, artist: String) -> Unit,
  onNavigateToArtist: (artist: String) -> Unit
) {
  var menuExpanded by remember { mutableStateOf(false) }
  val title = track.title.ifEmpty { stringResource(R.string.unknown_title) }
  val artist = track.artist.ifEmpty { stringResource(R.string.unknown_artist) }
  val album = track.album.ifEmpty { stringResource(R.string.unknown_album) }

  DoubleLineRow(
    title = title,
    subtitle = "$artist - $album",
    onClick = onClick,
    leadingContent = {
      if (isStarting) {
        Box(
          modifier = Modifier.size(48.dp),
          contentAlignment = Alignment.Center
        ) {
          CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp
          )
        }
      } else {
        TrackPositionIndicator(
          position = trackNumber,
          isPlaying = isPlaying
        )
      }
    },
    trailingContent = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (track.loved) {
          Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = stringResource(R.string.track_loved_description),
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
          )
        }
        Box {
          IconButton(onClick = { menuExpanded = true }) {
            Icon(
              imageVector = Icons.Default.MoreVert,
              contentDescription = stringResource(CoreUiR.string.menu_overflow_description)
            )
          }
          DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
          ) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.playlist_go_to_album)) },
              onClick = {
                menuExpanded = false
                if (track.album.isNotBlank()) {
                  onNavigateToAlbum(track.album, track.albumArtist.ifBlank { track.artist })
                }
              }
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.playlist_go_to_artist)) },
              onClick = {
                menuExpanded = false
                onNavigateToArtist(track.albumArtist.ifBlank { track.artist })
              }
            )
          }
        }
      }
    }
  )
}
