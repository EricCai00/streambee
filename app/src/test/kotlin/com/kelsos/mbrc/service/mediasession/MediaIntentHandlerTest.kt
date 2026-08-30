package com.kelsos.mbrc.service.mediasession

import android.content.Intent
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kelsos.mbrc.core.common.playback.LocalPlaybackController
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaIntentHandlerTest {

  private lateinit var mediaIntentHandler: MediaIntentHandler
  private lateinit var devicePlaybackController: LocalPlaybackController

  @Before
  fun setUp() {
    devicePlaybackController = mockk(relaxed = true)
    mediaIntentHandler = MediaIntentHandler(devicePlaybackController)
  }

  // region Null and invalid intent tests

  @Test
  fun `handleMediaIntent should return false for null intent`() {
    val result = mediaIntentHandler.handleMediaIntent(null)
    assertThat(result).isFalse()
  }

  @Test
  fun `handleMediaIntent should return false for non-media-button action`() {
    val intent = Intent("some.other.action")
    val result = mediaIntentHandler.handleMediaIntent(intent)
    assertThat(result).isFalse()
  }

  @Test
  fun `handleMediaIntent should return false for media button with no extras`() {
    val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
    val result = mediaIntentHandler.handleMediaIntent(intent)
    assertThat(result).isFalse()
  }

  @Test
  fun `handleMediaIntent should return false for ACTION_UP key event`() {
    val intent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.ACTION_UP)
    val result = mediaIntentHandler.handleMediaIntent(intent)
    assertThat(result).isFalse()
  }

  // endregion

  // region Playback control tests

  @Test
  fun `handleMediaIntent should handle KEYCODE_MEDIA_PLAY_PAUSE`() {
    val intent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)

    val result = mediaIntentHandler.handleMediaIntent(intent)

    assertThat(result).isTrue()
    verify { devicePlaybackController.playPause() }
  }

  @Test
  fun `handleMediaIntent should handle KEYCODE_MEDIA_PLAY`() {
    val intent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PLAY)

    val result = mediaIntentHandler.handleMediaIntent(intent)

    assertThat(result).isTrue()
    verify { devicePlaybackController.play() }
  }

  @Test
  fun `handleMediaIntent should handle KEYCODE_MEDIA_PAUSE`() {
    val intent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PAUSE)

    val result = mediaIntentHandler.handleMediaIntent(intent)

    assertThat(result).isTrue()
    verify { devicePlaybackController.pause() }
  }

  @Test
  fun `handleMediaIntent should handle KEYCODE_MEDIA_STOP`() {
    val intent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_STOP)

    val result = mediaIntentHandler.handleMediaIntent(intent)

    assertThat(result).isTrue()
    verify { devicePlaybackController.stop() }
  }

  @Test
  fun `handleMediaIntent should handle KEYCODE_MEDIA_NEXT`() {
    val intent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_NEXT)

    val result = mediaIntentHandler.handleMediaIntent(intent)

    assertThat(result).isTrue()
    verify { devicePlaybackController.next() }
  }

  @Test
  fun `handleMediaIntent should handle KEYCODE_MEDIA_PREVIOUS`() {
    val intent = createMediaButtonIntent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    val result = mediaIntentHandler.handleMediaIntent(intent)

    assertThat(result).isTrue()
    verify { devicePlaybackController.previous() }
  }

  // endregion

  // region Volume control tests

  @Test
  fun `handleMediaIntent should handle KEYCODE_VOLUME_UP`() {
    val intent = createMediaButtonIntent(KeyEvent.KEYCODE_VOLUME_UP)

    val result = mediaIntentHandler.handleMediaIntent(intent)

    assertThat(result).isTrue()
    verify { devicePlaybackController.adjustVolume(10) }
  }

  @Test
  fun `handleMediaIntent should handle KEYCODE_VOLUME_DOWN`() {
    val intent = createMediaButtonIntent(KeyEvent.KEYCODE_VOLUME_DOWN)

    val result = mediaIntentHandler.handleMediaIntent(intent)

    assertThat(result).isTrue()
    verify { devicePlaybackController.adjustVolume(-10) }
  }

  @Test
  fun `handleMediaIntent should handle KEYCODE_VOLUME_MUTE`() {
    val intent = createMediaButtonIntent(KeyEvent.KEYCODE_VOLUME_MUTE)

    val result = mediaIntentHandler.handleMediaIntent(intent)

    assertThat(result).isTrue()
    verify { devicePlaybackController.toggleMute() }
  }

  // endregion

  // region Headset hook / double-click tests

  @Test
  fun `handleMediaIntent should play-pause on single headset hook click`() {
    val intent = createMediaButtonIntent(KeyEvent.KEYCODE_HEADSETHOOK)

    val result = mediaIntentHandler.handleMediaIntent(intent)

    assertThat(result).isTrue()
    verify { devicePlaybackController.playPause() }
  }

  @Test
  fun `handleMediaIntent should skip to next on double headset hook click`() {
    // First click
    val intent1 = createMediaButtonIntent(KeyEvent.KEYCODE_HEADSETHOOK)
    mediaIntentHandler.handleMediaIntent(intent1)

    // Second click within 350ms (double-click)
    val intent2 = createMediaButtonIntent(KeyEvent.KEYCODE_HEADSETHOOK)
    val result = mediaIntentHandler.handleMediaIntent(intent2)

    assertThat(result).isTrue()

    verify(exactly = 1) { devicePlaybackController.playPause() }
    verify(exactly = 1) { devicePlaybackController.next() }
  }

  @Test
  fun `handleMediaIntent should treat slow clicks as separate single clicks`() {
    // Create a fresh handler and mock to test single click in isolation
    val freshDevicePlaybackController: LocalPlaybackController = mockk(relaxed = true)
    val freshHandler = MediaIntentHandler(freshDevicePlaybackController)

    // Single click on fresh handler (simulating click after timeout from previous)
    val intent = createMediaButtonIntent(KeyEvent.KEYCODE_HEADSETHOOK)
    val result = freshHandler.handleMediaIntent(intent)

    assertThat(result).isTrue()
    verify { freshDevicePlaybackController.playPause() }
    verify(exactly = 0) { freshDevicePlaybackController.next() }
  }

  // endregion

  // region Unknown key code tests

  @Test
  fun `handleMediaIntent should return false for unknown key code`() {
    val intent = createMediaButtonIntent(KeyEvent.KEYCODE_A)

    val result = mediaIntentHandler.handleMediaIntent(intent)

    assertThat(result).isFalse()
    verify(exactly = 0) { devicePlaybackController.playPause() }
    verify(exactly = 0) { devicePlaybackController.play() }
    verify(exactly = 0) { devicePlaybackController.pause() }
    verify(exactly = 0) { devicePlaybackController.stop() }
    verify(exactly = 0) { devicePlaybackController.next() }
    verify(exactly = 0) { devicePlaybackController.previous() }
  }

  @Test
  fun `handleMediaIntent should return false for KEYCODE_CAMERA`() {
    val intent = createMediaButtonIntent(KeyEvent.KEYCODE_CAMERA)

    val result = mediaIntentHandler.handleMediaIntent(intent)

    assertThat(result).isFalse()
  }

  // endregion

  // Helper function
  private fun createMediaButtonIntent(keyCode: Int, action: Int = KeyEvent.ACTION_DOWN): Intent {
    val keyEvent = KeyEvent(action, keyCode)
    return Intent(Intent.ACTION_MEDIA_BUTTON).apply {
      putExtra(Intent.EXTRA_KEY_EVENT, keyEvent)
    }
  }
}
