package com.kelsos.mbrc.feature.library.compose.drilldown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kelsos.mbrc.core.common.settings.ArtistSortField
import com.kelsos.mbrc.core.common.settings.ArtistSortPreference
import com.kelsos.mbrc.core.common.settings.SortOrder
import com.kelsos.mbrc.core.common.utilities.AppError
import com.kelsos.mbrc.core.common.utilities.Outcome
import com.kelsos.mbrc.core.data.library.artist.Artist
import com.kelsos.mbrc.core.queue.Queue
import com.kelsos.mbrc.core.ui.compose.ActionItem
import com.kelsos.mbrc.core.ui.compose.NavigationIconType
import com.kelsos.mbrc.core.ui.compose.PagingScrollbarStyle
import com.kelsos.mbrc.core.ui.compose.QueueResultEffect
import com.kelsos.mbrc.core.ui.compose.ScreenScaffold
import com.kelsos.mbrc.core.ui.compose.StaticListScreen
import com.kelsos.mbrc.feature.library.R
import com.kelsos.mbrc.feature.library.artists.ArtistUiMessage
import com.kelsos.mbrc.feature.library.artists.GenreArtistsViewModel
import com.kelsos.mbrc.feature.library.compose.SortBottomSheet
import com.kelsos.mbrc.feature.library.compose.SortOption
import com.kelsos.mbrc.feature.library.compose.components.ArtistListItem
import com.kelsos.mbrc.feature.library.compose.components.rememberDefaultArtistPictureConnection
import com.kelsos.mbrc.feature.minicontrol.MiniControl
import com.kelsos.mbrc.feature.library.compose.tabs.alphabeticIndexLabel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import org.koin.androidx.compose.koinViewModel

private val artistSortOptions = listOf(
  SortOption(ArtistSortField.NAME, R.string.sort_by_name)
)

@Composable
fun GenreArtistsScreen(
  genreId: Long,
  genreName: String,
  onNavigateBack: () -> Unit,
  onNavigateToArtistAlbums: (Artist) -> Unit,
  onNavigateToPlayer: () -> Unit,
  snackbarHostState: SnackbarHostState,
  modifier: Modifier = Modifier,
  viewModel: GenreArtistsViewModel = koinViewModel()
) {
  val artists by viewModel.artists.collectAsStateWithLifecycle()
  val sortPreference by viewModel.sortPreference.collectAsStateWithLifecycle(
    initialValue = SortOrder.ASC
  )
  val artistPictureConnection = rememberDefaultArtistPictureConnection()
  var showSortSheet by rememberSaveable { mutableStateOf(false) }

  // Load genre artists
  LaunchedEffect(genreId) {
    viewModel.load(genreId)
  }

  LaunchedEffect(Unit) {
    viewModel.events.filterIsInstance<ArtistUiMessage.OpenArtistAlbums>().collect { event ->
      onNavigateToArtistAlbums(event.artist)
    }
  }

  val queueResults = remember {
    viewModel.events.map { event ->
      when (event) {
        is ArtistUiMessage.QueueSuccess -> Outcome.Success(event.tracksCount)
        is ArtistUiMessage.QueueFailed -> Outcome.Failure(AppError.OperationFailed)
        is ArtistUiMessage.NetworkUnavailable -> Outcome.Failure(AppError.NetworkUnavailable)
        else -> null
      }
    }.filterIsInstance<Outcome<Int>>()
  }

  QueueResultEffect(
    queueResults = queueResults,
    snackbarHostState = snackbarHostState
  )

  val sortDescription = stringResource(R.string.sort_button_description)

  ScreenScaffold(
    title = genreName,
    snackbarHostState = snackbarHostState,
    navigationIcon = NavigationIconType.Back(onNavigateBack),
    actionItems = listOf(
      ActionItem(
        icon = Icons.AutoMirrored.Filled.Sort,
        contentDescription = sortDescription,
        onClick = { showSortSheet = true }
      )
    ),
    modifier = modifier
  ) { paddingValues ->
    Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
      StaticListScreen(
        items = artists.orEmpty(),
        isLoading = artists == null,
        modifier = Modifier.weight(1f),
        emptyMessage = stringResource(R.string.artists_list_empty),
        emptyIcon = Icons.Default.Person,
        key = { it.id },
        scrollbarStyle = PagingScrollbarStyle.Indexed,
        indexLabel = { artist -> alphabeticIndexLabel(artist.artist, ignoreLeadingThe = true) }
      ) { artist ->
        ArtistListItem(
          artist = artist,
          onClick = { viewModel.queue(Queue.Default, artist) },
          onQueue = { queue -> viewModel.queue(queue, artist) },
          artistPictureConnection = artistPictureConnection
        )
      }

      MiniControl(
        onNavigateToPlayer = onNavigateToPlayer,
        snackbarHostState = snackbarHostState
      )
    }
  }

  if (showSortSheet) {
    SortBottomSheet(
      title = stringResource(R.string.sort_title),
      options = artistSortOptions,
      selectedField = ArtistSortField.NAME,
      selectedOrder = sortPreference,
      onSortSelected = { field, order ->
        viewModel.updateSortPreference(ArtistSortPreference(field, order))
      },
      onDismiss = { showSortSheet = false }
    )
  }
}
