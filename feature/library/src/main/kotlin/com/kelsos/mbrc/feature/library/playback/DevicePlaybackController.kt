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
import com.kelsos.mbrc.core.networking.DefaultConnectionProvider
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
  private val dispatchers: AppCoroutineDispatchers
) : LocalPlaybackController {
  private val scope = CoroutineScope(Job() + dispatchers.main)
  private var muted = false
  private var volumeBeforeMute = 1f
  @Volatile private var localPlaybackActive = false
  private val _queue = MutableStateFlow<List<LocalQueueTrack>>(emptyList())

  override val queue = _queue.asStateFlow()

  val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build().apply {
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

  val mediaSession: MediaSession = MediaSession.Builder(context.applicationContext, player)
    .setId(SESSION_ID).build()

  val hasLocalPlayback: Boolean
    get() = localPlaybackActive

  init {
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
    return runCatching {
      tracks.map { track ->
        val encodedPath = Base64.encodeToString(
          track.src.toByteArray(StandardCharsets.UTF_8),
          Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val host = if (connection.address.contains(':') && !connection.address.startsWith("["))
          "[${connection.address}]" else connection.address
        val uri = Uri.Builder().scheme("http")
          .encodedAuthority("$host:${connection.port + 1}")
          .appendPath("audio").appendPath(encodedPath).build()
        MediaItem.Builder().setMediaId(track.src).setUri(uri).setTag(track)
          .setMediaMetadata(
            MediaMetadata.Builder().setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
              .setTitle(track.title).setArtist(track.artist).setAlbumTitle(track.album)
              .setAlbumArtist(track.albumArtist).build()
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
      appState.updatePlayingTrack(
        BasicTrackInfo(track.artist, track.title, track.album, track.year, track.src, "", duration)
      )
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
  }

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

  companion object {
    private const val MAX_PORT = 65535
    private const val SESSION_ID = "mbrc-device-playback"
    private const val STREAM_CHECK_TIMEOUT_MS = 3_000
    private const val POSITION_UPDATE_MS = 500L
  }
}
