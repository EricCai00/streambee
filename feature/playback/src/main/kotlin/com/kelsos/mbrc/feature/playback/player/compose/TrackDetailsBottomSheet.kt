package com.kelsos.mbrc.feature.playback.player.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kelsos.mbrc.core.common.state.TrackDetails
import com.kelsos.mbrc.core.common.state.TrackInfo
import com.kelsos.mbrc.core.common.state.TrackRating
import com.kelsos.mbrc.feature.playback.R

private const val TAGS_TAB = 0
private const val PROPERTIES_TAB = 1
private const val COMMENTS_MAX_LINES = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackDetailsBottomSheet(
  trackInfo: TrackInfo,
  trackDetails: TrackDetails,
  trackRating: TrackRating,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var selectedTabIndex by rememberSaveable { mutableIntStateOf(TAGS_TAB) }
  // Coerce a value restored from the previous three-tab layout, where
  // Properties was index 2.
  val visibleTabIndex = selectedTabIndex.coerceIn(TAGS_TAB, PROPERTIES_TAB)
  val tabTitles = listOf(
    stringResource(R.string.track_details_tab_tags),
    stringResource(R.string.track_details_tab_properties)
  )

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    modifier = modifier
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 24.dp)
    ) {
      Text(
        text = stringResource(R.string.track_details_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
      )

      TabRow(selectedTabIndex = visibleTabIndex) {
        tabTitles.forEachIndexed { index, title ->
          Tab(
            selected = visibleTabIndex == index,
            onClick = { selectedTabIndex = index },
            text = { Text(text = title) }
          )
        }
      }

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp, vertical = 16.dp)
      ) {
        when (visibleTabIndex) {
          TAGS_TAB -> TagsContent(
            trackInfo = trackInfo,
            trackDetails = trackDetails,
            trackRating = trackRating
          )

          PROPERTIES_TAB -> PropertiesContent(
            trackInfo = trackInfo,
            trackDetails = trackDetails
          )
        }
      }
    }
  }
}

@Composable
private fun TagsContent(
  trackInfo: TrackInfo,
  trackDetails: TrackDetails,
  trackRating: TrackRating
) {
  DetailRow(
    label = stringResource(R.string.track_details_track_title),
    value = trackInfo.title
  )
  DetailRow(
    label = stringResource(R.string.track_details_artist),
    value = trackInfo.artist
  )
  DetailRow(
    label = stringResource(R.string.track_details_album_artist),
    value = trackDetails.albumArtist
  )
  DetailRow(
    label = stringResource(R.string.track_details_album),
    value = trackInfo.album
  )
  DetailRow(
    label = stringResource(R.string.track_details_year),
    value = trackInfo.year
  )
  DetailRow(
    label = stringResource(R.string.track_details_track),
    value = formatTrackNumber(trackDetails.trackNo, trackDetails.trackCount)
  )
  DetailRow(
    label = stringResource(R.string.track_details_disc),
    value = formatTrackNumber(trackDetails.discNo, trackDetails.discCount)
  )
  DetailRow(
    label = stringResource(R.string.track_details_publisher),
    value = trackDetails.publisher
  )
  DetailRow(
    label = stringResource(R.string.track_details_composer),
    value = trackDetails.composer
  )
  DetailRow(
    label = stringResource(R.string.track_details_conductor),
    value = trackDetails.conductor
  )
  DetailRow(
    label = stringResource(R.string.track_details_genre),
    value = trackDetails.genre
  )
  DetailRow(
    label = stringResource(R.string.track_details_grouping),
    value = trackDetails.grouping
  )
  DetailRow(
    label = stringResource(R.string.track_details_track_rating),
    value = formatRating(trackRating.rating)
  )
  DetailRow(
    label = stringResource(R.string.track_details_album_rating),
    value = trackDetails.ratingAlbum
  )
  DetailRow(
    label = stringResource(R.string.track_details_comment),
    value = trackDetails.comment,
    maxLines = COMMENTS_MAX_LINES
  )
  DetailRow(
    label = stringResource(R.string.track_details_original_artist),
    value = trackDetails.originalArtist
  )
  DetailRow(
    label = stringResource(R.string.track_details_original_album),
    value = trackDetails.originalAlbum
  )
  DetailRow(
    label = stringResource(R.string.track_details_original_year),
    value = trackDetails.originalYear
  )
  DetailRow(
    label = stringResource(R.string.track_details_bpm),
    value = trackDetails.bpm
  )
  DetailRow(
    label = stringResource(R.string.track_details_tempo),
    value = trackDetails.tempo
  )
  DetailRow(
    label = stringResource(R.string.track_details_mood),
    value = trackDetails.mood
  )
  DetailRow(
    label = stringResource(R.string.track_details_occasion),
    value = trackDetails.occasion
  )
  DetailRow(
    label = stringResource(R.string.track_details_keywords),
    value = trackDetails.keywords
  )
  DetailRow(
    label = stringResource(R.string.track_details_language),
    value = trackDetails.language
  )
  DetailRow(
    label = stringResource(R.string.track_details_region),
    value = trackDetails.region
  )
  DetailRow(
    label = stringResource(R.string.track_details_continent),
    value = trackDetails.continent
  )
  DetailRow(
    label = stringResource(R.string.track_details_custom_3),
    value = trackDetails.custom3
  )
  DetailRow(
    label = stringResource(R.string.track_details_custom_4),
    value = trackDetails.custom4
  )
  DetailRow(
    label = stringResource(R.string.track_details_custom_5),
    value = trackDetails.custom5
  )
  DetailRow(
    label = stringResource(R.string.track_details_custom_6),
    value = trackDetails.custom6
  )
  DetailRow(
    label = stringResource(R.string.track_details_custom_7),
    value = trackDetails.custom7
  )
  DetailRow(
    label = stringResource(R.string.track_details_custom_8),
    value = trackDetails.custom8
  )
}

