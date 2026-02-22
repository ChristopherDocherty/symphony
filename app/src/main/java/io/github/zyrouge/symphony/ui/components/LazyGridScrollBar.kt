package io.github.zyrouge.symphony.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import io.github.zyrouge.symphony.utils.toSafeFinite
import kotlin.math.ceil

fun Modifier.drawScrollBar(state: LazyGridState, columns: Int): Modifier = composed {
    val scrollPointerColor = MaterialTheme.colorScheme.surfaceTint
    val showScrollPointer by remember {
        derivedStateOf {
            !(state.firstVisibleItemIndex == 0 && state.layoutInfo.visibleItemsInfo.lastOrNull()?.index == state.layoutInfo.totalItemsCount - 1)
        }
    }
    val showScrollPointerColorAnimated by animateColorAsState(
        scrollPointerColor.copy(alpha = if (showScrollPointer) 1f else 0f),
        animationSpec = tween(durationMillis = 500),
        label = "c-lazy-grid-scroll-pointer-color",
    )

    drawWithContent {
        drawContent()
        val visibleItems = state.layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return@drawWithContent
        val thumbHeight = ContentDrawScopeScrollBarDefaults.scrollPointerHeight.toPx()
        val scrollBarHeight = size.height - thumbHeight
        val avgRowHeight = visibleItems.sumOf { it.size.height }.toFloat() / visibleItems.size
        val totalRows = ceil(state.layoutInfo.totalItemsCount.toFloat() / columns)
        val totalContentHeight = totalRows * avgRowHeight
        val maxScrollOffset = (totalContentHeight - size.height).coerceAtLeast(1f)
        val firstVisibleRow = state.firstVisibleItemIndex / columns
        val currentScrollOffset = firstVisibleRow * avgRowHeight + state.firstVisibleItemScrollOffset
        val offsetY = (scrollBarHeight * currentScrollOffset / maxScrollOffset)
            .coerceIn(0f, scrollBarHeight)
            .toSafeFinite()
        drawScrollBar(
            scrollPointerColor = showScrollPointerColorAnimated,
            scrollPointerOffsetY = offsetY,
        )
    }
}
