package com.kelsos.mbrc.feature.library.playback

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.kelsos.mbrc.core.common.state.AppStatePublisher
import com.kelsos.mbrc.core.common.playback.LocalPlaybackController
import com.kelsos.mbrc.core.common.playback.LocalQueueTrack
import com.kelsos.mbrc.core.common.state.BasicTrackInfo
import com.kelsos.mbrc.core.common.state.PlayerState
import com.kelsos.mbrc.core.common.state.PlayingPosition
import com.kelsos.mbrc.core.common.state.Repeat
import com.kelsos.mbrc.core.common.state.ShuffleMode
import com.kelsos.mbrc.core.common.utilities.coroutines.AppCoroutineDispatchers
import com.kelsos.mbrc.core.data.library.track.Track
import com.kelsos.mbrc.core.data.library.track.key
import com.kelsos.mbrc.core.networking.DefaultConnectionProvider
import com.kelsos.mbrc.feature.library.data.HighResolutionCoverCache
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/** Local playback source of truth for MusicBee library tracks. */
class DevicePlaybackController(
  context: Context,
  private val connectionProvider: DefaultConnectionProvider,
  private val appState: AppStatePublisher,
  private val dispatchers: AppCoroutineDispatchers,
  private val highResolutionCoverCache: HighResolutionCoverCache
) : LocalPlaybackController {
  private val appContext = context.applicationContext
  private val scope = CoroutineScope(Job() + dispatchers.main)
  private var muted = false
  private var volumeBeforeMute = 1f
  private var publishedTrackPath: String? = null
  private var lyricsJob: Job? = null
  private var favoriteJob: Job? = null
  private var restoring = true
  @Volatile private var localPlaybackActive = false
  private val _queue = MutableStateFlow<List<LocalQueueTrack>>(emptyList())
  private val statePreferences =
    appContext.getSharedPreferences(PLAYBACK_STATE_PREFS, Context.MODE_PRIVATE)

  override val queue = _queue.asStateFlow()

  val player: ExoPlayer = ExoPlayer.Builder(appContext).build().apply {
    setAudioAttributes(
      AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA).build(), true
    )
    setHandleAudioBecomingNoisy(true)
    addListener(object : Player.Listener {
      override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
            Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_IS_PLAYING_CHANGED,
            Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_POSITION_DISCONTINUITY,
            Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED, Player.EVENT_REPEAT_MODE_CHANGED,
            Player.EVENT_VOLUME_CHANGED
          )) publishState()
        publishQueue()
      }
    })
  }

  val mediaSession: MediaSession = MediaSession.Builder(appContext, player)
    .setId(SESSION_ID).build()

  override val hasLocalPlayback: Boolean
    get() = localPlaybackActive

  init {
    scope.launch {
      restoreState()
      restoring = false
      publishState()
      publishQueue()
    }
    scope.launch {
      while (isActive) {
        delay(POSITION_UPDATE_MS)
        publishState()
      }
    }
  }

  suspend fun play(track: Track): Boolean = playTracks(listOf(track), 0)

  suspend fun playTracks(tracks: List<Track>, startIndex: Int = 0): Boolean {
    if (tracks.isEmpty()) return false
    val items = createItems(tracks) ?: return false
    val selected = startIndex.coerceIn(items.indices)
    if (!isStreamAvailable(items[selected].localConfiguration?.uri)) {
      Timber.w("MusicBee audio stream is not reachable")
      return false
    }
    return withContext(dispatchers.main) {
      runCatching {
        player.setMediaItems(items, selected, 0L)
        localPlaybackActive = true
        publishQueue()
        player.prepare()
        player.play()
        publishState()
        true
      }.onFailure { Timber.e(it, "Failed to start local playback") }.getOrDefault(false)
    }
  }

  suspend fun enqueueNext(tracks: List<Track>): Boolean = addTracks(tracks, true)

  suspend fun enqueueLast(tracks: List<Track>): Boolean = addTracks(tracks, false)

  override fun playPause() = onMain { if (player.isPlaying) player.pause() else player.play() }
  override fun play() = onMain { player.play() }
  override fun pause() = onMain { player.pause() }
  override fun previous() = onMain { player.seekToPreviousMediaItem() }
  override fun next() = onMain { player.seekToNextMediaItem() }
  override fun playQueueItem(index: Int) = onMain {
    if (index in 0 until player.mediaItemCount) {
      player.seekTo(index, 0L)
      player.play()
    }
  }

  override fun removeQueueItem(index: Int) = onMain {
    if (index in 0 until player.mediaItemCount) {
      player.removeMediaItem(index)
      localPlaybackActive = player.mediaItemCount > 0
      publishQueue()
      publishState()
    }
  }

  override fun moveQueueItem(from: Int, to: Int) = onMain {
    if (from in 0 until player.mediaItemCount && to in 0 until player.mediaItemCount) {
      player.moveMediaItem(from, to)
      publishQueue()
    }
  }

  override fun stop() = onMain {
    player.stop()
    player.clearMediaItems()
    localPlaybackActive = false
    publishedTrackPath = null
    lyricsJob?.cancel()
    favoriteJob?.cancel()
    appState.updateLyrics(emptyList())
    appState.updateTrackRating(com.kelsos.mbrc.core.common.state.TrackRating())
    publishQueue()
    publishState()
  }

  override fun seekTo(positionMs: Long) = onMain { player.seekTo(positionMs.coerceAtLeast(0L)) }

  override fun setVolume(percent: Int) = onMain {
    val value = percent.coerceIn(0, 100) / 100f
    player.volume = value
    if (value > 0f) {
      muted = false
      volumeBeforeMute = value
    }
    publishState()
  }

  override fun adjustVolume(deltaPercent: Int) = onMain {
    setVolume(((player.volume * 100).toInt() + deltaPercent).coerceIn(0, 100))
  }

  override fun toggleMute() = onMain {
    if (muted || player.volume == 0f) {
      player.volume = volumeBeforeMute.coerceIn(0.01f, 1f)
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

  private suspend fun addTracks(tracks: List<Track>, next: Boolean): Boolean {
    val items = createItems(tracks) ?: return false
    return withContext(dispatchers.main) {
      runCatching {
        val index = if (next) (player.currentMediaItemIndex + 1).coerceAtLeast(0)
        else player.mediaItemCount
        player.addMediaItems(index, items)
        publishQueue()
        true
      }.onFailure { Timber.e(it, "Failed to enqueue local tracks") }.getOrDefault(false)
    }
  }

  private suspend fun createItems(tracks: List<Track>): List<MediaItem>? {
    val connection = withContext(dispatchers.io) { connectionProvider.getDefault() } ?: return null
    if (connection.port >= MAX_PORT) return null
    val artworkByAlbum = mutableMapOf<String, Uri?>()
    return runCatching {
      tracks.map { track ->
        val uri = streamUri(connection.address, connection.port, "audio", track.src)
        val artworkUri = if (artworkByAlbum.containsKey(track.key())) {
          artworkByAlbum[track.key()]
        } else {
          val cached = highResolutionCoverCache.load(track.src, track.key())?.let(Uri::fromFile)
            ?: thumbnailUri(track)
          artworkByAlbum[track.key()] = cached
          cached
        }
        MediaItem.Builder().setMediaId(track.src).setUri(uri).setTag(track)
          .setMediaMetadata(
            MediaMetadata.Builder().setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
              .setTitle(track.title).setArtist(track.artist).setAlbumTitle(track.album)
              .setAlbumArtist(track.albumArtist).setArtworkUri(artworkUri).build()
          ).build()
      }
    }.onFailure { Timber.e(it, "Failed to create local playback items") }.getOrNull()
  }

  private fun onMain(action: () -> Unit) {
    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) action()
    else scope.launch { action() }
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
      val coverUrl = item.mediaMetadata.artworkUri?.toString()
        ?: thumbnailUri(track)?.toString().orEmpty()
      appState.updatePlayingTrack(
        BasicTrackInfo(
          artist = track.artist,
          title = track.title,
          album = track.album,
          year = track.year,
          path = track.src,
          coverUrl = coverUrl,
          duration = duration
        )
      )
      if (publishedTrackPath != track.src) {
        publishedTrackPath = track.src
        loadLyrics(track.src)
        loadFavorite(track.src)
      }
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
        volume = (player.volume * 100).toInt().coerceIn(0, 100),
        mute = muted || player.volume == 0f, shuffle = shuffle, repeat = repeat, state = state
      )
    )
    val total = player.duration.takeIf { it != C.TIME_UNSET && it >= 0 } ?: -1L
    appState.updatePlayingPosition(PlayingPosition(player.currentPosition.coerceAtLeast(0L), total))
    saveState()
  }

  private fun publishQueue() {
    if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
      scope.launch { publishQueue() }
      return
    }
    _queue.value = (0 until player.mediaItemCount).mapNotNull { index ->
      val track = player.getMediaItemAt(index).localConfiguration?.tag as? Track
        ?: return@mapNotNull null
      LocalQueueTrack(track.title, track.artist, track.album, track.src)
    }
    saveState()
  }

  private suspend fun restoreState() {
    val count = statePreferences.getInt(KEY_QUEUE_COUNT, 0)
    if (count <= 0) return
    val tracks = (0 until count).mapNotNull { index -> readTrack(index) }
    if (tracks.isEmpty()) return
    val items = createItems(tracks) ?: return
    val selected = statePreferences.getInt(KEY_CURRENT_INDEX, 0).coerceIn(items.indices)
    val position = statePreferences.getLong(KEY_POSITION_MS, 0L).coerceAtLeast(0L)
    withContext(dispatchers.main) {
      runCatching {
        player.setMediaItems(items, selected, position)
        player.shuffleModeEnabled = statePreferences.getBoolean(KEY_SHUFFLE, false)
        player.repeatMode = statePreferences.getInt(KEY_REPEAT, Player.REPEAT_MODE_OFF)
        localPlaybackActive = true
        player.prepare()
      }.onFailure { Timber.e(it, "Failed to restore local playback state") }
    }
  }

  private fun saveState() {
    if (restoring || android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) return
    val items = (0 until player.mediaItemCount).mapNotNull { index ->
      player.getMediaItemAt(index).localConfiguration?.tag as? Track
    }
    statePreferences.edit().apply {
      putInt(KEY_QUEUE_COUNT, items.size)
      items.forEachIndexed { index, track -> writeTrack(this, index, track) }
      putInt(KEY_CURRENT_INDEX, player.currentMediaItemIndex.coerceAtLeast(0))
      putLong(KEY_POSITION_MS, player.currentPosition.coerceAtLeast(0L))
      putBoolean(KEY_SHUFFLE, player.shuffleModeEnabled)
      putInt(KEY_REPEAT, player.repeatMode)
      apply()
    }
  }

  private fun readTrack(index: Int): Track? {
    val encoded = statePreferences.getString("$KEY_TRACK_PREFIX$index", null) ?: return null
    val fields = encoded.decodeBase64().split(FIELD_SEPARATOR)
    if (fields.size != TRACK_FIELD_COUNT) return null
    return runCatching {
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
    }.getOrNull()
  }

  private fun writeTrack(
    preferences: android.content.SharedPreferences.Editor,
    index: Int,
    track: Track
  ) {
    val fields = listOf(
      track.artist, track.title, track.src, track.trackno.toString(), track.disc.toString(),
      track.albumArtist, track.album, track.genre, track.year, track.id.toString()
    )
    preferences.putString(
      "$KEY_TRACK_PREFIX$index",
      fields.joinToString(FIELD_SEPARATOR).encodeBase64()
    )
  }

  private fun String.encodeBase64(): String = Base64.encodeToString(
    toByteArray(StandardCharsets.UTF_8),
    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
  )

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
        connection.responseCode in 200..299
      } finally { connection.disconnect() }
    }.onFailure { Timber.w(it, "MusicBee audio stream preflight failed") }.getOrDefault(false)
  }

  private fun thumbnailUri(track: Track): Uri? {
    val cover = File(File(appContext.cacheDir, "covers"), track.key())
    return cover.takeIf(File::isFile)?.let(Uri::fromFile)
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
        if (connection.responseCode !in 200..299) return@runCatching null
        connection.inputStream.bufferedReader(StandardCharsets.US_ASCII).use { it.readText() == "loved" }
      } finally {
        connection.disconnect()
      }
    }.onFailure { Timber.w(it, "Failed to load favorite state for local playback") }
      .getOrNull()
  }

  private suspend fun setFavoriteForPath(path: String, loved: Boolean): Boolean = withContext(dispatchers.io) {
    val connectionSettings = connectionProvider.getDefault() ?: return@withContext false
    if (connectionSettings.port >= MAX_PORT) return@withContext false
    val uri = Uri.parse(streamUri(connectionSettings.address, connectionSettings.port, "favorite", path).toString())
      .buildUpon().appendQueryParameter("loved", loved.toString()).build()
    runCatching {
      val connection = URL(uri.toString()).openConnection() as HttpURLConnection
      try {
        connection.requestMethod = "PUT"
        connection.connectTimeout = STREAM_CHECK_TIMEOUT_MS
        connection.readTimeout = STREAM_CHECK_TIMEOUT_MS
        connection.useCaches = false
        connection.responseCode in 200..299
      } finally {
        connection.disconnect()
      }
    }.onFailure { Timber.w(it, "Failed to update favorite state for local playback") }
      .getOrDefault(false)
  }

  private suspend fun fetchLyrics(path: String): List<String> = withContext(dispatchers.io) {
    val connectionSettings = connectionProvider.getDefault() ?: return@withContext emptyList()
    if (connectionSettings.port >= MAX_PORT) return@withContext emptyList()
    val uri = streamUri(
      connectionSettings.address,
      connectionSettings.port,
      "lyrics",
      path
    )
    runCatching {
      val connection = URL(uri.toString()).openConnection() as HttpURLConnection
      try {
        connection.requestMethod = "GET"
        connection.connectTimeout = STREAM_CHECK_TIMEOUT_MS
        connection.readTimeout = LYRICS_READ_TIMEOUT_MS
        connection.useCaches = false
        if (connection.responseCode !in 200..299) return@runCatching emptyList()
        connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
          parseLyrics(reader.readText())
        }
      } finally {
        connection.disconnect()
      }
    }.onFailure { Timber.w(it, "Failed to load lyrics for local playback") }
      .getOrDefault(emptyList())
  }

  private fun streamUri(address: String, commandPort: Int, endpoint: String, path: String): Uri {
    val encodedPath = Base64.encodeToString(
      path.toByteArray(StandardCharsets.UTF_8),
      Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )
    val host = if (address.contains(':') && !address.startsWith("[")) "[$address]" else address
    return Uri.Builder().scheme("http")
      .encodedAuthority("$host:${commandPort + 1}")
      .appendPath(endpoint).appendPath(encodedPath).build()
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
    .filterNot { LRC_METADATA.matches(it.trim()) }
    .map { LRC_TIMESTAMP.replace(it, "").trimEnd() }
    .dropLastWhile(String::isEmpty)

  companion object {
    private const val MAX_PORT = 65535
    private const val SESSION_ID = "mbrc-device-playback"
    private const val STREAM_CHECK_TIMEOUT_MS = 3_000
    private const val LYRICS_READ_TIMEOUT_MS = 5_000
    private const val POSITION_UPDATE_MS = 500L
    private const val PLAYBACK_STATE_PREFS = "local_playback_state"
    private const val KEY_QUEUE_COUNT = "queue_count"
    private const val KEY_CURRENT_INDEX = "current_index"
    private const val KEY_POSITION_MS = "position_ms"
    private const val KEY_SHUFFLE = "shuffle"
    private const val KEY_REPEAT = "repeat"
    private const val KEY_TRACK_PREFIX = "track_"
    private const val FIELD_SEPARATOR = "\u001f"
    private const val TRACK_FIELD_COUNT = 10
    private val LRC_TIMESTAMP = Regex("\\[\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?]")
    private val LRC_METADATA = Regex(
      "\\[(?:ar|ti|al|by|offset|re|ve):.*]",
      RegexOption.IGNORE_CASE
    )
  }
}