@Composable
private fun PropertiesContent(trackInfo: TrackInfo, trackDetails: TrackDetails) {
  DetailRow(
    label = stringResource(R.string.track_details_type),
    value = trackDetails.kind
  )
  DetailRow(
    label = stringResource(R.string.track_details_format),
    value = trackDetails.format
  )
  DetailRow(
    label = stringResource(R.string.track_details_encoded_with),
    value = trackDetails.encoder
  )
  DetailRow(
    label = stringResource(R.string.track_details_channels),
    value = trackDetails.channels
  )
  DetailRow(
    label = stringResource(R.string.track_details_bitrate),
    value = trackDetails.bitrate
  )
  DetailRow(
    label = stringResource(R.string.track_details_sample_rate),
    value = trackDetails.sampleRate
  )
  DetailRow(
    label = stringResource(R.string.track_details_duration),
    value = trackDetails.duration
  )
  DetailRow(
    label = stringResource(R.string.track_details_size),
    value = trackDetails.size
  )
  DetailRow(
    label = stringResource(R.string.track_details_volume_leveling),
    value = trackDetails.volumeLeveling
  )
  DetailRow(
    label = stringResource(R.string.track_details_date_modified),
    value = trackDetails.dateModified
  )
  DetailRow(
    label = stringResource(R.string.track_details_date_added),
    value = trackDetails.dateAdded
  )
  DetailRow(
    label = stringResource(R.string.track_details_play_count),
    value = trackDetails.playCount
  )
  DetailRow(
    label = stringResource(R.string.track_details_skip_count),
    value = trackDetails.skipCount
  )
  DetailRow(
    label = stringResource(R.string.track_details_last_played),
    value = trackDetails.lastPlayed
  )
  DetailRow(
    label = stringResource(R.string.track_details_origin),
    value = trackDetails.origin
  )
  DetailRow(
    label = stringResource(R.string.track_details_location),
    value = trackInfo.path
  )
}

@Composable
private fun DetailRow(label: String, value: String, maxLines: Int = Int.MAX_VALUE) {
  if (value.isNotBlank()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(weight = 0.4f)
      )
      Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(weight = 0.6f),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

private fun formatTrackNumber(number: String, count: String): String = when {
  number.isBlank() -> ""
  count.isBlank() -> number
  else -> "$number / $count"
}

private fun formatRating(rating: Float?): String = when {
  rating == null -> ""
  rating % 1f == 0f -> rating.toInt().toString()
  else -> rating.toString()
}
