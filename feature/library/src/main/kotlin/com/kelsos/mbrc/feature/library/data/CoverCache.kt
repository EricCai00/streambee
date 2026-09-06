package com.kelsos.mbrc.feature.library.data

import android.app.Application
import com.kelsos.mbrc.core.common.data.Progress
import com.kelsos.mbrc.core.common.utilities.coroutines.AppCoroutineDispatchers
import com.kelsos.mbrc.core.data.library.album.AlbumCover
import com.kelsos.mbrc.core.data.library.album.AlbumRepository
import com.kelsos.mbrc.core.data.library.track.TrackRepository
import com.kelsos.mbrc.core.networking.ApiStatus
import com.kelsos.mbrc.core.networking.api.LibraryApi
import com.kelsos.mbrc.core.networking.dto.AlbumCoverDto
import com.kelsos.mbrc.core.networking.dto.CoverDto
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.decodeBase64
import okio.buffer
import okio.sink
import timber.log.Timber

class CoverCache(
  private val albumRepository: AlbumRepository,
  private val libraryApi: LibraryApi,
  private val trackRepository: TrackRepository,
  private val highResolutionCoverCache: HighResolutionCoverCache,
  private val dispatchers: AppCoroutineDispatchers,
  app: Application
) {
  private val cache = File(app.cacheDir, "covers")
  private val locks = ConcurrentHashMap<String, Mutex>()
  private val downloadPermits = Semaphore(MAX_CONCURRENT_DOWNLOADS)

  init {
    if (!cache.exists()) {
      val isSuccess = cache.mkdir()
      Timber.v("Cover cache directory ${cache.absolutePath} created: $isSuccess")
    }
  }

  suspend fun cache(progress: Progress? = null) {
    val covers = getCachedCovers()
    fetchCovers(covers, progress)
  }

  /** Fetches one thumbnail when its list item becomes visible. */
  suspend fun load(artist: String, album: String, key: String): File? {
    val file = File(cache, key)
    if (file.isFile && file.length() > 0L) return file

    return locks.getOrPut(key) { Mutex() }.withLock {
      if (file.isFile && file.length() > 0L) return@withLock file
      downloadPermits.withPermit {
        val payload = AlbumCoverDto(artist, album, null)
        val response = loadResponse(payload)
        if (response != null) {
          val cover = response.cover
          val hash = response.hash
          if (!cover.isNullOrEmpty() && !hash.isNullOrEmpty()) {
            val updated = mutableListOf<AlbumCover>()
            val result = withContext(dispatchers.io) {
              cacheAlbumCover(
                payload = AlbumCover(artist, album, null),
                cover = cover,
                updated = updated,
                hash = hash
              )
            }
            if (result.isSuccess) {
              withContext(dispatchers.database) { albumRepository.updateCovers(updated) }
              file.takeIf { it.isFile && it.length() > 0L }?.let { return@withPermit it }
            } else {
              Timber.v(result.exceptionOrNull(), "Failed to cache album thumbnail")
            }
          }
        }

        // Some MusicBee/plugin versions can serve the artwork through the
        // track-path endpoint used by the album detail screen while the
        // album-name endpoint returns 404. Reuse that already reliable cache
        // as a fallback, then copy the result into the thumbnail cache so
        // subsequent list visits are local and immediate.
        fallbackToTrackArtwork(artist, album, key, file)
      }
    }
  }

  private suspend fun fallbackToTrackArtwork(
    artist: String,
    album: String,
    key: String,
    destination: File
  ): File? {
    val original = try {
      highResolutionCoverCache.load(album, artist, key, trackRepository)
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      Timber.v(error, "Failed to load album artwork through a track path for $artist - $album")
      null
    } ?: return null

    return withContext(dispatchers.io) {
      if (!original.isFile || original.length() <= 0L) return@withContext null
      if (destination.isFile && destination.length() > 0L) return@withContext destination
      if (destination.exists()) destination.delete()

      val temporary = File(cache, "$key.tmp")
      try {
        original.inputStream().use { input ->
          temporary.outputStream().use { output -> input.copyTo(output) }
        }
        if (temporary.length() <= 0L) {
          temporary.delete()
          null
        } else if (temporary.renameTo(destination)) {
          Timber.v("Saved track-path artwork as album thumbnail for $artist - $album")
          destination
        } else {
          temporary.delete()
          destination.takeIf { it.isFile && it.length() > 0L }
        }
      } catch (error: CancellationException) {
        throw error
      } catch (error: Exception) {
        temporary.delete()
        Timber.v(error, "Failed to copy track-path artwork into the thumbnail cache")
        null
      }
    }
  }

  /**
   * Loads a single cover and retries transport/protocol failures. A response that explicitly
   * reports no cover is not retried because it is a valid MusicBee result.
   */
  private suspend fun loadResponse(payload: AlbumCoverDto): CoverDto? {
    for (attempt in 1..MAX_LOAD_ATTEMPTS) {
      try {
        val response = libraryApi.getCover(payload)
        if (
          response.status == ApiStatus.SUCCESS &&
          !response.cover.isNullOrEmpty() &&
          !response.hash.isNullOrEmpty()
        ) {
          return response
        } else if (response.status == STATUS_BAD_REQUEST ||
          response.status == STATUS_NOT_FOUND ||
          response.status == ApiStatus.NOT_MODIFIED
        ) {
          Timber.v("MusicBee returned no thumbnail for $payload (status=${response.status})")
          return null
        } else {
          Timber.v(
            "MusicBee returned a retryable thumbnail status for $payload " +
              "(status=${response.status}, attempt $attempt/$MAX_LOAD_ATTEMPTS)"
          )
        }
      } catch (error: CancellationException) {
        throw error
      } catch (error: Exception) {
        Timber.w(
          error,
          "Failed to load album thumbnail for $payload (attempt $attempt/$MAX_LOAD_ATTEMPTS)"
        )
      }

      if (attempt < MAX_LOAD_ATTEMPTS) {
        delay(RETRY_DELAY_MS * attempt)
      }
    }
    return null
  }

  private suspend fun getCachedCovers(): List<AlbumCover> = withContext(dispatchers.database) {
    val covers = albumRepository.getCovers()
    val albumCovers = mutableListOf<AlbumCover>()
    withContext(dispatchers.io) {
      val files = cache.listFiles()?.mapTo(HashSet()) { it.nameWithoutExtension } ?: emptySet()

      for (cover in covers) {
        if (cover.hash.isNullOrEmpty() || cover.key() in files) {
          albumCovers.add(cover)
        } else {
          albumCovers.add(cover.copy(hash = null))
        }
      }
    }
    albumCovers
  }

  private suspend fun fetchCovers(covers: List<AlbumCover>, progress: Progress?) {
    withContext(dispatchers.network) {
      val updated = mutableListOf<AlbumCover>()
      val coverDtos = covers.map { it.toDto() }
      libraryApi
        .getCovers(coverDtos, progress)
        .onCompletion {
          finalizeUpdate(updated)
        }.collect { (payload, response) ->
          processApiCoverResponse(response, payload.toAlbumCover(), updated)
        }
    }
  }

  private fun processApiCoverResponse(
    response: CoverDto,
    payload: AlbumCover,
    updated: MutableList<AlbumCover>
  ) {
    val status = response.status
    if (status == ApiStatus.NOT_MODIFIED) {
      Timber.v("cover for $payload did not change")
      return
    }

    val cover = response.cover
    val hash = response.hash

    if (status == ApiStatus.SUCCESS && !cover.isNullOrEmpty() && !hash.isNullOrEmpty()) {
      val result = cacheAlbumCover(payload, cover, updated, hash)

      if (result.isFailure) {
        Timber.e(result.exceptionOrNull(), "Could not save cover for $payload")
      }
    }
  }

  private suspend fun finalizeUpdate(updated: List<AlbumCover>) {
    Timber.v("Updated covers for ${updated.size} albums")
    withContext(dispatchers.database) {
      albumRepository.updateCovers(updated)
    }
    val storedCovers = albumRepository.getCovers().mapTo(HashSet()) { it.key() }
    val coverFiles = cache.listFiles()
    if (coverFiles != null) {
      val notInDb = coverFiles.filter { it.nameWithoutExtension !in storedCovers }
      Timber.v("deleting ${notInDb.size} covers no longer in db")
      for (file in notInDb) {
        runCatching { file.delete() }
      }
    }
  }

  private fun cacheAlbumCover(
    payload: AlbumCover,
    cover: String,
    updated: MutableList<AlbumCover>,
    hash: String
  ): Result<Boolean> = runCatching {
    val file = File(cache, payload.key())
    val decodeBase64 = requireNotNull(cover.decodeBase64()) { "invalid base64 artwork" }
    Timber.v("saving cover for $payload -> ${file.path}")
    file.sink().buffer().use { sink -> sink.write(decodeBase64) }
    updated.add(payload.copy(hash = hash))
  }

  private fun AlbumCover.toDto(): AlbumCoverDto = AlbumCoverDto(artist, album, hash)

  private fun AlbumCoverDto.toAlbumCover(): AlbumCover = AlbumCover(artist, album, hash)

  companion object {
    private const val MAX_CONCURRENT_DOWNLOADS = 3
    private const val MAX_LOAD_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 250L
    private const val STATUS_BAD_REQUEST = 400
    private const val STATUS_NOT_FOUND = 404
  }
}
