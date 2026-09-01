package com.kelsos.mbrc.feature.library.compose.drilldown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
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
import androidx.paging.compose.collectAsLazyPagingItems
import com.kelsos.mbrc.core.common.settings.GenreSortField
import com.kelsos.mbrc.core.common.settings.GenreSortPreference
import com.kelsos.mbrc.core.common.settings.SortOrder
import com.kelsos.mbrc.core.common.settings.SortPreference
import com.kelsos.mbrc.core.common.utilities.AppError
import com.kelsos.mbrc.core.common.utilities.Outcome
import com.kelsos.mbrc.core.data.library.album.AlbumRepository
import com.kelsos.mbrc.core.data.library.genre.Genre
import com.kelsos.mbrc.core.queue.Queue
import com.kelsos.mbrc.core.ui.compose.ActionItem
import com.kelsos.mbrc.core.ui.compose.NavigationIconType
import com.kelsos.mbrc.core.ui.compose.PagingListScreen
import com.kelsos.mbrc.core.ui.compose.QueueResultEffect
import com.kelsos.mbrc.core.ui.compose.ScreenScaffold
import com.kelsos.mbrc.feature.library.R
import com.kelsos.mbrc.feature.library.compose.SortBottomSheet
import com.kelsos.mbrc.feature.library.compose.SortOption
import com.kelsos.mbrc.feature.library.compose.components.GenreListItem
import com.kelsos.mbrc.feature.library.compose.components.rememberGenreAlbumPreviews
import com.kelsos.mbrc.feature.library.genres.CategoryGenresViewModel
import com.kelsos.mbrc.feature.library.genres.GenreUiMessage
import com.kelsos.mbrc.feature.minicontrol.MiniControl
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private val genreSortOptions = listOf(
  SortOption(GenreSortField.NAME, R.string.sort_by_name)
)

@Composable
fun CategoryGenresScreen(
  categoryName: String,
  onNavigateBack: () -> Unit,
  onNavigateToGenreAlbums: (Genre) -> Unit,
  onNavigateToPlayer: () -> Unit,
  snackbarHostState: SnackbarHostState,
  modifier: Modifier = Modifier,
  viewModel: CategoryGenresViewModel = koinViewModel()
) {
  val albumRepository: AlbumRepository = koinInject()
  val genres = viewModel.genres.collectAsLazyPagingItems()
  val sortPreference by viewModel.sortPreference.collectAsStateWithLifecycle(
    initialValue = SortPreference(GenreSortField.NAME, SortOrder.ASC)
  )
  var showSortSheet by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(categoryName) {
    viewModel.load(categoryName)
  }

  LaunchedEffect(Unit) {
    viewModel.events.filterIsInstance<GenreUiMessage.OpenAlbums>().collect { event ->
      onNavigateToGenreAlbums(event.genre)
    }
  }

  val queueResults = remember {
    viewModel.events.map { event ->
      when (event) {
        is GenreUiMessage.QueueSuccess -> Outcome.Success(event.tracksCount)
        is GenreUiMessage.QueueFailed -> Outcome.Failure(AppError.OperationFailed)
        is GenreUiMessage.NetworkUnavailable -> Outcome.Failure(AppError.NetworkUnavailable)
        else -> null
      }
    }.filterIsInstance<Outcome<Int>>()
  }

  QueueResultEffect(queueResults = queueResults, snackbarHostState = snackbarHostState)

  ScreenScaffold(
    title = categoryName.ifEmpty { stringResource(R.string.unknown_genre_category) },
    snackbarHostState = snackbarHostState,
    navigationIcon = NavigationIconType.Back(onNavigateBack),
    actionItems = listOf(
      ActionItem(
        icon = Icons.AutoMirrored.Filled.Sort,
        contentDescription = stringResource(R.string.sort_button_description),
        onClick = { showSortSheet = true }
      )
    ),
    modifier = modifier
  ) { paddingValues ->
    Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
      PagingListScreen(
        items = genres,
        modifier = Modifier.weight(1f),
        emptyMessage = stringResource(R.string.genres_list_empty),
        emptyIcon = Icons.AutoMirrored.Filled.QueueMusic,
        key = { it.id }
      ) { genre ->
        GenreListItem(
          genre = genre,
          onClick = { viewModel.queue(Queue.Default, genre) },
          onQueue = { queue -> viewModel.queue(queue, genre) },
          albumPreviews = rememberGenreAlbumPreviews(
            repository = albumRepository,
            genreId = genre.id
          )
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
      options = genreSortOptions,
      selectedField = sortPreference.field,
      selectedOrder = sortPreference.order,
      onSortSelected = { field, order ->
        viewModel.updateSortPreference(GenreSortPreference(field, order))
      },
      onDismiss = { showSortSheet = false }
    )
  }
}
