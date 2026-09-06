package com.kelsos.mbrc.core.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TrackNavigationMenuItemsTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `shows album artist and genre actions`() {
    var selectedAction = ""
    composeTestRule.setContent {
      MaterialTheme {
        Column {
          TrackNavigationMenuItems(
            onGoToAlbum = { selectedAction = "album" },
            onGoToArtist = { selectedAction = "artist" },
            onGoToGenre = { selectedAction = "genre" }
          )
        }
      }
    }

    composeTestRule.onNodeWithText("Go to album").assertIsDisplayed().performClick()
    assertThat(selectedAction).isEqualTo("album")
    composeTestRule.onNodeWithText("Go to artist").assertIsDisplayed().performClick()
    assertThat(selectedAction).isEqualTo("artist")
    composeTestRule.onNodeWithText("Go to genre").assertIsDisplayed().performClick()
    assertThat(selectedAction).isEqualTo("genre")
  }

  @Test
  fun `keeps unavailable destinations visible but disabled`() {
    composeTestRule.setContent {
      MaterialTheme {
        Column {
          TrackNavigationMenuItems(
            onGoToAlbum = null,
            onGoToArtist = null,
            onGoToGenre = null
          )
        }
      }
    }

    composeTestRule.onNodeWithText("Go to album").assertIsNotEnabled()
    composeTestRule.onNodeWithText("Go to artist").assertIsNotEnabled()
    composeTestRule.onNodeWithText("Go to genre").assertIsNotEnabled()
  }
}
