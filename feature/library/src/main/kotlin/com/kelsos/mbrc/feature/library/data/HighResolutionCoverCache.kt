package com.kelsos.mbrc.feature.library.data

import android.app.Application
import android.net.Uri
import android.util.Base64
import com.kelsos.mbrc.core.common.data.ConnectionSettings
import com.kelsos.mbrc.core.common.utilities.coroutines.AppCoroutineDispatchers
import com.kelsos.mbrc.core.data.library.track.TrackQuery
import com.kelsos.mbrc.core.data.library.track.TrackRepository
import com.kelsos.mbrc.core.networking.DefaultConnectionProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Downloads one original artwork file per album and shares it across library screens and playback. */
class HighResolutionCoverCache(
  app: Application,
  private val connectionProvider: DefaultConnectionProvider,
  private val dispatchers: AppCoroutineDispatchers
) {
  // Versioned so files produced by the earlier thumbnail endpoint cannot be
  // mistaken for original artwork after an app upgrade.
  private val directory = File(app.cacheDir, DIRECTORY).apply { mkdirs() }
  private val locks = ConcurrentHashMap<String, Mutex>()
  private val representativePaths = ConcurrentHashMap<String, String>()
  private val downloadPermits = Semaphore(MAX_CONCURRENT_DOWNLOADS)

  suspend fun load(
    album: String,
    artist: String,
    albumKey: String,
    trackRepository: TrackRepository
  ): File? {
    val path = representativePaths[albumKey] ?: withContext(dispatchers.database) {
      trackRepository.getTrackPaths(TrackQuery.Album(album, artist)).firstOrNull()
    }?.also { representativePaths[albumKey] = it } ?: return null
    return load(path, albumKey)
  }

  suspend fun load(trackPath: String, albumKey: String): File? = withContext(dispatchers.io) {
    val file = fileFor(albumKey)
    if (file.isFile && file.length() > 0L) return@withContext file
    val lock = locks.getOrPut(albumKey) { Mutex() }
    lock.withLock {
      if (file.isFile && file.length() > 0L) return@withLock file

      downloadPermits.withPermit {
        val connection = connectionProvider.getDefault()
        if (connection == null || connection.port >= MAX_PORT) {
          return@withPermit null
        }

        runCatching {
          val uri = streamUri(connection, trackPath)
          val http = URL(uri.toString()).openConnection() as HttpURLConnection
          try {
            http.requestMethod = "GET"
            http.connectTimeout = CONNECT_TIMEOUT_MS
            http.readTimeout = READ_TIMEOUT_MS
            http.useCaches = false
            if (http.responseCode !in 200..299) return@runCatching null

            val temporary = File(directory, "$albumKey.tmp")
            http.inputStream.use { input ->
              temporary.outputStream().use { output -> input.copyTo(output) }
            }
            if (temporary.length() <= 0L || !temporary.renameTo(file)) {
              temporary.delete()
              null
            } else {
              file
            }
          } finally {
            http.disconnect()
          }
        }.onFailure { Timber.v(it, "Failed to cache high-resolution album artwork") }
          .getOrNull()
      }
    }
  }

  private fun fileFor(albumKey: String): File = File(directory, albumKey)

  private fun streamUri(connection: ConnectionSettings, path: String): Uri {
    val encodedPath = Base64.encodeToString(
      path.toByteArray(StandardCharsets.UTF_8),
      Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )
    val host = if (connection.address.contains(':') && !connection.address.startsWith("[")) {
      "[${connection.address}]"
    } else {
      connection.address
    }
    return Uri.Builder().scheme("http")
      .encodedAuthority("$host:${connection.port + 1}")
      .appendPath("cover").appendPath(encodedPath).build()
  }

  companion object {
    private const val DIRECTORY = "covers_hd_v2"
    private const val MAX_PORT = 65535
    private const val CONNECT_TIMEOUT_MS = 3_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val MAX_CONCURRENT_DOWNLOADS = 3
  }
}
