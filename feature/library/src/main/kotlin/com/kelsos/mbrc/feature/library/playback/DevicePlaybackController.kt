package com.kelsos.mbrc.feature.library.playback

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.AtomicFile
import android.util.Base64
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.kelsos.mbrc.core.common.playback.LocalPlaybackController
import com.kelsos.mbrc.core.common.playback.LocalQueueTrack
import com.kelsos.mbrc.core.common.settings.PlaybackSettings
import com.kelsos.mbrc.core.common.state.AppStatePublisher
import com.kelsos.mbrc.core.common.state.BasicTrackInfo
import com.kelsos.mbrc.core.common.state.PlayerState
import com.kelsos.mbrc.core.common.state.PlayingPosition
import com.kelsos.mbrc.core.common.state.Repeat
import com.kelsos.mbrc.core.common.state.ShuffleMode
import com.kelsos.mbrc.core.common.state.TrackDetails
import com.kelsos.mbrc.core.common.utilities.coroutines.AppCoroutineDispatchers
import com.kelsos.mbrc.core.data.history.PlaybackHistoryDao
import com.kelsos.mbrc.core.data.history.PlaybackHistoryEntry
import com.kelsos.mbrc.core.data.library.track.Track
import com.kelsos.mbrc.core.networking.DefaultConnectionProvider
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import timber.log.Timber

