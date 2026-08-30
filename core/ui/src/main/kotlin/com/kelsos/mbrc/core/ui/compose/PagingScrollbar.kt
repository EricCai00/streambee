package com.kelsos.mbrc.core.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

enum class PagingScrollbarStyle {
  Standard,
  Indexed
}

/**
 * A draggable scrollbar that maps directly onto a paging list's placeholder-backed item count.
 * This lets a user jump through very large libraries without loading every preceding page.
 */
@Composable
fun PagingScrollbar(
  totalItems: Int,
  firstVisibleItemIndex: Int,
  visibleItemsCount: Int,
  style: PagingScrollbarStyle,
  labelForIndex: (Int) -> String?,
  onIndexSelected: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  if (totalItems <= visibleItemsCount || totalItems <= 1) return

  val density = LocalDensity.current
  val currentOnIndexSelected by rememberUpdatedState(onIndexSelected)
  var trackHeightPx by remember { mutableFloatStateOf(0f) }
  var isDragging by remember { mutableStateOf(false) }
  var draggedIndex by remember { mutableIntStateOf(-1) }

  val minimumThumbHeightPx = with(density) { MinimumThumbHeight.toPx() }
  val proportionalHeight = if (totalItems > 0) {
    trackHeightPx * visibleItemsCount.coerceAtLeast(1) / totalItems
  } else {
    trackHeightPx
  }
  val thumbHeightPx = proportionalHeight
    .coerceAtLeast(minimumThumbHeightPx)
    .coerceAtMost(trackHeightPx)
  val scrollableHeightPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
  val currentIndex = if (isDragging && draggedIndex >= 0) {
    draggedIndex
  } else {
    firstVisibleItemIndex.coerceIn(0, totalItems - 1)
  }
  val scrollFraction = currentIndex.toFloat() / (totalItems - 1).coerceAtLeast(1)
  val thumbTopPx = scrollableHeightPx * scrollFraction
  val thumbWidth by animateDpAsState(
    targetValue = if (isDragging) DraggedThumbWidth else ThumbWidth,
    label = "paging_scrollbar_width"
  )
  val thumbHeight = with(density) { thumbHeightPx.toDp() }
  val bubbleHalfHeightPx = with(density) { BubbleHalfHeight.toPx() }
  val bubbleTopPx = (thumbTopPx + thumbHeightPx / 2f - bubbleHalfHeightPx)
    .coerceIn(0f, (trackHeightPx - bubbleHalfHeightPx * 2f).coerceAtLeast(0f))

  fun selectIndex(position: Offset) {
    if (trackHeightPx <= 0f) return
    val target = (position.y / trackHeightPx * totalItems)
      .toInt()
      .coerceIn(0, totalItems - 1)
    if (target != draggedIndex) {
      draggedIndex = target
      currentOnIndexSelected(target)
    }
  }

  Box(
    modifier = modifier
      .fillMaxHeight()
      .width(ScrollbarOverlayWidth)
      .onSizeChanged { trackHeightPx = it.height.toFloat() }
  ) {
    Box(
      modifier = Modifier
        .fillMaxHeight()
        .width(TouchTargetWidth)
        .align(Alignment.CenterEnd)
        .pointerInput(totalItems) {
          detectVerticalDragGestures(
            onDragStart = { position ->
              isDragging = true
              selectIndex(position)
            },
            onDragEnd = {
              isDragging = false
              draggedIndex = -1
            },
            onDragCancel = {
              isDragging = false
              draggedIndex = -1
            },
            onVerticalDrag = { change, _ ->
              change.consume()
              selectIndex(change.position)
            }
          )
        }
    )

    Box(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(end = ThumbEndPadding)
        .width(thumbWidth)
        .height(thumbHeight)
        .offsetPx(y = thumbTopPx)
        .clip(RoundedCornerShape(percent = 50))
        .background(
          MaterialTheme.colorScheme.onSurfaceVariant.copy(
            alpha = if (isDragging) 0.9f else 0.5f
          )
        )
    )

    AnimatedVisibility(
      visible = style == PagingScrollbarStyle.Indexed && isDragging,
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(end = BubbleEndPadding)
        .offsetPx(y = bubbleTopPx),
      enter = fadeIn() + scaleIn(initialScale = 0.72f),
      exit = fadeOut() + scaleOut(targetScale = 0.72f)
    ) {
      Surface(
        modifier = Modifier.width(BubbleWidth),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 6.dp
      ) {
        Text(
          text = labelForIndex(currentIndex).orEmpty().ifBlank { "…" },
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 9.dp),
          maxLines = 1,
          textAlign = TextAlign.Center,
          style = MaterialTheme.typography.titleLarge
        )
      }
    }
  }
}

private fun Modifier.offsetPx(y: Float): Modifier = this.then(
  Modifier.offset { IntOffset(x = 0, y = y.roundToInt()) }
)

private val ScrollbarOverlayWidth = 124.dp
private val TouchTargetWidth = 48.dp
private val ThumbWidth = 4.dp
private val DraggedThumbWidth = 14.dp
private val MinimumThumbHeight = 36.dp
private val ThumbEndPadding = 4.dp
private val BubbleWidth = 76.dp
private val BubbleEndPadding = 32.dp
private val BubbleHalfHeight = 24.dp
