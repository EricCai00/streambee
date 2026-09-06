package com.kelsos.mbrc.feature.library.compose.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import com.kelsos.mbrc.core.ui.compose.DoubleLineRow
import com.kelsos.mbrc.feature.library.R

@Composable
fun GroupingListItem(
  title: String,
  albumCount: Int,
  icon: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  DoubleLineRow(
    title = title,
    subtitle = pluralStringResource(R.plurals.album, albumCount, albumCount),
    onClick = onClick,
    modifier = modifier,
    leadingContent = {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary
      )
    }
  )
}
