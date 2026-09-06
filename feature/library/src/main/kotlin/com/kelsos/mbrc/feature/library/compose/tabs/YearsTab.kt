package com.kelsos.mbrc.feature.library.compose.tabs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.kelsos.mbrc.core.common.settings.AlbumSortField
import com.kelsos.mbrc.core.common.settings.AlbumSortPreference
import com.kelsos.mbrc.core.common.settings.SortOrder
import com.kelsos.mbrc.core.common.settings.SortPreference
import com.kelsos.mbrc.core.common.utilities.Outcome
import com.kelsos.mbrc.core.data.library.grouping.DecadeGroup
import com.kelsos.mbrc.feature.library.R
import com.kelsos.mbrc.feature.library.compose.SortBottomSheet
import com.kelsos.mbrc.feature.library.compose.SortOption
import com.kelsos.mbrc.feature.library.compose.components.GroupingListItem
import com.kelsos.mbrc.feature.library.grouping.BrowseYearViewModel
import kotlinx.coroutines.flow.emptyFlow
import org.koin.androidx.compose.koinViewModel

private val yearSortOptions = listOf(
  SortOption(AlbumSortField.YEAR, R.string.sort_by_year)
)

@Composable
fun YearsTab(
  snackbarHostState: SnackbarHostState,
  isSyncing: Boolean,
  showSortSheet: Boolean,
  indexedScrollbar: Boolean,
  onNavigateToDecadeYears: (DecadeGroup) -> Unit,
  onDismissSortSheet: () -> Unit,
  onSync: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: BrowseYearViewModel = koinViewModel()
) {
  val decades = viewModel.decades.collectAsLazyPagingItems()
  val showSync by viewModel.showSync.collectAsStateWithLifecycle(initialValue = true)
  val sortPreference by viewModel.sortPreference.collectAsStateWithLifecycle(
    initialValue = SortPreference(AlbumSortField.YEAR, SortOrder.DESC)
  )
  val unknownYear = stringResource(R.string.unknown_year)

  LibraryBrowseTab(
    items = decades,
    queueResults = emptyFlow<Outcome<Int>>(),
    snackbarHostState = snackbarHostState,
    syncState = SyncState(isSyncing = isSyncing, showSync = showSync, onSync = onSync),
    emptyState = EmptyState(
      message = stringResource(R.string.decades_list_empty),
      icon = Icons.Default.DateRange
    ),
    itemKey = { it.decade },
    indexedScrollbar = indexedScrollbar,
    indexLabel = { decade -> decadeLabel(decade.decade, unknownYear) },
    modifier = modifier
  ) { decade ->
    GroupingListItem(
      title = decadeLabel(decade.decade, unknownYear),
      albumCount = decade.albumCount,
      icon = Icons.Default.DateRange,
      onClick = { onNavigateToDecadeYears(decade) }
    )
  }

  if (showSortSheet) {
    SortBottomSheet(
      title = stringResource(R.string.sort_title),
      options = yearSortOptions,
      selectedField = sortPreference.field,
      selectedOrder = sortPreference.order,
      onSortSelected = { field, order ->
        viewModel.updateSortPreference(AlbumSortPreference(field, order))
      },
      onDismiss = onDismissSortSheet
    )
  }
}

fun decadeLabel(decade: Int, unknownYear: String): String =
  if (decade < 0) unknownYear else "${decade}s"
