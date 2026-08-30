package com.kelsos.mbrc.feature.playback.nowplaying

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.kelsos.mbrc.core.common.mvvm.BaseViewModel
import com.kelsos.mbrc.core.common.playback.LocalPlaybackController
import com.kelsos.mbrc.core.common.state.AppStateFlow
import com.kelsos.mbrc.core.common.state.ConnectionStateFlow
import com.kelsos.mbrc.core.common.utilities.coroutines.AppCoroutineDispatchers
import com.kelsos.mbrc.core.data.nowplaying.NowPlaying
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

interface INowPlayingActions {
  fun reload()

  fun reload(showUserMessage: Boolean)

  fun play(position: Int)

  fun removeTrack(position: Int)

  fun moveTrack(from: Int, to: Int)

  fun move()

  fun search(query: String)
}

class NowPlayingActions(
  private val scope: CoroutineScope,
  private val dispatchers: AppCoroutineDispatchers,
  private val repository: NowPlayingRepository,
  private val moveManager: MoveManager,
  private val connectionStateFlow: ConnectionStateFlow,
  private val localPlaybackController: LocalPlaybackController,
  private val emit: suspend (uiMessage: NowPlayingUiMessages) -> Unit
) : INowPlayingActions {
  override fun reload() {
    reload(showUserMessage = true)
  }

  override fun reload(showUserMessage: Boolean) {
    scope.launch(dispatchers.network) {
      if (!connectionStateFlow.isConnected) {
        if (showUserMessage) {
          emit(NowPlayingUiMessages.NetworkUnavailable)
        }
        return@launch
      }
      val result =
        try {
          repository.getRemote()
          if (showUserMessage) NowPlayingUiMessages.RefreshSucceeded else null
        } catch (e: CancellationException) {
          // A newer refresh superseded this one. If this coroutine is still active the shared
          // refresh was cancelled (not our scope), so just dismiss the indicator; otherwise our
          // scope is going away (e.g. screen closed) and the cancellation must propagate.
          if (!isActive) throw e
          if (showUserMessage) NowPlayingUiMessages.RefreshSuperseded else null
        } catch (e: IOException) {
          Timber.e(e)
          if (showUserMessage) NowPlayingUiMessages.RefreshFailed(e) else null
        }

      result?.let { emit(it) }
    }
  }

  override fun play(position: Int) {
    scope.launch(dispatchers.database) {
      try {
        if (position !in localPlaybackController.queue.value.indices) {
          emit(NowPlayingUiMessages.PlayFailed)
        } else {
          localPlaybackController.playQueueItem(position)
        }
      } catch (e: IOException) {
        Timber.e(e)
        emit(NowPlayingUiMessages.PlayFailed)
      }
    }
  }

  override fun removeTrack(position: Int) {
    scope.launch(dispatchers.database) {
      try {
        delay(REMOVE_DELAY_MS)
        localPlaybackController.removeQueueItem(position)
      } catch (e: IOException) {
        Timber.e(e)
        emit(NowPlayingUiMessages.RemoveFailed)
      }
    }
  }

  override fun moveTrack(from: Int, to: Int) {
    moveManager.move(from, to)
  }

  override fun move() {
    scope.launch(dispatchers.database) {
      moveManager.commit()
    }
  }

  override fun search(query: String) {
    scope.launch(dispatchers.database) {
      val result = localPlaybackController.queue.value.indexOfFirst {
        it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
      }
      if (result < 0) {
        emit(NowPlayingUiMessages.SearchNotFound)
        return@launch
      }

      try {
        localPlaybackController.playQueueItem(result)
        emit(NowPlayingUiMessages.SearchSuccess(localPlaybackController.queue.value[result].title))
      } catch (e: IOException) {
        Timber.e(e)
        emit(NowPlayingUiMessages.PlayFailed)
      }
    }
  }

  companion object {
    const val REMOVE_DELAY_MS = 400L
  }
}

class NowPlayingViewModel(
  repository: NowPlayingRepository,
  dispatchers: AppCoroutineDispatchers,
  moveManager: MoveManager,
  connectionStateFlow: ConnectionStateFlow,
  appState: AppStateFlow,
  localPlaybackController: LocalPlaybackController
) : BaseViewModel<NowPlayingUiMessages>() {
  val tracks: Flow<PagingData<NowPlaying>> = localPlaybackController.queue
    .map { queue ->
      PagingData.from(
        queue.mapIndexed { index, track ->
          NowPlaying(track.title, track.artist, track.path, index, index.toLong())
        }
      )
    }.cachedIn(viewModelScope)
  val playingTrack = appState.playingTrack
  val connectionState = connectionStateFlow.connection
  val syncProgress: StateFlow<SyncProgress?> = repository.syncProgress()
  val trackCount: StateFlow<Int> = localPlaybackController.queue
    .map { it.size }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
  val actions: NowPlayingActions =
    NowPlayingActions(
      scope = viewModelScope,
      dispatchers = dispatchers,
      repository = repository,
      moveManager = moveManager,
      connectionStateFlow = connectionStateFlow,
      localPlaybackController = localPlaybackController,
      emit = this::emit
    )

  init {
    actions.reload(showUserMessage = false)
    moveManager.onMoveCommit { originalPosition, finalPosition ->
      viewModelScope.launch(dispatchers.database) {
        try {
          localPlaybackController.moveQueueItem(originalPosition, finalPosition)
        } catch (e: IOException) {
          Timber.e(e)
          emit(NowPlayingUiMessages.MoveFailed)
        }
      }
    }
  }
}
