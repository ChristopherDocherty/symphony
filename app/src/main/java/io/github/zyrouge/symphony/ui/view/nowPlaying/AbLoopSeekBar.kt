package io.github.zyrouge.symphony.ui.view.nowPlaying

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.services.radio.AbLoopState
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.utils.DurationUtils
import kotlin.math.abs

@Composable
fun AbLoopSeekBar(context: ViewContext, state: AbLoopState, duration: Long) {
    val playbackPosition by context.symphony.radio.observatory.playbackPosition.collectAsState()
    val startMs by state.startMs.collectAsState()
    val endMs by state.endMs.collectAsState()
    val isActive by state.isActive.collectAsState()

    LaunchedEffect(playbackPosition.played) {
        if (isActive && duration > 0 && playbackPosition.played >= endMs) {
            context.symphony.radio.seek(startMs)
        }
    }

    Column(modifier = Modifier.padding(defaultHorizontalPadding, 0.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            AbLoopTimePicker(
                label = "A",
                valueMs = startMs,
                duration = duration,
                onValueChange = { newMs ->
                    state.startMs.value = newMs.coerceIn(0L, endMs - 100L)
                },
                modifier = Modifier.weight(1f),
            )
            AbLoopTimePicker(
                label = "B",
                valueMs = endMs,
                duration = duration,
                onValueChange = { newMs ->
                    state.endMs.value = newMs.coerceIn(startMs + 100L, duration)
                },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val textStyle = MaterialTheme.typography.labelMedium
            Text(DurationUtils.formatMs(playbackPosition.played), style = textStyle)
            Box(modifier = Modifier.weight(1f)) {
                AbLoopTrack(
                    state = state,
                    duration = duration,
                    playedMs = playbackPosition.played,
                )
            }
            Text(DurationUtils.formatMs(duration), style = textStyle)
        }
    }
}

@Composable
private fun AbLoopTimePicker(
    label: String,
    valueMs: Long,
    duration: Long,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxMinutes = (duration / 60000).toInt()
    val minutes = (valueMs / 60000).toInt()
    val seconds = ((valueMs / 1000) % 60).toInt()
    val tenths = ((valueMs / 100) % 10).toInt()

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            PickerColumn(
                value = minutes,
                range = 0..maxMinutes,
                formatItem = { it.toString() },
                onValueChange = { newMin ->
                    onValueChange((newMin * 60000L) + (seconds * 1000L) + (tenths * 100L))
                },
            )
            PickerSeparator(":")
            PickerColumn(
                value = seconds,
                range = 0..59,
                formatItem = { it.toString().padStart(2, '0') },
                onValueChange = { newSec ->
                    onValueChange((minutes * 60000L) + (newSec * 1000L) + (tenths * 100L))
                },
            )
            PickerSeparator(".")
            PickerColumn(
                value = tenths,
                range = 0..9,
                formatItem = { it.toString() },
                onValueChange = { newTenth ->
                    onValueChange((minutes * 60000L) + (seconds * 1000L) + (newTenth * 100L))
                },
            )
        }
    }
}

@Composable
private fun PickerSeparator(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = 2.dp),
    )
}

@Composable
private fun PickerColumn(
    value: Int,
    range: IntRange,
    formatItem: (Int) -> String,
    onValueChange: (Int) -> Unit,
) {
    val itemHeight = 36.dp
    val itemWidth = 40.dp
    val count = range.count()
    val initialIndex = (value - range.first).coerceIn(0, count - 1)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snapBehavior = rememberSnapFlingBehavior(listState)
    val centerIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val selected = range.first + listState.firstVisibleItemIndex
            if (selected != value) {
                onValueChange(selected)
            }
        }
    }

    LaunchedEffect(value) {
        val targetIndex = (value - range.first).coerceIn(0, count - 1)
        if (!listState.isScrollInProgress && listState.firstVisibleItemIndex != targetIndex) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    Box(
        modifier = Modifier.width(itemWidth),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(itemWidth)
                .height(itemHeight)
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    RoundedCornerShape(6.dp),
                )
        )
        LazyColumn(
            state = listState,
            flingBehavior = snapBehavior,
            modifier = Modifier.height(itemHeight * 3),
            contentPadding = PaddingValues(vertical = itemHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(count) { index ->
                val isSelected = index == centerIndex
                Box(
                    modifier = Modifier
                        .width(itemWidth)
                        .height(itemHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        formatItem(range.first + index),
                        style = if (isSelected) {
                            MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            MaterialTheme.typography.bodyMedium.copy(
                                color = LocalContentColor.current.copy(alpha = 0.4f),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AbLoopTrack(state: AbLoopState, duration: Long, playedMs: Long) {
    val startMs by state.startMs.collectAsState()
    val endMs by state.endMs.collectAsState()

    val startRatio = if (duration > 0) (startMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val endRatio = if (duration > 0) (endMs.toFloat() / duration).coerceIn(0f, 1f) else 1f
    val playedRatio = if (duration > 0) (playedMs.toFloat() / duration).coerceIn(0f, 1f) else 0f

    val sliderHeight = 24.dp
    val thumbSize = 16.dp
    val thumbSizeHalf = thumbSize / 2
    val trackHeight = 4.dp

    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    var activeThumb by remember { mutableStateOf<Int?>(null) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(sliderHeight),
        contentAlignment = Alignment.Center,
    ) {
        val sliderWidth = maxWidth

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(sliderHeight)
                .pointerInput(duration) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val aX = state.startMs.value.toFloat() / duration * sliderWidth.toPx()
                            val bX = state.endMs.value.toFloat() / duration * sliderWidth.toPx()
                            activeThumb = if (abs(offset.x - aX) <= abs(offset.x - bX)) 0 else 1
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val widthPx = sliderWidth.toPx()
                            if (duration > 0 && widthPx > 0) {
                                val deltaMs = (dragAmount.x / widthPx * duration.toFloat()).toLong()
                                when (activeThumb) {
                                    0 -> state.startMs.value =
                                        (state.startMs.value + deltaMs)
                                            .coerceIn(0L, state.endMs.value - 100L)
                                    1 -> state.endMs.value =
                                        (state.endMs.value + deltaMs)
                                            .coerceIn(state.startMs.value + 100L, duration)
                                }
                            }
                        },
                        onDragEnd = { activeThumb = null },
                        onDragCancel = { activeThumb = null },
                    )
                }
        )

        Canvas(
            modifier = Modifier
                .padding(horizontal = thumbSizeHalf)
                .height(trackHeight)
                .fillMaxWidth(),
        ) {
            val w = size.width
            val h = size.height
            val r = h / 2

            drawRoundRect(
                color = surfaceVariantColor,
                size = Size(w, h),
                cornerRadius = CornerRadius(r),
            )

            val regionStart = w * startRatio
            val regionWidth = w * (endRatio - startRatio)
            if (regionWidth > 0) {
                drawRect(
                    color = primaryColor.copy(alpha = 0.25f),
                    topLeft = Offset(regionStart, 0f),
                    size = Size(regionWidth, h),
                )
            }

            drawRoundRect(
                color = primaryColor,
                size = Size(w * playedRatio, h),
                cornerRadius = CornerRadius(r),
            )
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(thumbSize)
                    .offset(x = (sliderWidth - thumbSize) * startRatio, y = 0.dp)
                    .background(primaryColor, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(thumbSize)
                    .offset(x = (sliderWidth - thumbSize) * endRatio, y = 0.dp)
                    .background(secondaryColor, CircleShape)
            )
        }
    }
}
