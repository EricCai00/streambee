package com.kelsos.mbrc.feature.library.compose.drilldown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Language
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
import com.kelsos.mbrc.core.common.settings.GenreSortField
import com.kelsos.mbrc.core.common.settings.GenreSortPreference
import com.kelsos.mbrc.core.common.settings.SortOrder
import com.kelsos.mbrc.core.common.settings.SortPreference
import com.kelsos.mbrc.core.data.library.grouping.LanguageCategoryGroup
import com.kelsos.mbrc.core.ui.compose.ActionItem
import com.kelsos.mbrc.core.ui.compose.NavigationIconType
import com.kelsos.mbrc.core.ui.compose.PagingListScreen
import com.kelsos.mbrc.core.ui.compose.ScreenScaffold
import com.kelsos.mbrc.feature.library.R
import com.kelsos.mbrc.feature.library.compose.SortBottomSheet
import com.kelsos.mbrc.feature.library.compose.SortOption
import com.kelsos.mbrc.feature.library.compose.components.GroupingListItem
import com.kelsos.mbrc.feature.library.compose.tabs.languageLabel
import com.kelsos.mbrc.feature.library.grouping.LanguageCategoriesViewModel
import com.kelsos.mbrc.feature.minicontrol.MiniControl
import org.koin.androidx.compose.koinViewModel

private val categorySortOptions = listOf(
  SortOption(GenreSortField.NAME, R.string.sort_by_name)
)

@Composable
fun LanguageCategoriesScreen(
  language: String,
  onNavigateBack: () -> Unit,
  onNavigateToCategoryAlbums: (LanguageCategoryGroup) -> Unit,
  onNavigateToPlayer: () -> Unit,
  snackbarHostState: SnackbarHostState,
  modifier: Modifier = Modifier,
  viewModel: LanguageCategoriesViewModel = koinViewModel()
) {
  val categories = viewModel.categories.collectAsLazyPagingItems()
  val sortPreference by viewModel.sortPreference.collectAsStateWithLifecycle(
    initialValue = SortPreference(GenreSortField.NAME, SortOrder.ASC)
  )
  val unknownLanguage = stringResource(R.string.unknown_language)
  val uncategorized = stringResource(R.string.unknown_genre_category)
  var showSortSheet by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(language) { viewModel.load(language) }

  ScreenScaffold(
    title = languageLabel(language, unknownLanguage),
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
        items = categories,
        modifier = Modifier.weight(1f),
        emptyMessage = stringResource(R.string.language_categories_list_empty),
        emptyIcon = Icons.Default.Language,
        key = { it.category }
      ) { category ->
        GroupingListItem(
          title = category.category.ifBlank { uncategorized },
          albumCount = category.albumCount,
          icon = Icons.Default.Language,
          onClick = { onNavigateToCategoryAlbums(category) }
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
      options = categorySortOptions,
      selectedField = sortPreference.field,
      selectedOrder = sortPreference.order,
      onSortSelected = { field, order ->
        viewModel.updateSortPreference(GenreSortPreference(field, order))
      },
      onDismiss = { showSortSheet = false }
    )
  }
}
