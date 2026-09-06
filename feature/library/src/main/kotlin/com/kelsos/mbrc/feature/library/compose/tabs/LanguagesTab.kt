package com.kelsos.mbrc.feature.library.compose.tabs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.kelsos.mbrc.core.common.settings.GenreSortField
import com.kelsos.mbrc.core.common.settings.GenreSortPreference
import com.kelsos.mbrc.core.common.settings.SortOrder
import com.kelsos.mbrc.core.common.settings.SortPreference
import com.kelsos.mbrc.core.common.utilities.Outcome
import com.kelsos.mbrc.core.data.library.grouping.LanguageGroup
import com.kelsos.mbrc.feature.library.R
import com.kelsos.mbrc.feature.library.compose.SortBottomSheet
import com.kelsos.mbrc.feature.library.compose.SortOption
import com.kelsos.mbrc.feature.library.compose.components.GroupingListItem
import com.kelsos.mbrc.feature.library.grouping.BrowseLanguageViewModel
import kotlinx.coroutines.flow.emptyFlow
import org.koin.androidx.compose.koinViewModel

private val languageSortOptions = listOf(
  SortOption(GenreSortField.NAME, R.string.sort_by_name)
)

@Composable
fun LanguagesTab(
  snackbarHostState: SnackbarHostState,
  isSyncing: Boolean,
  showSortSheet: Boolean,
  indexedScrollbar: Boolean,
  onNavigateToLanguageCategories: (LanguageGroup) -> Unit,
  onDismissSortSheet: () -> Unit,
  onSync: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: BrowseLanguageViewModel = koinViewModel()
) {
  val languages = viewModel.languages.collectAsLazyPagingItems()
  val showSync by viewModel.showSync.collectAsStateWithLifecycle(initialValue = true)
  val sortPreference by viewModel.sortPreference.collectAsStateWithLifecycle(
    initialValue = SortPreference(GenreSortField.NAME, SortOrder.ASC)
  )
  val unknownLanguage = stringResource(R.string.unknown_language)

  LibraryBrowseTab(
    items = languages,
    queueResults = emptyFlow<Outcome<Int>>(),
    snackbarHostState = snackbarHostState,
    syncState = SyncState(isSyncing = isSyncing, showSync = showSync, onSync = onSync),
    emptyState = EmptyState(
      message = stringResource(R.string.languages_list_empty),
      icon = Icons.Default.Language
    ),
    itemKey = { it.language },
    indexedScrollbar = indexedScrollbar,
    indexLabel = { language -> alphabeticIndexLabel(language.language) },
    modifier = modifier
  ) { language ->
    GroupingListItem(
      title = languageLabel(language.language, unknownLanguage),
      albumCount = language.albumCount,
      icon = Icons.Default.Language,
      onClick = { onNavigateToLanguageCategories(language) }
    )
  }

  if (showSortSheet) {
    SortBottomSheet(
      title = stringResource(R.string.sort_title),
      options = languageSortOptions,
      selectedField = sortPreference.field,
      selectedOrder = sortPreference.order,
      onSortSelected = { field, order ->
        viewModel.updateSortPreference(GenreSortPreference(field, order))
      },
      onDismiss = onDismissSortSheet
    )
  }
}

fun languageLabel(language: String, unknownLanguage: String): String =
  language.ifBlank { unknownLanguage }
