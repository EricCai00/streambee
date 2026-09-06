package com.kelsos.mbrc.core.ui.compose

import androidx.annotation.StringRes
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kelsos.mbrc.core.ui.R

/** Navigation actions shared by every track overflow menu. */
@Composable
fun TrackNavigationMenuItems(
  onGoToAlbum: (() -> Unit)?,
  onGoToArtist: (() -> Unit)?,
  onGoToGenre: (() -> Unit)?
) {
  TrackNavigationMenuItem(
    label = R.string.go_to_album,
    onClick = onGoToAlbum
  )
  TrackNavigationMenuItem(
    label = R.string.go_to_artist,
    onClick = onGoToArtist
  )
  TrackNavigationMenuItem(
    label = R.string.go_to_genre,
    onClick = onGoToGenre
  )
}

@Composable
private fun TrackNavigationMenuItem(@StringRes label: Int, onClick: (() -> Unit)?) {
  DropdownMenuItem(
    text = { Text(stringResource(label)) },
    enabled = onClick != null,
    onClick = { onClick?.invoke() }
  )
}
