package com.kelsos.mbrc.feature.library.compose.drilldown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.kelsos.mbrc.core.common.settings.AlbumSortField
import com.kelsos.mbrc.core.common.settings.AlbumSortPreference
import com.kelsos.mbrc.core.common.settings.SortOrder
import com.kelsos.mbrc.core.common.settings.SortPreference
import com.kelsos.mbrc.core.data.library.grouping.YearGroup
import com.kelsos.mbrc.core.ui.compose.ActionItem
import com.kelsos.mbrc.core.ui.compose.NavigationIconType
import com.kelsos.mbrc.core.ui.compose.PagingListScreen
import com.kelsos.mbrc.core.ui.compose.ScreenScaffold
import com.kelsos.mbrc.feature.library.R
import com.kelsos.mbrc.feature.library.compose.SortBottomSheet
import com.kelsos.mbrc.feature.library.compose.SortOption
import com.kelsos.mbrc.feature.library.compose.components.GroupingListItem
import com.kelsos.mbrc.feature.library.compose.tabs.decadeLabel
import com.kelsos.mbrc.feature.library.grouping.DecadeYearsViewModel
import com.kelsos.mbrc.feature.minicontrol.MiniControl
import org.koin.androidx.compose.koinViewModel

private val yearSortOptions = listOf(
  SortOption(AlbumSortField.YEAR, R.string.sort_by_year)
)

@Composable
fun DecadeYearsScreen(
  decade: Int,
  onNavigateBack: () -> Unit,
  onNavigateToYearAlbums: (YearGroup) -> Unit,
  onNavigateToPlayer: () -> Unit,
  snackbarHostState: SnackbarHostState,
  modifier: Modifier = Modifier,
  viewModel: DecadeYearsViewModel = koinViewModel()
) {
  val years = viewModel.years.collectAsLazyPagingItems()
  val sortPreference by viewModel.sortPreference.collectAsStateWithLifecycle(
    initialValue = SortPreference(AlbumSortField.YEAR, SortOrder.DESC)
  )
  val unknownYear = stringResource(R.string.unknown_year)
  var showSortSheet by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(decade) { viewModel.load(decade) }

  ScreenScaffold(
    title = decadeLabel(decade, unknownYear),
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
        items = years,
        modifier = Modifier.weight(1f),
        emptyMessage = stringResource(R.string.years_list_empty),
        emptyIcon = Icons.Default.DateRange,
        key = { it.year }
      ) { year ->
        GroupingListItem(
          title = year.year.ifBlank { unknownYear },
          albumCount = year.albumCount,
          icon = Icons.Default.DateRange,
          onClick = { onNavigateToYearAlbums(year) }
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
      options = yearSortOptions,
      selectedField = sortPreference.field,
      selectedOrder = sortPreference.order,
      onSortSelected = { field, order ->
        viewModel.updateSortPreference(AlbumSortPreference(field, order))
      },
      onDismiss = { showSortSheet = false }
    )
  }
}
