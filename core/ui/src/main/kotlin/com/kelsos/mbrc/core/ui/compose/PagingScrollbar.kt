package com.kelsos.mbrc.core.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

enum class PagingScrollbarStyle {
  Standard,
  Indexed
}

@Stable
class PagingScrollbarState internal constructor() {
  internal var trackHeightPx by mutableFloatStateOf(0f)
  internal var isDragging by mutableStateOf(false)
  internal var draggedIndex by mutableIntStateOf(-1)
}

@Composable
internal fun rememberPagingScrollbarState(): PagingScrollbarState = remember {
  PagingScrollbarState()
}

/**
 * Draws a scrollbar for a paging list's placeholder-backed item count. Pair it with
 * [pagingScrollbarDragGesture] on the containing list host so taps remain clickable underneath.
 */
@Composable
fun PagingScrollbar(
  totalItems: Int,
  firstVisibleItemIndex: Int,
  visibleItemsCount: Int,
  style: PagingScrollbarStyle,
  labelForIndex: (Int) -> String?,
  state: PagingScrollbarState? = null,
  modifier: Modifier = Modifier
) {
  val scrollbarState = state ?: rememberPagingScrollbarState()
  if (totalItems <= visibleItemsCount || totalItems <= 1) return

  val density = LocalDensity.current
  val trackHeightPx = scrollbarState.trackHeightPx
  val isDragging = scrollbarState.isDragging
  val draggedIndex = scrollbarState.draggedIndex

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
  val maxScrollIndex = (totalItems - visibleItemsCount.coerceAtLeast(1)).coerceAtLeast(0)
  val currentIndex = if (isDragging && draggedIndex >= 0) {
    draggedIndex
  } else {
    firstVisibleItemIndex.coerceIn(0, maxScrollIndex)
  }
  val scrollFraction = if (maxScrollIndex > 0) {
    currentIndex.toFloat() / maxScrollIndex
  } else {
    0f
  }
  val thumbTopPx = scrollableHeightPx * scrollFraction
  val thumbWidth by animateDpAsState(
    targetValue = if (isDragging) DraggedThumbWidth else ThumbWidth,
    label = "paging_scrollbar_width"
  )
  val thumbHeight = with(density) { thumbHeightPx.toDp() }
  val bubbleHalfHeightPx = with(density) { BubbleHalfHeight.toPx() }
  val bubbleTopPx = (thumbTopPx + thumbHeightPx / 2f - bubbleHalfHeightPx)
    .coerceIn(0f, (trackHeightPx - bubbleHalfHeightPx * 2f).coerceAtLeast(0f))

  Box(
    modifier = modifier
      .fillMaxHeight()
      .width(ScrollbarOverlayWidth)
      .onSizeChanged { scrollbarState.trackHeightPx = it.height.toFloat() }
  ) {
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

/**
 * Observes the list's initial pointer pass so taps continue to the list item below the scrollbar.
 * The pointer is consumed only after a vertical drag crosses touch slop, at which point the
 * scrollbar takes over and jumps through the paging list.
 */
@Composable
internal fun Modifier.pagingScrollbarDragGesture(
  enabled: Boolean,
  totalItems: Int,
  visibleItemsCount: Int,
  state: PagingScrollbarState,
  onIndexSelected: (Int) -> Unit
): Modifier {
  if (!enabled || totalItems <= visibleItemsCount || totalItems <= 1) return this

  val currentOnIndexSelected = rememberUpdatedState(onIndexSelected)
  val touchTargetWidthPx = with(LocalDensity.current) {
    PagingScrollbarTouchTargetWidth.toPx()
  }

  return pointerInput(totalItems, state) {
    awaitPointerEventScope {
      val slop = viewConfiguration.touchSlop
      while (true) {
        val down = awaitFirstDown(
          requireUnconsumed = false,
          pass = PointerEventPass.Initial
        )
        if (down.position.x < size.width - touchTargetWidthPx) continue

        fun selectIndex(positionY: Float) {
          val trackHeightPx = state.trackHeightPx
          if (trackHeightPx <= 0f) return
          val maxScrollIndex = (totalItems - visibleItemsCount.coerceAtLeast(1)).coerceAtLeast(0)
          val target = (positionY / trackHeightPx * maxScrollIndex)
            .roundToInt()
            .coerceIn(0, maxScrollIndex)
          if (target != state.draggedIndex) {
            state.draggedIndex = target
            currentOnIndexSelected.value(target)
          }
        }

        try {
          awaitScrollbarDrag(
            downId = down.id,
            slop = slop,
            onDragStart = {
              state.isDragging = true
              selectIndex(it)
            },
            onDrag = ::selectIndex
          )
        } finally {
          state.isDragging = false
          state.draggedIndex = -1
        }
      }
    }
  }
}

private suspend fun AwaitPointerEventScope.awaitScrollbarDrag(
  downId: PointerId,
  slop: Float,
  onDragStart: (Float) -> Unit,
  onDrag: (Float) -> Unit
) {
  var totalX = 0f
  var totalY = 0f

  while (true) {
    val event = awaitPointerEvent(PointerEventPass.Initial)
    val change = event.changes.firstOrNull { it.id == downId } ?: return
    if (!change.pressed) return

    val delta = change.positionChange()
    totalX += delta.x
    totalY += delta.y

    if (abs(totalY) > slop && abs(totalY) > abs(totalX)) {
      change.consume()
      onDragStart(change.position.y)

      while (true) {
        val dragEvent = awaitPointerEvent(PointerEventPass.Initial)
        val dragChange = dragEvent.changes.firstOrNull { it.id == downId } ?: return
        if (!dragChange.pressed) return
        dragChange.consume()
        onDrag(dragChange.position.y)
      }
    }

    if (abs(totalX) > slop && abs(totalX) > abs(totalY)) return
  }
}

private fun Modifier.offsetPx(y: Float): Modifier = this.then(
  Modifier.offset { IntOffset(x = 0, y = y.roundToInt()) }
)

private val ScrollbarOverlayWidth = 124.dp

/**
 * Right-edge region that arms scrollbar dragging. Taps in this region remain unconsumed.
 */
internal val PagingScrollbarTouchTargetWidth = 48.dp
private val ThumbWidth = 4.dp
private val DraggedThumbWidth = 14.dp
private val MinimumThumbHeight = 36.dp
private val ThumbEndPadding = 4.dp
private val BubbleWidth = 76.dp
private val BubbleEndPadding = 32.dp
private val BubbleHalfHeight = 24.dp
