package com.kelsos.mbrc.service.mediasession

import android.content.Intent
import android.view.KeyEvent
import com.kelsos.mbrc.core.common.playback.LocalPlaybackController

class MediaIntentHandler(
  private val devicePlaybackController: LocalPlaybackController
) {
  private var previousClick: Long = 0

  private fun getKeyEventFromIntent(mediaIntent: Intent?): KeyEvent? {
    val action = mediaIntent?.action

    if (action == Intent.ACTION_MEDIA_BUTTON) {
      val extras = mediaIntent.extras
      return if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
        @Suppress("DEPRECATION")
        (extras?.getParcelable(Intent.EXTRA_KEY_EVENT))
      } else {
        extras?.getParcelable(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
      }
    }
    return null
  }

  private fun detectDoubleClick(): Boolean {
    val currentClick = System.currentTimeMillis()
    if (currentClick - previousClick < DOUBLE_CLICK_INTERVAL) {
      devicePlaybackController.next()
      return true
    }
    previousClick = currentClick
    devicePlaybackController.playPause()
    return true
  }

  fun handleMediaIntent(mediaIntent: Intent?): Boolean {
    val event = getKeyEventFromIntent(mediaIntent)
    if (event?.action != KeyEvent.ACTION_DOWN) {
      return false
    }

    return when (event.keyCode) {
      KeyEvent.KEYCODE_HEADSETHOOK -> detectDoubleClick()

      KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> devicePlaybackController.playPause().let { true }

      KeyEvent.KEYCODE_MEDIA_PLAY -> devicePlaybackController.play().let { true }

      KeyEvent.KEYCODE_MEDIA_PAUSE -> devicePlaybackController.pause().let { true }

      KeyEvent.KEYCODE_MEDIA_STOP -> devicePlaybackController.stop().let { true }

      KeyEvent.KEYCODE_MEDIA_NEXT -> devicePlaybackController.next().let { true }

      KeyEvent.KEYCODE_MEDIA_PREVIOUS -> devicePlaybackController.previous().let { true }

      KeyEvent.KEYCODE_VOLUME_UP -> {
        devicePlaybackController.adjustVolume(10)
        true
      }

      KeyEvent.KEYCODE_VOLUME_DOWN -> {
        devicePlaybackController.adjustVolume(-10)
        true
      }

      KeyEvent.KEYCODE_VOLUME_MUTE -> devicePlaybackController.toggleMute().let { true }

      else -> false
    }
  }


  companion object {
    private const val DOUBLE_CLICK_INTERVAL = 350
  }
}
