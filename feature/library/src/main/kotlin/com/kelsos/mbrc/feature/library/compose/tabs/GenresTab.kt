package com.kelsos.mbrc.feature.library.compose.tabs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.kelsos.mbrc.core.data.library.genre.GenreCategory
import com.kelsos.mbrc.feature.library.R
import com.kelsos.mbrc.feature.library.compose.SortBottomSheet
import com.kelsos.mbrc.feature.library.compose.SortOption
import com.kelsos.mbrc.feature.library.compose.components.GenreCategoryListItem
import com.kelsos.mbrc.feature.library.compose.components.rememberGenreCategoryAlbumPreviews
import com.kelsos.mbrc.feature.library.genres.BrowseGenreViewModel
import com.kelsos.mbrc.feature.library.genres.GenreUiMessage
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

val genreSortOptions = listOf(
  SortOption(GenreSortField.NAME, R.string.sort_by_name)
)

@Composable
fun GenresTab(
  snackbarHostState: SnackbarHostState,
  isSyncing: Boolean,
  showSortSheet: Boolean,
  indexedScrollbar: Boolean,
  onNavigateToCategoryGenres: (GenreCategory) -> Unit,
  onDismissSortSheet: () -> Unit,
  onSync: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: BrowseGenreViewModel = koinViewModel()
) {
  val albumRepository: AlbumRepository = koinInject()
  val categories = viewModel.categories.collectAsLazyPagingItems()
  val showSync by viewModel.showSync.collectAsStateWithLifecycle(initialValue = true)
  val sortPreference by viewModel.sortPreference.collectAsStateWithLifecycle(
    initialValue = SortPreference(GenreSortField.NAME, SortOrder.ASC)
  )

  // Handle navigation events
  LaunchedEffect(Unit) {
    viewModel.events.collect { event ->
      when (event) {
        is GenreUiMessage.OpenGenres -> onNavigateToCategoryGenres(event.category)
        else -> Unit
      }
    }
  }

  // Handle queue results
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

  LibraryBrowseTab(
    items = categories,
    queueResults = queueResults,
    snackbarHostState = snackbarHostState,
    syncState = SyncState(
      isSyncing = isSyncing,
      showSync = showSync,
      onSync = onSync
    ),
    emptyState = EmptyState(
      message = stringResource(R.string.genre_categories_list_empty),
      icon = Icons.AutoMirrored.Filled.QueueMusic
    ),
    itemKey = { it.category },
    indexedScrollbar = indexedScrollbar,
    indexLabel = { category -> alphabeticIndexLabel(category.category) },
    modifier = modifier
  ) { category ->
    GenreCategoryListItem(
      category = category,
      onClick = { viewModel.openCategory(category) },
      albumPreviews = rememberGenreCategoryAlbumPreviews(
        repository = albumRepository,
        category = category.category
      )
    )
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
      onDismiss = onDismissSortSheet
    )
  }
}