/** Local playback source of truth for MusicBee library and playlist tracks. */
class DevicePlaybackController(
  context: Context,
  private val connectionProvider: DefaultConnectionProvider,
  private val appState: AppStatePublisher,
  private val dispatchers: AppCoroutineDispatchers,
  private val playbackSettings: PlaybackSettings,
  private val playbackHistoryDao: PlaybackHistoryDao
) : LocalPlaybackController {
  private val appContext = context.applicationContext
  private val scope = CoroutineScope(Job() + dispatchers.main)
  private val playbackRequestId = AtomicLong()
  private val persistenceGeneration = AtomicLong()
  private val queueStateFile = File(appContext.filesDir, QUEUE_STATE_FILE)
  private val atomicQueueFile = AtomicFile(queueStateFile)
  private val queueFileMutex = Mutex()
  private val statePreferences =
    appContext.getSharedPreferences(PLAYBACK_STATE_PREFS, Context.MODE_PRIVATE)
  private val _queue = MutableStateFlow<List<LocalQueueTrack>>(emptyList())
  private val completionTracker = PlaybackCompletionTracker(
    onQualified = ::recordQualifiedPlayback
  )

  private var muted = false
  private var volumeBeforeMute = 1f
  private var publishedTrackPath: String? = null
  private var lyricsJob: Job? = null
  private var favoriteJob: Job? = null
  private var queueFillJob: Job? = null
  private var queuePersistenceJob: Job? = null
  private var logicalQueue: List<Track> = emptyList()
  private var loadedStartIndex = 0
  private var loadedEndIndexExclusive = 0
  private var activeQueueGeneration = 0L
  private var queueRevision = 0L
  private var restoring = true
  private var appScrobblingEnabled = false

  @Volatile
  private var localPlaybackActive = false

  override val queue = _queue.asStateFlow()

  val player: ExoPlayer = ExoPlayer.Builder(appContext).build().apply {
    setAudioAttributes(
      AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build(),
      true
    )
    setHandleAudioBecomingNoisy(true)
    addListener(
      object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
          completionTracker.transition(
            track = mediaItem?.localConfiguration?.tag as? Track,
            durationMs = player.playableDuration(),
            isPlaying = player.isPlaying
          )
        }

        override fun onEvents(player: Player, events: Player.Events) {
          if (
            events.containsAny(
              Player.EVENT_PLAYBACK_STATE_CHANGED,
              Player.EVENT_IS_PLAYING_CHANGED,
              Player.EVENT_MEDIA_ITEM_TRANSITION,
              Player.EVENT_POSITION_DISCONTINUITY
            )
          ) {
            sampleAppPlayback(player)
          }
          if (
            events.containsAny(
              Player.EVENT_PLAYBACK_STATE_CHANGED,
              Player.EVENT_IS_PLAYING_CHANGED,
              Player.EVENT_MEDIA_ITEM_TRANSITION,
              Player.EVENT_POSITION_DISCONTINUITY,
              Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
              Player.EVENT_REPEAT_MODE_CHANGED,
              Player.EVENT_VOLUME_CHANGED
            )
          ) {
            publishState()
          }
          if (
            events.containsAny(
              Player.EVENT_PLAYBACK_STATE_CHANGED,
              Player.EVENT_IS_PLAYING_CHANGED,
              Player.EVENT_MEDIA_ITEM_TRANSITION,
              Player.EVENT_POSITION_DISCONTINUITY,
              Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
              Player.EVENT_REPEAT_MODE_CHANGED
            )
          ) {
            savePlaybackState()
          }
        }
      }
    )
  }

  val mediaSession: MediaSession = MediaSession.Builder(appContext, player)
    .setId(SESSION_ID)
    .build()

  override val hasLocalPlayback: Boolean
    get() = localPlaybackActive

  init {
    scope.launch {
      playbackSettings.appScrobblingEnabledFlow.collect { enabled ->
        appScrobblingEnabled = enabled
      }
    }
    scope.launch {
      restoreState()
      restoring = false
      publishState()
    }
    scope.launch {
      while (isActive) {
        delay(POSITION_UPDATE_MS)
        sampleAppPlayback(player)
        publishState()
      }
    }
    scope.launch {
      while (isActive) {
        delay(PLAYBACK_STATE_SAVE_MS)
        savePlaybackState()
      }
    }
  }

  suspend fun play(track: Track): Boolean = playTracks(listOf(track), 0)

  suspend fun playTracks(tracks: List<Track>, startIndex: Int = 0): Boolean = startQueue(
    tracks = tracks,
    startIndex = startIndex,
    positionMs = 0L,
    playWhenReady = true,
    checkStream = true,
    persistQueue = true
  )

  suspend fun enqueueNext(tracks: List<Track>): Boolean = addTracks(tracks, next = true)

  suspend fun enqueueLast(tracks: List<Track>): Boolean = addTracks(tracks, next = false)

  override fun playPause() = onMain {
    if (player.isPlaying) player.pause() else player.play()
  }

  override fun play() = onMain { player.play() }

  override fun pause() = onMain { player.pause() }

  override fun previous() = onMain {
    if (player.hasPreviousMediaItem()) {
      player.seekToPreviousMediaItem()
    } else {
      playLogicalQueueItem(currentLogicalIndex() - 1)
    }
  }

  override fun next() = onMain {
    if (player.hasNextMediaItem()) {
      player.seekToNextMediaItem()
    } else {
      playLogicalQueueItem(currentLogicalIndex() + 1)
    }
  }

  override fun playQueueItem(index: Int) = onMain { playLogicalQueueItem(index) }

  override fun removeQueueItem(index: Int) = onMain {
    if (index !in logicalQueue.indices) return@onMain
    if (!isQueueFullyLoaded()) {
      restartAfterRemove(index)
      return@onMain
    }

    val previousQueue = logicalQueue
    val updatedQueue = previousQueue.toMutableList().apply { removeAt(index) }
    logicalQueue = updatedQueue
    loadedEndIndexExclusive = updatedQueue.size
    runCatching { player.removeMediaItem(index) }
      .onFailure {
        logicalQueue = previousQueue
        loadedEndIndexExclusive = previousQueue.size
        Timber.e(it, "Failed to remove local queue item")
      }
      .onSuccess {
        localPlaybackActive = updatedQueue.isNotEmpty()
        notifyQueueChanged(updatedQueue, persistQueue = true)
        publishState()
      }
  }

  override fun moveQueueItem(from: Int, to: Int) = onMain {
    if (from !in logicalQueue.indices || to !in logicalQueue.indices || from == to) return@onMain
    if (!isQueueFullyLoaded()) {
      restartAfterMove(from, to)
      return@onMain
    }

    val previousQueue = logicalQueue
    val updatedQueue = previousQueue.toMutableList().apply { add(to, removeAt(from)) }
    logicalQueue = updatedQueue
    runCatching { player.moveMediaItem(from, to) }
      .onFailure {
        logicalQueue = previousQueue
        Timber.e(it, "Failed to move local queue item")
      }
      .onSuccess { notifyQueueChanged(updatedQueue, persistQueue = true) }
  }

  override fun stop() = onMain {
    invalidatePlaybackWork()
    player.stop()
    player.clearMediaItems()
    logicalQueue = emptyList()
    loadedStartIndex = 0
    loadedEndIndexExclusive = 0
    localPlaybackActive = false
    publishedTrackPath = null
    lyricsJob?.cancel()
    favoriteJob?.cancel()
    appState.updateLyrics(emptyList())
    appState.updateTrackRating(com.kelsos.mbrc.core.common.state.TrackRating())
    appState.updateTrackDetails(TrackDetails.EMPTY)
    queueRevision += 1
    _queue.value = emptyList()
    statePreferences.edit().clear().apply()
    deletePersistedQueue()
    publishState()
  }

  override fun seekTo(positionMs: Long) = onMain {
    player.seekTo(positionMs.coerceAtLeast(0L))
  }

  override fun setVolume(percent: Int) = onMain {
    val value = percent.coerceIn(0, VOLUME_MAX_PERCENT) / VOLUME_MAX_PERCENT.toFloat()
    player.volume = value
    if (value > 0f) {
      muted = false
      volumeBeforeMute = value
    }
    publishState()
  }

  override fun adjustVolume(deltaPercent: Int) = onMain {
    setVolume(
      ((player.volume * VOLUME_MAX_PERCENT).toInt() + deltaPercent)
        .coerceIn(0, VOLUME_MAX_PERCENT)
    )
  }

  override fun toggleMute() = onMain {
    if (muted || player.volume == 0f) {
      player.volume = volumeBeforeMute.coerceIn(MIN_AUDIBLE_VOLUME, 1f)
      muted = false
    } else {
      volumeBeforeMute = player.volume
      player.volume = 0f
      muted = true
    }
    publishState()
  }

  override fun toggleShuffle() = onMain {
    player.shuffleModeEnabled = !player.shuffleModeEnabled
    publishState()
  }

  override fun toggleRepeat() = onMain {
    player.repeatMode = when (player.repeatMode) {
      Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
      Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
      else -> Player.REPEAT_MODE_OFF
    }
    publishState()
  }

  override fun setFavorite(loved: Boolean) {
    onMain {
      val track = player.currentMediaItem?.localConfiguration?.tag as? Track ?: return@onMain
      favoriteJob?.cancel()
      favoriteJob = scope.launch {
        if (setFavoriteForPath(track.src, loved) && publishedTrackPath == track.src) {
          appState.updateTrackRating(
            com.kelsos.mbrc.core.common.state.TrackRating(
              lfmRating = if (loved) {
                com.kelsos.mbrc.core.common.state.LfmRating.Loved
              } else {
                com.kelsos.mbrc.core.common.state.LfmRating.Normal
              }
            )
          )
        }
      }
    }
  }

  private suspend fun startQueue(
    tracks: List<Track>,
    startIndex: Int,
    positionMs: Long,
    playWhenReady: Boolean,
    checkStream: Boolean,
    persistQueue: Boolean
  ): Boolean {
    if (tracks.isEmpty()) return false
    val startedAt = SystemClock.elapsedRealtime()
    val requestId = playbackRequestId.incrementAndGet()
    val queueSnapshot = withContext(dispatchers.io) { tracks.toList() }
    val selected = startIndex.coerceIn(queueSnapshot.indices)
    val connection = withContext(dispatchers.io) { connectionProvider.getDefault() } ?: return false
    if (connection.port >= MAX_PORT || playbackRequestId.get() != requestId) return false
    val selectedItem = withContext(dispatchers.io) {
      createMediaItem(queueSnapshot[selected], connection.address, connection.port)
    }
    if (checkStream && !isStreamAvailable(selectedItem.localConfiguration?.uri)) {
      Timber.w("MusicBee audio stream is not reachable")
      return false
    }
    if (playbackRequestId.get() != requestId) return false

    return withContext(dispatchers.main) {
      if (playbackRequestId.get() != requestId) return@withContext false
      runCatching {
        queueFillJob?.cancel()
        val queueGeneration = ++activeQueueGeneration
        logicalQueue = queueSnapshot
        loadedStartIndex = selected
        loadedEndIndexExclusive = selected + 1
        player.setMediaItems(listOf(selectedItem), 0, positionMs.coerceAtLeast(0L))
        player.playWhenReady = playWhenReady
        localPlaybackActive = true
        notifyQueueChanged(queueSnapshot, persistQueue)
        player.prepare()
        publishState()
        savePlaybackState()
        Timber.i(
          "Started selected local queue item %d/%d in %d ms",
          selected + 1,
          queueSnapshot.size,
          SystemClock.elapsedRealtime() - startedAt
        )
        startQueueFill(
          queueSnapshot = queueSnapshot,
          selectedIndex = selected,
          address = connection.address,
          commandPort = connection.port,
          queueGeneration = queueGeneration
        )
        true
      }.onFailure { Timber.e(it, "Failed to start local playback") }.getOrDefault(false)
    }
  }

  private fun startQueueFill(
    queueSnapshot: List<Track>,
    selectedIndex: Int,
    address: String,
    commandPort: Int,
    queueGeneration: Long
  ) {
    val chunks = planQueueChunks(queueSnapshot.size, selectedIndex, QUEUE_CHUNK_SIZE)
    if (chunks.isEmpty()) {
      loadedStartIndex = 0
      loadedEndIndexExclusive = queueSnapshot.size
      queueFillJob = null
      return
    }

    queueFillJob = scope.launch {
      try {
        chunks.forEach { chunk ->
          val items = createItems(
            queueSnapshot.subList(chunk.fromIndex, chunk.toIndexExclusive),
            address,
            commandPort
          )
          if (activeQueueGeneration != queueGeneration) return@launch
          if (chunk.prepend) {
            val previousStart = loadedStartIndex
            loadedStartIndex = chunk.fromIndex
            try {
              player.addMediaItems(0, items)
            } catch (error: Exception) {
              loadedStartIndex = previousStart
              throw error
            }
          } else {
            val previousEnd = loadedEndIndexExclusive
            loadedEndIndexExclusive = chunk.toIndexExclusive
            try {
              player.addMediaItems(items)
            } catch (error: Exception) {
              loadedEndIndexExclusive = previousEnd
              throw error
            }
          }
          yield()
        }
      } catch (error: CancellationException) {
        throw error
      } catch (error: Exception) {
        Timber.e(error, "Failed while filling local playback queue")
      } finally {
        if (activeQueueGeneration == queueGeneration) {
          queueFillJob = null
          savePlaybackState()
        }
      }
    }
  }

  private suspend fun addTracks(tracks: List<Track>, next: Boolean): Boolean {
    if (tracks.isEmpty()) return false
    withContext(dispatchers.main) { queueFillJob }?.join()
    val requestId = playbackRequestId.get()
    val connection = withContext(dispatchers.io) { connectionProvider.getDefault() } ?: return false
    if (connection.port >= MAX_PORT || playbackRequestId.get() != requestId) return false
    val items = createItems(tracks, connection.address, connection.port)
    return withContext(dispatchers.main) {
      if (playbackRequestId.get() != requestId) return@withContext false
      runCatching {
        val insertionIndex = if (next) {
          (currentLogicalIndex() + 1).coerceIn(0, logicalQueue.size)
        } else {
          logicalQueue.size
        }
        val previousQueue = logicalQueue
        val previousStart = loadedStartIndex
        val previousEnd = loadedEndIndexExclusive
        val updatedQueue = previousQueue.toMutableList().apply {
          addAll(insertionIndex, tracks)
        }
        logicalQueue = updatedQueue
        loadedStartIndex = 0
        loadedEndIndexExclusive = updatedQueue.size
        try {
          player.addMediaItems(insertionIndex, items)
        } catch (error: Exception) {
          logicalQueue = previousQueue
          loadedStartIndex = previousStart
          loadedEndIndexExclusive = previousEnd
          throw error
        }
        localPlaybackActive = player.mediaItemCount > 0
        notifyQueueChanged(updatedQueue, persistQueue = true)
        true
      }.onFailure { Timber.e(it, "Failed to enqueue local tracks") }.getOrDefault(false)
    }
  }

  private suspend fun createItems(
    tracks: List<Track>,
    address: String,
    commandPort: Int
  ): List<MediaItem> = withContext(dispatchers.io) {
    tracks.map { track -> createMediaItem(track, address, commandPort) }
  }

  private fun createMediaItem(track: Track, address: String, commandPort: Int): MediaItem =
    MediaItem.Builder()
      .setMediaId(track.src)
      .setUri(streamUri(address, commandPort, "audio", track.src))
      .setTag(track)
      .setMediaMetadata(
        MediaMetadata.Builder()
          .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
          .setTitle(track.title)
          .setArtist(track.artist)
          .setAlbumTitle(track.album)
          .setAlbumArtist(track.albumArtist)
          .setArtworkUri(streamUri(address, commandPort, "cover", track.src))
          .build()
      )
      .build()

  private fun playLogicalQueueItem(index: Int) {
    if (index !in logicalQueue.indices) return
    val playerIndex = index - loadedStartIndex
    if (index in loadedStartIndex until loadedEndIndexExclusive &&
      playerIndex in 0 until player.mediaItemCount
    ) {
      player.seekTo(playerIndex, 0L)
      player.play()
      return
    }

    val snapshot = logicalQueue
    invalidatePlaybackWork()
    scope.launch {
      startQueue(
        tracks = snapshot,
        startIndex = index,
        positionMs = 0L,
        playWhenReady = true,
        checkStream = false,
        persistQueue = false
      )
    }
  }

  private fun restartAfterRemove(index: Int) {
    val currentIndex = currentLogicalIndex()
    val currentPosition = player.currentPosition.coerceAtLeast(0L)
    val playWhenReady = player.playWhenReady
    val updatedQueue = logicalQueue.toMutableList().apply { removeAt(index) }
    if (updatedQueue.isEmpty()) {
      stop()
      return
    }
    val selected = when {
      index < currentIndex -> currentIndex - 1
      index == currentIndex -> index.coerceAtMost(updatedQueue.lastIndex)
      else -> currentIndex
    }.coerceIn(updatedQueue.indices)
    val restoredPosition = if (index == currentIndex) 0L else currentPosition
    invalidatePlaybackWork()
    scope.launch {
      startQueue(
        tracks = updatedQueue,
        startIndex = selected,
        positionMs = restoredPosition,
        playWhenReady = playWhenReady,
        checkStream = false,
        persistQueue = true
      )
    }
  }

  private fun restartAfterMove(from: Int, to: Int) {
    val currentIndex = currentLogicalIndex()
    val currentPosition = player.currentPosition.coerceAtLeast(0L)
    val playWhenReady = player.playWhenReady
    val updatedQueue = logicalQueue.toMutableList().apply { add(to, removeAt(from)) }
    val selected = when {
      currentIndex == from -> to
      from < currentIndex && to >= currentIndex -> currentIndex - 1
      from > currentIndex && to <= currentIndex -> currentIndex + 1
      else -> currentIndex
    }.coerceIn(updatedQueue.indices)
    invalidatePlaybackWork()
    scope.launch {
      startQueue(
        tracks = updatedQueue,
        startIndex = selected,
        positionMs = currentPosition,
        playWhenReady = playWhenReady,
        checkStream = false,
        persistQueue = true
      )
    }
  }

  private fun currentLogicalIndex(): Int {
    val playerIndex = player.currentMediaItemIndex
    if (playerIndex < 0 || logicalQueue.isEmpty()) return -1
    return (loadedStartIndex + playerIndex).coerceIn(logicalQueue.indices)
  }

  private fun isQueueFullyLoaded(): Boolean = logicalQueue.isNotEmpty() &&
    loadedStartIndex == 0 &&
    loadedEndIndexExclusive == logicalQueue.size &&
    player.mediaItemCount == logicalQueue.size

  private fun invalidatePlaybackWork() {
    playbackRequestId.incrementAndGet()
    activeQueueGeneration += 1
    queueFillJob?.cancel()
    queueFillJob = null
  }

  private fun onMain(action: () -> Unit) {
    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
      action()
    } else {
      scope.launch { action() }
    }
  }

  private fun publishState() {
    if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
      scope.launch { publishState() }
      return
    }
    val item = player.currentMediaItem
    val track = item?.localConfiguration?.tag as? Track
    if (track != null) {
      val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
      appState.updatePlayingTrack(
        BasicTrackInfo(
          artist = track.artist,
          title = track.title,
          album = track.album,
          year = track.year,
          path = track.src,
          coverUrl = item.mediaMetadata.artworkUri?.toString().orEmpty(),
          duration = duration
        )
      )
      if (publishedTrackPath != track.src) {
        publishedTrackPath = track.src
        appState.updateTrackDetails(track.toTrackDetails())
        loadLyrics(track.src)
        loadFavorite(track.src)
      }
    } else if (publishedTrackPath != null) {
      publishedTrackPath = null
      appState.updateTrackDetails(TrackDetails.EMPTY)
    }
    val state = when {
      player.isPlaying -> PlayerState.Playing
      item != null && player.playbackState != Player.STATE_IDLE -> PlayerState.Paused
      else -> PlayerState.Stopped
    }
    val repeat = when (player.repeatMode) {
      Player.REPEAT_MODE_ONE -> Repeat.One
      Player.REPEAT_MODE_ALL -> Repeat.All
      else -> Repeat.None
    }
    val shuffle = if (player.shuffleModeEnabled) ShuffleMode.Shuffle else ShuffleMode.Off
    appState.updatePlayerStatus(
      appState.playerStatus.value.copy(
        volume = (player.volume * VOLUME_MAX_PERCENT).toInt()
          .coerceIn(0, VOLUME_MAX_PERCENT),
        mute = muted || player.volume == 0f,
        shuffle = shuffle,
        repeat = repeat,
        state = state
      )
    )
    val total = player.duration.takeIf { it != C.TIME_UNSET && it >= 0 } ?: -1L
    appState.updatePlayingPosition(
      PlayingPosition(player.currentPosition.coerceAtLeast(0L), total)
    )
  }

  private fun sampleAppPlayback(source: Player) {
    completionTracker.update(
      track = source.currentMediaItem?.localConfiguration?.tag as? Track,
      durationMs = source.playableDuration(),
      isPlaying = source.isPlaying
    )
  }

  private fun Player.playableDuration(): Long =
    duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L

  private fun recordQualifiedPlayback(playback: QualifiedAppPlayback) {
    val shouldScrobble = appScrobblingEnabled &&
      PlaybackCompletionPolicy.canScrobble(playback.durationMs)
    val entry = PlaybackHistoryEntry(
      title = playback.track.title,
      artist = playback.track.artist,
      album = playback.track.album,
      albumArtist = playback.track.albumArtist,
      path = playback.track.src,
      durationMs = playback.durationMs,
      listenedMs = playback.listenedMs,
      startedAt = playback.startedAt,
      playedAt = playback.playedAt,
      scrobbleRequested = shouldScrobble
    )
    scope.launch(dispatchers.io) {
      runCatching { playbackHistoryDao.insert(entry) }
        .onFailure { Timber.e(it, "Failed to store app playback history") }
      if (!reportPlaybackToMusicBee(playback.track.src, shouldScrobble)) {
        Timber.w("Failed to report completed app playback to MusicBee")
      }
    }
  }

  private suspend fun reportPlaybackToMusicBee(path: String, scrobble: Boolean): Boolean =
    withContext(dispatchers.io) {
      val connectionSettings = connectionProvider.getDefault() ?: return@withContext false
      if (connectionSettings.port >= MAX_PORT) return@withContext false
      val uri = streamUri(connectionSettings.address, connectionSettings.port, "played", path)
        .buildUpon()
        .appendQueryParameter("scrobble", scrobble.toString())
        .build()
      runCatching {
        val connection = URL(uri.toString()).openConnection() as HttpURLConnection
        try {
          connection.requestMethod = "PUT"
          connection.connectTimeout = STREAM_CHECK_TIMEOUT_MS
          connection.readTimeout = STREAM_CHECK_TIMEOUT_MS
          connection.useCaches = false
          connection.responseCode in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX
        } finally {
          connection.disconnect()
        }
      }.onFailure { Timber.w(it, "Failed to report app playback") }
        .getOrDefault(false)
    }

  private fun notifyQueueChanged(queueSnapshot: List<Track>, persistQueue: Boolean) {
    val revision = ++queueRevision
    scope.launch(dispatchers.io) {
      val rows = queueSnapshot.map { track ->
        LocalQueueTrack(track.title, track.artist, track.album, track.src)
      }
      withContext(dispatchers.main) {
        if (revision == queueRevision) _queue.value = rows
      }
    }
    if (persistQueue) scheduleQueuePersistence(queueSnapshot)
  }

  private suspend fun restoreState() {
    val initialRequestId = playbackRequestId.get()
    val persistedQueue = withContext(dispatchers.io) {
      queueFileMutex.withLock { readPersistedQueue() }
    }
    val legacyCount = statePreferences.getInt(KEY_QUEUE_COUNT, 0)
    val tracks = persistedQueue ?: withContext(dispatchers.io) {
      (0 until legacyCount).mapNotNull(::readLegacyTrack)
    }
    if (tracks.isEmpty() || playbackRequestId.get() != initialRequestId) return

    val selected = statePreferences.getInt(KEY_CURRENT_INDEX, 0).coerceIn(tracks.indices)
    val position = statePreferences.getLong(KEY_POSITION_MS, 0L).coerceAtLeast(0L)
    val restored = startQueue(
      tracks = tracks,
      startIndex = selected,
      positionMs = position,
      playWhenReady = false,
      checkStream = false,
      persistQueue = false
    )
    if (!restored) return
    withContext(dispatchers.main) {
      player.shuffleModeEnabled = statePreferences.getBoolean(KEY_SHUFFLE, false)
      player.repeatMode = statePreferences.getInt(KEY_REPEAT, Player.REPEAT_MODE_OFF)
    }
    if (persistedQueue == null) {
      scheduleQueuePersistence(tracks)
    } else if (legacyCount > 0) {
      scope.launch(dispatchers.io) { clearLegacyQueueState() }
    }
  }

  private fun savePlaybackState() {
    if (restoring || android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) return
    statePreferences.edit()
      .putInt(KEY_CURRENT_INDEX, currentLogicalIndex().coerceAtLeast(0))
      .putLong(KEY_POSITION_MS, player.currentPosition.coerceAtLeast(0L))
      .putBoolean(KEY_SHUFFLE, player.shuffleModeEnabled)
      .putInt(KEY_REPEAT, player.repeatMode)
      .apply()
  }

  private fun scheduleQueuePersistence(queueSnapshot: List<Track>) {
    val generation = persistenceGeneration.incrementAndGet()
    queuePersistenceJob?.cancel()
    queuePersistenceJob = scope.launch(dispatchers.io) {
      delay(QUEUE_PERSIST_DEBOUNCE_MS)
      queueFileMutex.withLock {
        if (generation != persistenceGeneration.get()) return@withLock
        if (writePersistedQueue(queueSnapshot)) clearLegacyQueueState()
      }
    }
  }

  private fun writePersistedQueue(queueSnapshot: List<Track>): Boolean {
    var output: FileOutputStream? = null
    return try {
      output = atomicQueueFile.startWrite()
      LocalPlaybackQueueCodec.write(output, queueSnapshot)
      atomicQueueFile.finishWrite(output)
      output = null
      true
    } catch (error: Exception) {
      output?.let(atomicQueueFile::failWrite)
      Timber.e(error, "Failed to persist local playback queue")
      false
    }
  }

  private fun readPersistedQueue(): List<Track>? = try {
    atomicQueueFile.openRead().use(LocalPlaybackQueueCodec::read)
  } catch (_: FileNotFoundException) {
    null
  } catch (error: Exception) {
    Timber.e(error, "Failed to restore persisted local playback queue")
    null
  }

  private fun deletePersistedQueue() {
    val generation = persistenceGeneration.incrementAndGet()
    queuePersistenceJob?.cancel()
    queuePersistenceJob = scope.launch(dispatchers.io) {
      queueFileMutex.withLock {
        if (generation == persistenceGeneration.get()) atomicQueueFile.delete()
      }
    }
  }

  private fun clearLegacyQueueState() {
    val legacyKeys = statePreferences.all.keys.filter { key ->
      key == KEY_QUEUE_COUNT || key.startsWith(KEY_TRACK_PREFIX)
    }
    if (legacyKeys.isEmpty()) return
    statePreferences.edit().apply {
      legacyKeys.forEach(::remove)
      apply()
    }
  }

  private fun readLegacyTrack(index: Int): Track? {
    val encoded = statePreferences.getString("$KEY_TRACK_PREFIX$index", null) ?: return null
    return runCatching {
      val fields = encoded.decodeBase64().split(FIELD_SEPARATOR)
      require(fields.size == TRACK_FIELD_COUNT)
      Track(
        artist = fields[0],
        title = fields[1],
        src = fields[2],
        trackno = fields[3].toInt(),
        disc = fields[4].toInt(),
        albumArtist = fields[5],
        album = fields[6],
        genre = fields[7],
        year = fields[8],
        id = fields[9].toLong()
      )
    }.onFailure { Timber.w(it, "Skipping invalid legacy queue item") }.getOrNull()
  }

  private fun String.decodeBase64(): String = String(
    Base64.decode(this, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
    StandardCharsets.UTF_8
  )

  private suspend fun isStreamAvailable(uri: Uri?): Boolean = withContext(dispatchers.io) {
    uri ?: return@withContext false
    runCatching {
      val connection = URL(uri.toString()).openConnection() as HttpURLConnection
      try {
        connection.requestMethod = "HEAD"
        connection.connectTimeout = STREAM_CHECK_TIMEOUT_MS
        connection.readTimeout = STREAM_CHECK_TIMEOUT_MS
        connection.useCaches = false
        connection.responseCode in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX
      } finally {
        connection.disconnect()
      }
    }.onFailure { Timber.w(it, "MusicBee audio stream preflight failed") }
      .getOrDefault(false)
  }

  private fun loadLyrics(path: String) {
    appState.updateLyrics(emptyList())
    lyricsJob?.cancel()
    lyricsJob = scope.launch {
      val lyrics = fetchLyrics(path)
      if (publishedTrackPath == path) appState.updateLyrics(lyrics)
    }
  }

  private fun loadFavorite(path: String) {
    appState.updateTrackRating(com.kelsos.mbrc.core.common.state.TrackRating())
    favoriteJob?.cancel()
    favoriteJob = scope.launch {
      fetchFavorite(path)?.let { loved ->
        if (publishedTrackPath == path) {
          appState.updateTrackRating(
            com.kelsos.mbrc.core.common.state.TrackRating(
              lfmRating = if (loved) {
                com.kelsos.mbrc.core.common.state.LfmRating.Loved
              } else {
                com.kelsos.mbrc.core.common.state.LfmRating.Normal
              }
            )
          )
        }
      }
    }
  }

  private suspend fun fetchFavorite(path: String): Boolean? = withContext(dispatchers.io) {
    val connectionSettings = connectionProvider.getDefault() ?: return@withContext null
    if (connectionSettings.port >= MAX_PORT) return@withContext null
    val uri = streamUri(connectionSettings.address, connectionSettings.port, "favorite", path)
    runCatching {
      val connection = URL(uri.toString()).openConnection() as HttpURLConnection
      try {
        connection.requestMethod = "GET"
        connection.connectTimeout = STREAM_CHECK_TIMEOUT_MS
        connection.readTimeout = STREAM_CHECK_TIMEOUT_MS
        connection.useCaches = false
        if (connection.responseCode !in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX) {
          return@runCatching null
        }
        connection.inputStream.bufferedReader(StandardCharsets.US_ASCII).use {
          it.readText() == "loved"
        }
      } finally {
        connection.disconnect()
      }
    }.onFailure { Timber.w(it, "Failed to load favorite state for local playback") }
      .getOrNull()
  }

  private suspend fun setFavoriteForPath(path: String, loved: Boolean): Boolean =
    withContext(dispatchers.io) {
      val connectionSettings = connectionProvider.getDefault() ?: return@withContext false
      if (connectionSettings.port >= MAX_PORT) return@withContext false
      val uri = streamUri(connectionSettings.address, connectionSettings.port, "favorite", path)
        .buildUpon()
        .appendQueryParameter("loved", loved.toString())
        .build()
      runCatching {
        val connection = URL(uri.toString()).openConnection() as HttpURLConnection
        try {
          connection.requestMethod = "PUT"
          connection.connectTimeout = STREAM_CHECK_TIMEOUT_MS
          connection.readTimeout = STREAM_CHECK_TIMEOUT_MS
          connection.useCaches = false
          connection.responseCode in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX
        } finally {
          connection.disconnect()
        }
      }.onFailure { Timber.w(it, "Failed to update favorite state for local playback") }
        .getOrDefault(false)
    }

  private suspend fun fetchLyrics(path: String): List<String> = withContext(dispatchers.io) {
    val connectionSettings = connectionProvider.getDefault() ?: return@withContext emptyList()
    if (connectionSettings.port >= MAX_PORT) return@withContext emptyList()
    val uri = streamUri(connectionSettings.address, connectionSettings.port, "lyrics", path)
    repeat(LYRICS_PROVIDER_RETRY_ATTEMPTS) { attempt ->
      val result = runCatching<List<String>?> {
        val connection = URL(uri.toString()).openConnection() as HttpURLConnection
        try {
          connection.requestMethod = "GET"
          connection.connectTimeout = STREAM_CHECK_TIMEOUT_MS
          connection.readTimeout = LYRICS_READ_TIMEOUT_MS
          connection.useCaches = false
          when (connection.responseCode) {
            HTTP_ACCEPTED -> null

            in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX -> {
              connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                parseLyrics(reader.readText())
              }
            }

            else -> emptyList()
          }
        } finally {
          connection.disconnect()
        }
      }.onFailure { Timber.w(it, "Failed to load lyrics for local playback") }
        .getOrElse { emptyList() }

      if (result != null) {
        return@withContext result
      }
      if (attempt < LYRICS_PROVIDER_RETRY_ATTEMPTS - 1) {
        delay(LYRICS_PROVIDER_RETRY_DELAY_MS)
      }
    }
    emptyList()
  }

  private fun streamUri(address: String, commandPort: Int, endpoint: String, path: String): Uri {
    val encodedPath = Base64.encodeToString(
      path.toByteArray(StandardCharsets.UTF_8),
      Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )
    val host = if (address.contains(':') && !address.startsWith("[")) "[$address]" else address
    return Uri.Builder()
      .scheme("http")
      .encodedAuthority("$host:${commandPort + 1}")
      .appendPath(endpoint)
      .appendPath(encodedPath)
      .build()
  }

  private fun parseLyrics(raw: String): List<String> = raw
    .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("</?p>", RegexOption.IGNORE_CASE), "\n")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&apos;", "'")
    .replace("&amp;", "&")
    .split(Regex("\\r\\n|\\n|\\r"))
    .map(String::trimEnd)
    .dropLastWhile(String::isEmpty)

  companion object {
    private const val MAX_PORT = 65_535
    private const val HTTP_SUCCESS_MIN = 200
    private const val HTTP_SUCCESS_MAX = 299
    private const val HTTP_ACCEPTED = 202
    private const val VOLUME_MAX_PERCENT = 100
    private const val MIN_AUDIBLE_VOLUME = 0.01f
    private const val SESSION_ID = "mbrc-device-playback"
    private const val STREAM_CHECK_TIMEOUT_MS = 3_000
    private const val LYRICS_READ_TIMEOUT_MS = 5_000
    private const val LYRICS_PROVIDER_RETRY_ATTEMPTS = 15
    private const val LYRICS_PROVIDER_RETRY_DELAY_MS = 1_000L
    private const val POSITION_UPDATE_MS = 500L
    private const val PLAYBACK_STATE_SAVE_MS = 5_000L
    private const val QUEUE_PERSIST_DEBOUNCE_MS = 250L
    private const val QUEUE_CHUNK_SIZE = 250
    private const val PLAYBACK_STATE_PREFS = "local_playback_state"
    private const val QUEUE_STATE_FILE = "local_playback_queue_v2.bin"
    private const val KEY_QUEUE_COUNT = "queue_count"
    private const val KEY_CURRENT_INDEX = "current_index"
    private const val KEY_POSITION_MS = "position_ms"
    private const val KEY_SHUFFLE = "shuffle"
    private const val KEY_REPEAT = "repeat"
    private const val KEY_TRACK_PREFIX = "track_"
    private const val FIELD_SEPARATOR = "\u001f"
    private const val TRACK_FIELD_COUNT = 10
  }
}

internal fun Track.toTrackDetails(): TrackDetails = TrackDetails(
  albumArtist = albumArtist,
  genre = genre,
  trackNo = trackno.takeIf { it > 0 }?.toString().orEmpty(),
  discNo = disc.takeIf { it > 0 }?.toString().orEmpty()
)
