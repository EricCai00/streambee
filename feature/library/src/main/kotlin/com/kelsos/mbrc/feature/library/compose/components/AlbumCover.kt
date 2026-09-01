package com.kelsos.mbrc.feature.library.compose.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Size
import com.kelsos.mbrc.core.data.library.track.TrackRepository
import com.kelsos.mbrc.core.ui.R
import com.kelsos.mbrc.feature.library.data.CoverCache
import com.kelsos.mbrc.feature.library.data.HighResolutionCoverCache
import java.io.File
import okio.ByteString.Companion.encodeUtf8
import org.koin.compose.koinInject

/**
 * Displays an album cover by computing the cover key from artist and album names.
 *
 * Use this version when the cover hash is not available (e.g., for tracks).
 * The key is computed once and Coil handles missing files gracefully by
 * showing the error placeholder.
 *
 * Note: This version does NOT check if the file exists - it lets Coil
 * handle missing files, which is more performant than blocking I/O
 * during composition.
 *
 * @param artist The artist name
 * @param album The album name
 * @param modifier Modifier for the composable
 * @param size The size of the cover image
 */
@Composable
fun AlbumCoverByKey(
  artist: String,
  album: String,
  modifier: Modifier = Modifier,
  size: Dp = 48.dp
) {
  if (LocalInspectionMode.current) {
    Image(
      painter = painterResource(R.drawable.ic_image_no_cover),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = modifier.size(size)
    )
    return
  }

  val context = LocalContext.current
  val density = LocalDensity.current
  val sizePx = with(density) { size.roundToPx() }

  val key = remember(artist, album) {
    "${artist}_$album".encodeUtf8().sha1().hex().uppercase()
  }

  val coverFile = remember(key) {
    File(File(context.cacheDir, "covers"), key)
  }

  val coverCache: CoverCache = koinInject()
  val loadedFile = produceState<File?>(
    initialValue = coverFile.takeIf { it.isFile && it.length() > 0L },
    artist,
    album,
    key
  ) {
    if (value == null) {
      value = runCatching { coverCache.load(artist, album, key) }.getOrNull()
    }
  }.value
  val sourceFile = loadedFile ?: coverFile
  val sourceToken = remember(sourceFile, loadedFile) {
    "${sourceFile.absolutePath}_${sourceFile.lastModified()}_${sourceFile.length()}"
  }

  val imageRequest = remember(key, sizePx, sourceToken) {
    ImageRequest.Builder(context)
      .data(sourceFile)
      .size(Size(sizePx, sizePx))
      .memoryCacheKey("cover_${key}_${sourceToken}_$sizePx")
      .diskCacheKey("cover_${key}_$sourceToken")
      .memoryCachePolicy(CachePolicy.ENABLED)
      .diskCachePolicy(CachePolicy.ENABLED)
      .build()
  }

  AsyncImage(
    model = imageRequest,
    contentDescription = null,
    placeholder = painterResource(R.drawable.ic_image_no_cover),
    error = painterResource(R.drawable.ic_image_no_cover),
    contentScale = ContentScale.Crop,
    modifier = modifier.size(size)
  )
}

/** Displays the cached thumbnail immediately, then upgrades it to the original artwork. */
@Composable
fun HighResolutionAlbumCover(
  artist: String,
  album: String,
  trackPath: String?,
  modifier: Modifier = Modifier,
  size: Dp = 124.dp
) {
  val key = remember(artist, album) {
    "${artist}_$album".encodeUtf8().sha1().hex().uppercase()
  }
  val coverCache: HighResolutionCoverCache = koinInject()
  val highResolutionFile = produceState<File?>(null, trackPath, key) {
    value = trackPath?.let { path ->
      runCatching { coverCache.load(path, key) }.getOrNull()
    }
  }.value
  ProgressiveAlbumCover(
    key = key,
    highResolutionFile = highResolutionFile,
    modifier = modifier,
    size = size
  )
}

/** Loads original artwork only while a grid item is composed (visible or prefetched). */
@Composable
fun HighResolutionAlbumGridCover(artist: String, album: String, modifier: Modifier = Modifier) {
  val coverCache: HighResolutionCoverCache = koinInject()
  val trackRepository: TrackRepository = koinInject()
  val key = remember(artist, album) {
    "${artist}_$album".encodeUtf8().sha1().hex().uppercase()
  }
  val highResolutionFile = produceState<File?>(null, artist, album, key) {
    value = runCatching {
      coverCache.load(album, artist, key, trackRepository)
    }.getOrNull()
  }.value

  BoxWithConstraints(modifier = modifier) {
    ProgressiveAlbumCover(
      key = key,
      highResolutionFile = highResolutionFile,
      modifier = Modifier.fillMaxSize(),
      size = maxWidth,
      decodeOriginal = false
    )
  }
}

@Composable
private fun ProgressiveAlbumCover(
  key: String,
  highResolutionFile: File?,
  modifier: Modifier,
  size: Dp,
  decodeOriginal: Boolean = true
) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val sizePx = with(density) { size.roundToPx() }
  val coverFile = remember(key) { File(File(context.cacheDir, "covers"), key) }
  val sourceFile = highResolutionFile ?: coverFile
  val sourceToken = remember(sourceFile) {
    "${sourceFile.absolutePath}_${sourceFile.lastModified()}_${sourceFile.length()}"
  }
  val imageRequest = remember(key, sizePx, sourceToken, highResolutionFile) {
    ImageRequest.Builder(context)
      .data(sourceFile)
      .size(
        if (highResolutionFile != null && decodeOriginal) {
          Size.ORIGINAL
        } else {
          Size(sizePx, sizePx)
        }
      )
      .memoryCacheKey("cover_high_${key}_${sourceToken}_$sizePx")
      .diskCacheKey("cover_high_${key}_$sourceToken")
      .memoryCachePolicy(CachePolicy.ENABLED)
      .diskCachePolicy(CachePolicy.ENABLED)
      .build()
  }

  AsyncImage(
    model = imageRequest,
    contentDescription = null,
    placeholder = painterResource(R.drawable.ic_image_no_cover),
    error = painterResource(R.drawable.ic_image_no_cover),
    contentScale = ContentScale.Crop,
    modifier = modifier.size(size)
  )
}
