package com.kelsos.mbrc.feature.library.compose.components

import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.kelsos.mbrc.core.common.utilities.coroutines.AppCoroutineDispatchers
import com.kelsos.mbrc.core.common.data.ConnectionSettings
import com.kelsos.mbrc.core.networking.DefaultConnectionProvider
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/** Displays MusicBee's local artist picture, falling back to the person icon. */
@Composable
fun ArtistPicture(artist: String, modifier: Modifier = Modifier, size: Dp = 48.dp) {
  val connectionProvider: DefaultConnectionProvider = koinInject()
  val dispatchers: AppCoroutineDispatchers = koinInject()
  val connection by produceState<ConnectionSettings?>(
    initialValue = null,
    key1 = connectionProvider,
    key2 = dispatchers
  ) {
    value = withContext(dispatchers.database) { connectionProvider.getDefault() }
  }
  val currentConnection = connection
  val uri = remember(artist, currentConnection) {
    if (artist.isBlank() || currentConnection == null || currentConnection.port >= MAX_PORT) {
      null
    } else {
      val encodedArtist = Base64.encodeToString(
        artist.toByteArray(StandardCharsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
      )
      val host = if (currentConnection.address.contains(':') && !currentConnection.address.startsWith("[")) {
        "[${currentConnection.address}]"
      } else {
        currentConnection.address
      }
      Uri.Builder()
        .scheme("http")
        .encodedAuthority("$host:${currentConnection.port + 1}")
        .appendPath("artist")
        .appendPath(encodedArtist)
        .build()
    }
  }

  if (uri == null) {
    ArtistPictureFallback(modifier = modifier.size(size))
    return
  }

  val context = LocalContext.current
  val request = remember(context, uri) {
    ImageRequest.Builder(context)
      .data(uri)
      .memoryCachePolicy(CachePolicy.ENABLED)
      .diskCachePolicy(CachePolicy.ENABLED)
      .build()
  }
  SubcomposeAsyncImage(
    model = request,
    contentDescription = null,
    contentScale = ContentScale.Crop,
    loading = { ArtistPictureFallback() },
    error = { ArtistPictureFallback() },
    modifier = modifier.size(size).clip(CircleShape)
  )
}

@Composable
private fun ArtistPictureFallback(modifier: Modifier = Modifier) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .fillMaxSize()
      .clip(CircleShape)
      .background(MaterialTheme.colorScheme.surfaceVariant)
  ) {
    Icon(
      imageVector = Icons.Default.Person,
      contentDescription = null,
      modifier = Modifier.fillMaxSize(0.58f),
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

private const val MAX_PORT = 65535
