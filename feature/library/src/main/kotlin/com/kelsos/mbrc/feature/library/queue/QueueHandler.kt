package com.kelsos.mbrc.feature.library.queue

import com.kelsos.mbrc.core.common.settings.LibrarySettings
import com.kelsos.mbrc.core.common.utilities.AppError
import com.kelsos.mbrc.core.common.utilities.Outcome
import com.kelsos.mbrc.core.common.utilities.asFailure
import com.kelsos.mbrc.core.common.utilities.asSuccess
import com.kelsos.mbrc.core.common.utilities.coroutines.AppCoroutineDispatchers
import com.kelsos.mbrc.core.data.library.track.Track
import com.kelsos.mbrc.core.data.library.track.TrackQuery
import com.kelsos.mbrc.core.data.library.track.TrackRepository
import com.kelsos.mbrc.core.networking.api.QueueApi
import com.kelsos.mbrc.core.queue.PathQueueUseCase
import com.kelsos.mbrc.core.queue.Queue
import com.kelsos.mbrc.feature.library.playback.DevicePlaybackController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Maps library queue actions to the on-device player. */
class QueueHandler(
  private val settings: LibrarySettings,
  private val trackRepository: TrackRepository,
  @Suppress("UNUSED_PARAMETER") private val queueApi: QueueApi,
  private val devicePlaybackController: DevicePlaybackController,
  private val dispatchers: AppCoroutineDispatchers
) : PathQueueUseCase {
  suspend fun queueAlbum(type: Queue, album: String, artist: String): Outcome<Int> =
    queueQuery(type, TrackQuery.Album(album, artist))

  suspend fun queueArtist(type: Queue, artist: String): Outcome<Int> =
    queueQuery(type, TrackQuery.Artist(artist))

  suspend fun queueGenre(type: Queue, genre: String): Outcome<Int> =
    queueQuery(type, TrackQuery.Genre(genre))

  private suspend fun queueQuery(type: Queue, query: TrackQuery): Outcome<Int> = try {
    val tracks = withContext(dispatchers.database) {
      trackRepository.getTrackPaths(query).mapNotNull { trackRepository.getByPath(it) }
    }
    playOrQueue(type, tracks)
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    Timber.e(e, "Local queue query failed")
    AppError.OperationFailed.asFailure()
  }

  override suspend fun queuePath(path: String): Outcome<Int> = try {
    val track = withContext(dispatchers.database) { trackRepository.getByPath(path) }
      ?: return AppError.OperationFailed.asFailure()
    playOrQueue(Queue.Now, listOf(track))
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    Timber.e(e, "Local path playback failed")
    AppError.OperationFailed.asFailure()
  }

  override suspend fun queuePaths(paths: List<String>): Outcome<Int> = try {
    val tracks = withContext(dispatchers.database) {
      paths.mapNotNull { path -> trackRepository.getByPath(path) }
    }
    playOrQueue(Queue.Now, tracks)
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    Timber.e(e, "Local playlist playback failed")
    AppError.OperationFailed.asFailure()
  }

  override suspend fun queuePaths(paths: List<String>, startIndex: Int): Outcome<Int> = try {
    val tracks = withContext(dispatchers.database) {
      paths.mapNotNull { path -> trackRepository.getByPath(path) }
    }
    if (tracks.isEmpty()) return AppError.OperationFailed.asFailure()
    playTracks(tracks, startIndex.coerceIn(0, tracks.lastIndex))
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    Timber.e(e, "Local playlist playback from selected track failed")
    AppError.OperationFailed.asFailure()
  }

  suspend fun queueTrack(track: Track, type: Queue, queueAlbum: Boolean = false): Outcome<Int> = try {
    val tracks = when (type) {
      Queue.AddAll -> withContext(dispatchers.database) {
        val paths = if (queueAlbum) {
          trackRepository.getTrackPaths(TrackQuery.Album(track.album, track.albumArtist))
        } else trackRepository.getTrackPaths(TrackQuery.All)
        paths.mapNotNull { trackRepository.getByPath(it) }
      }
      Queue.PlayAlbum -> withContext(dispatchers.database) {
        trackRepository.getTrackPaths(TrackQuery.Album(track.album, track.albumArtist))
          .mapNotNull { trackRepository.getByPath(it) }
      }
      Queue.PlayArtist -> withContext(dispatchers.database) {
        trackRepository.getTrackPaths(TrackQuery.Artist(track.artist))
          .mapNotNull { trackRepository.getByPath(it) }
      }
      else -> listOf(track)
    }
    val start = tracks.indexOfFirst { it.src == track.src }.coerceAtLeast(0)
    when (type) {
      Queue.AddAll, Queue.PlayAlbum, Queue.PlayArtist -> playTracks(tracks, start)
      else -> playOrQueue(type, tracks)
    }
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    Timber.e(e, "Local track queue failed")
    AppError.OperationFailed.asFailure()
  }

  suspend fun queueTrack(track: Track, queueAlbum: Boolean = false): Outcome<Int> =
    queueTrack(track, Queue.fromTrackAction(settings.libraryTrackDefaultActionFlow.first()), queueAlbum)

  private suspend fun playOrQueue(type: Queue, tracks: List<Track>): Outcome<Int> {
    if (tracks.isEmpty()) return AppError.OperationFailed.asFailure()
    return when (type) {
      Queue.Next -> enqueue(tracks, next = true)
      Queue.Last -> enqueue(tracks, next = false)
      else -> playTracks(tracks, 0)
    }
  }

  private suspend fun playTracks(tracks: List<Track>, start: Int): Outcome<Int> =
    if (devicePlaybackController.playTracks(tracks, start)) tracks.size.asSuccess()
    else AppError.OperationFailed.asFailure()

  private suspend fun enqueue(tracks: List<Track>, next: Boolean): Outcome<Int> {
    val success = if (next) devicePlaybackController.enqueueNext(tracks)
    else devicePlaybackController.enqueueLast(tracks)
    return if (success) tracks.size.asSuccess() else AppError.OperationFailed.asFailure()
  }
}
