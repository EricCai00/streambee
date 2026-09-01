package com.kelsos.mbrc.feature.library.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kelsos.mbrc.core.data.library.album.Album
import com.kelsos.mbrc.core.data.library.album.AlbumRepository
import kotlin.coroutines.cancellation.CancellationException

private val GENRE_COVER_CORNER_SHAPE = RoundedCornerShape(8.dp)
private val GENRE_COVER_LAYER_OFFSET_X = 10.dp
private val GENRE_COVER_LAYER_OFFSET_Y = 7.dp
private const val MAX_STACK_LAYERS = 3

/**
 * Shows up to three real album covers as a RYM-style stack. The first cover is
 * kept in front; missing covers simply reduce the number of visible layers.
 */
@Composable
fun StackedAlbumCovers(
  albums: List<Album>,
  modifier: Modifier = Modifier,
  size: Dp = 96.dp,
  layerOffsetX: Dp = GENRE_COVER_LAYER_OFFSET_X,
  layerOffsetY: Dp = GENRE_COVER_LAYER_OFFSET_Y
) {
  val covers = remember(albums) {
    albums
      .distinctBy { "${it.artist}\u0000${it.album}" }
      .take(3)
  }
  // Reserve the full three-layer footprint even when this row has only one or two covers,
  // keeping the title column aligned across every genre/category item.
  val lastLayer = (MAX_STACK_LAYERS - 1).toFloat()
  val stackModifier = modifier
    .width(size + layerOffsetX * lastLayer)
    .height(size + layerOffsetY * lastLayer)

  Box(modifier = stackModifier) {
    if (covers.isEmpty()) {
      Box(
        modifier = Modifier
          .size(size)
          .align(Alignment.Center)
          .clip(GENRE_COVER_CORNER_SHAPE)
          .background(MaterialTheme.colorScheme.surface)
          .border(1.dp, MaterialTheme.colorScheme.outline, GENRE_COVER_CORNER_SHAPE),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.QueueMusic,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(32.dp)
        )
      }
    } else {
      // Center shorter stacks inside the full three-layer footprint: one cover
      // gets one full layer of inset, while two covers get half a layer on each
      // side.
      val firstLayer = (MAX_STACK_LAYERS - covers.size) / 2f
      // Draw the back layers first, keeping the first album at the front-left.
      covers.indices.reversed().forEach { coverIndex ->
        val album = covers[coverIndex]
        val layer = firstLayer + coverIndex.toFloat()
        AlbumCoverByKey(
          artist = album.artist,
          album = album.album,
          size = size,
          modifier = Modifier
            .size(size)
            .offset(
              x = layerOffsetX * layer.toFloat(),
              y = layerOffsetY * layer.toFloat()
            )
            .clip(GENRE_COVER_CORNER_SHAPE)
            .border(1.dp, MaterialTheme.colorScheme.surface, GENRE_COVER_CORNER_SHAPE)
        )
      }
    }
  }
}

@Composable
fun rememberGenreAlbumPreviews(repository: AlbumRepository, genreId: Long): List<Album> =
  produceState<List<Album>>(emptyList(), repository, genreId) {
    value = try {
      repository.getPreviewAlbumsByGenre(genreId)
    } catch (error: CancellationException) {
      throw error
    } catch (_: Throwable) {
      emptyList()
    }
  }.value

@Composable
fun rememberGenreCategoryAlbumPreviews(repository: AlbumRepository, category: String): List<Album> =
  produceState<List<Album>>(emptyList(), repository, category) {
    value = try {
      repository.getPreviewAlbumsByCategory(category)
    } catch (error: CancellationException) {
      throw error
    } catch (_: Throwable) {
      emptyList()
    }
  }.value
