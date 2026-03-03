package io.github.zyrouge.symphony.ui.view.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.AlbumFilter
import io.github.zyrouge.symphony.AlbumSortBy
import io.github.zyrouge.symphony.ui.components.AlbumFilterDialog
import io.github.zyrouge.symphony.ui.components.LoaderScaffold
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.coroutines.flow.map

class AlbumTimelinePageState : HomePageState {
    var showFilterDialog by mutableStateOf(false)

    @Composable
    override fun DropdownItems() {
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Filled.FilterAlt, contentDescription = null) },
            text = { Text("Filter") },
            onClick = { showFilterDialog = true },
        )
    }

    @Composable
    override fun Dialogs(context: ViewContext) {
        if (showFilterDialog) {
            AlbumFilterDialog(
                context = context,
                onDismissRequest = { showFilterDialog = false },
            )
        }
    }
}

@Composable
fun AlbumTimelineView(context: ViewContext, pageState: AlbumTimelinePageState? = null) {
    val isUpdating by context.symphony.groove.album.isUpdating.collectAsState()
    val albumIds by context.symphony.groove.album.all.collectAsState()
    val albumFilter by context.symphony.settings.data
        .map { it.uiAlbumGridAlbumFilter }
        .collectAsState(AlbumFilter.getDefaultInstance())
    val hiddenAlbumIds by context.symphony.settings.data
        .map { it.hiddenAlbumIdsList.toSet() }
        .collectAsState(emptySet())
    val showHiddenAlbums by context.symphony.settings.data
        .map { it.showHiddenAlbums }
        .collectAsState(false)

    val filteredIds by remember(albumIds, albumFilter, hiddenAlbumIds, showHiddenAlbums) {
        derivedStateOf {
            context.symphony.groove.album.getAlbums(
                albumIds = albumIds,
                by = AlbumSortBy.ALBUM_NAME,
                reverse = false,
                filter = albumFilter,
                hiddenAlbumIds = hiddenAlbumIds,
                showHidden = showHiddenAlbums,
            )
        }
    }

    val yearData by remember(filteredIds) {
        derivedStateOf {
            val albums = filteredIds.mapNotNull { context.symphony.groove.album.get(it) }
            val noYearCount = albums.count { it.startYear == null }
            val countByYear = albums
                .filter { it.startYear != null }
                .groupBy { it.startYear!! }
                .mapValues { (_, list) -> list.size }
            val yearGroups = if (countByYear.isEmpty()) emptyList()
            else {
                val minYear = countByYear.keys.min()
                val maxYear = countByYear.keys.max()
                (minYear..maxYear).map { year -> year to (countByYear[year] ?: 0) }
            }
            Pair(yearGroups, noYearCount)
        }
    }

    val (yearGroups, noYearCount) = yearData

    LoaderScaffold(context, isLoading = isUpdating) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Overhead = column vertical padding (32dp) + year label + spacer (~20dp)
            // + "no year" row if present (~32dp)
            val overhead = 52.dp + if (noYearCount > 0) 32.dp else 0.dp
            val minChartHeight = 160.dp
            val chartHeight = (maxHeight - overhead).coerceAtLeast(minChartHeight)
            val needsScroll = maxHeight < (minChartHeight + overhead)

            Column(
                modifier = if (needsScroll)
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 16.dp)
                else
                    Modifier.fillMaxSize().padding(vertical = 16.dp),
            ) {
                if (noYearCount > 0) {
                    Text(
                        text = "No year: $noYearCount album${if (noYearCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp),
                    )
                }
                if (yearGroups.isNotEmpty()) {
                    AlbumYearChart(yearGroups = yearGroups, chartHeight = chartHeight)
                }
            }
        }
    }
}

@Composable
private fun AlbumYearChart(yearGroups: List<Pair<Int, Int>>, chartHeight: Dp) {
    val barWidth = 44.dp
    val barGap = 6.dp
    val maxCount = yearGroups.maxOf { it.second }
    val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val barColor = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .drawBehind {
                    listOf(0.25f, 0.5f, 0.75f, 1.0f).forEach { fraction ->
                        val y = size.height * (1f - fraction)
                        drawLine(
                            color = gridLineColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                },
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(barGap),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(yearGroups) { (year, count) ->
                Column(
                    modifier = Modifier.width(barWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .height(chartHeight)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        if (count > 0) {
                            val fraction = count.toFloat() / maxCount
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(fraction),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = "$count",
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center,
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.85f)
                                        .weight(1f)
                                        .background(
                                            barColor,
                                            RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                                        )
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$year",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
