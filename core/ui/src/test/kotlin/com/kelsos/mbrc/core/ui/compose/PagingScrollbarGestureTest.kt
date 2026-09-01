package com.kelsos.mbrc.core.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PagingScrollbarGestureTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun tapInScrollbarGutterReachesTheUnderlyingListItem() {
    var clickCount = 0
    var selectedIndices = 0
    mount(
      onClick = { clickCount += 1 },
      onIndexSelected = { selectedIndices += 1 }
    )

    composeTestRule.onNode(hasClickAction()).performTouchInput {
      click(Offset(x = right - 8f, y = center.y))
    }
    composeTestRule.waitForIdle()

    assertThat(clickCount).isEqualTo(1)
    assertThat(selectedIndices).isEqualTo(0)
  }

  @Test
  fun verticalDragInScrollbarGutterTakesOverAfterTouchSlop() {
    var clickCount = 0
    var selectedIndices = 0
    mount(
      onClick = { clickCount += 1 },
      onIndexSelected = { selectedIndices += 1 }
    )

    composeTestRule.onNode(hasClickAction()).performTouchInput {
      down(Offset(x = right - 8f, y = height / 4f))
      moveBy(Offset(x = 0f, y = height / 2f))
      up()
    }
    composeTestRule.waitForIdle()

    assertThat(clickCount).isEqualTo(0)
    assertThat(selectedIndices).isGreaterThan(0)
  }

  @Test
  fun draggingToTheBottomSelectsTheLastScrollableIndex() {
    var lastSelectedIndex = -1
    mount(
      onClick = {},
      onIndexSelected = { lastSelectedIndex = it }
    )

    composeTestRule.onNode(hasClickAction()).performTouchInput {
      down(Offset(x = right - 8f, y = height - 20f))
      moveTo(Offset(x = right - 8f, y = height - 1f))
      up()
    }
    composeTestRule.waitForIdle()

    assertThat(lastSelectedIndex).isEqualTo(90)
  }

  private fun mount(onClick: () -> Unit, onIndexSelected: (Int) -> Unit) {
    val state = PagingScrollbarState()
    composeTestRule.setContent {
      Box(
        modifier = Modifier
          .size(200.dp)
          .onSizeChanged { state.trackHeightPx = it.height.toFloat() }
          .pagingScrollbarDragGesture(
            enabled = true,
            totalItems = 100,
            visibleItemsCount = 10,
            state = state,
            onIndexSelected = onIndexSelected
          )
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onClick)
        )
      }
    }
    composeTestRule.waitForIdle()
  }
}
