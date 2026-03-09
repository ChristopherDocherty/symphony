package io.github.zyrouge.symphony.ui.view.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import coil.compose.AsyncImage
import io.github.zyrouge.symphony.AlbumFilter
import io.github.zyrouge.symphony.AlbumSortBy
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.ui.components.AlbumFilterDialog
import io.github.zyrouge.symphony.ui.components.AlbumFilterField
import io.github.zyrouge.symphony.ui.components.AlbumGridType
import io.github.zyrouge.symphony.ui.components.LoaderScaffold
import io.github.zyrouge.symphony.ui.components.MediaSortBar
import io.github.zyrouge.symphony.ui.components.MediaSortBarScaffold
import io.github.zyrouge.symphony.ui.components.getLastUsedReverse
import io.github.zyrouge.symphony.ui.components.getLastUsedSortBy
import io.github.zyrouge.symphony.ui.components.label
import io.github.zyrouge.symphony.ui.components.setLastUsedReverse
import io.github.zyrouge.symphony.ui.components.setLastUsedSortBy
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.AlbumViewRoute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class CoverFlowPageState : HomePageState {
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
                filterField = AlbumFilterField.CoverFlow,
                onDismissRequest = { showFilterDialog = false },
            )
        }
    }
}

@Composable
fun CoverFlowView(context: ViewContext, pageState: CoverFlowPageState? = null) {
    val isUpdating by context.symphony.groove.album.isUpdating.collectAsState()
    val allAlbumIds by context.symphony.groove.album.all.collectAsState()
    val sortBy by AlbumGridType.CoverFlow.getLastUsedSortBy(context).collectAsState(AlbumSortBy.ALBUM_NAME)
    val sortReverse by AlbumGridType.CoverFlow.getLastUsedReverse(context).collectAsState(false)
    val albumFilter by context.symphony.settings.data
        .map { it.uiCoverFlowAlbumFilter }
        .collectAsState(AlbumFilter.getDefaultInstance())
    val hiddenAlbumIds by context.symphony.settings.data
        .map { it.hiddenAlbumIdsList.toSet() }
        .collectAsState(emptySet())
    val showHiddenAlbums by context.symphony.settings.data
        .map { it.showHiddenAlbums }
        .collectAsState(false)
    val albumIds by remember(allAlbumIds, sortBy, sortReverse, albumFilter, hiddenAlbumIds, showHiddenAlbums) {
        derivedStateOf {
            context.symphony.groove.album.getAlbums(allAlbumIds, sortBy, sortReverse, albumFilter, hiddenAlbumIds, showHiddenAlbums)
        }
    }
    val coroutineScope = rememberCoroutineScope()
    var showFilterDialog by remember { mutableStateOf(false) }

    MediaSortBarScaffold(
        mediaSortBar = {
            MediaSortBar(
                context,
                reverse = sortReverse,
                onReverseChange = {
                    coroutineScope.launch { AlbumGridType.CoverFlow.setLastUsedReverse(context, it) }
                },
                sort = sortBy,
                sorts = AlbumSortBy.entries
                    .filter { it != AlbumSortBy.UNRECOGNIZED }
                    .associateWith { x -> ViewContext.parameterizedFn { x.label(it) } },
                onSortChange = {
                    coroutineScope.launch { AlbumGridType.CoverFlow.setLastUsedSortBy(context, it) }
                },
                label = {
                    Text(stringResource(R.string.XAlbums, albumIds.size.toString()))
                },
                onShowFilterDialog = { showFilterDialog = true },
            )
        },
        content = {
            LoaderScaffold(context, isLoading = isUpdating) {
                if (albumIds.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.CoverFlowEmpty))
                    }
                    return@LoaderScaffold
                }

                val maxPage = albumIds.size - 1
                val currentPage = remember { Animatable(0f) }
                var restoredPosition by remember { mutableStateOf(false) }

                LaunchedEffect(maxPage) {
                    currentPage.updateBounds(0f, maxPage.toFloat())
                }

                val settledPage by remember {
                    derivedStateOf {
                        val mp = (albumIds.size - 1).coerceAtLeast(0)
                        currentPage.value.roundToInt().coerceIn(0, mp)
                    }
                }
                val currentAlbum by remember {
                    derivedStateOf { context.symphony.groove.album.get(albumIds[settledPage]) }
                }
                // Recompute composed range when the integer position changes, not every frame
                val composedRange by remember {
                    derivedStateOf {
                        val cp = currentPage.value.roundToInt()
                        val mp = (albumIds.size - 1).coerceAtLeast(0)
                        (cp - 5).coerceAtLeast(0)..(cp + 5).coerceAtMost(mp)
                    }
                }

                // Restore the last-viewed album once when the list first loads.
                // Read directly from DataStore (not collectAsState) to avoid a race where
                // albumIds arrives from the Room cache before DataStore emits its first value.
                LaunchedEffect(albumIds) {
                    if (!restoredPosition && albumIds.isNotEmpty()) {
                        restoredPosition = true
                        val savedId = context.symphony.settings.data
                            .map { it.uiCoverFlowLastAlbumId }
                            .first()
                        if (savedId.isNotEmpty()) {
                            val idx = albumIds.indexOfFirst { it.toString() == savedId }
                            if (idx >= 0) currentPage.snapTo(idx.toFloat())
                        }
                    }
                }

                // Persist the centred album whenever the settled page changes.
                LaunchedEffect(settledPage) {
                    if (restoredPosition && albumIds.isNotEmpty()) {
                        val id = albumIds[settledPage]
                        context.symphony.settings.updateData {
                            it.copy { uiCoverFlowLastAlbumId = id.toString() }
                        }
                    }
                }

                val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        val localDensity = LocalDensity.current
                        val albumSizeDp = if (isLandscape)
                            maxHeight * 0.85f
                        else
                            minOf(maxWidth, maxHeight) * 0.55f
                        val albumSizePx = with(localDensity) { albumSizeDp.toPx() }
                        // Max rotation 78° — steeper than before so side albums are visually
                        // thinner (cos 78° ≈ 0.21) and more can fit on screen simultaneously.
                        // Center-to-first-side distance: settled minimum is albumSizePx*0.583;
                        // using 0.66 leaves a slim visible gap between center and first side album.
                        val sideXPx = albumSizePx * 0.66f
                        // Spacing between adjacent fully-rotated side albums.
                        // Settled minimum is albumSizePx*0.166; using 0.18 leaves a slim gap.
                        val sideSpacingPx = albumSizePx * 0.18f

                        // Returns the x offset for a continuous album position, interpolating
                        // between settled fan positions so albums never phase through each other.
                        // A power curve is applied for the center→side segment (n==0) to make
                        // the center album fan out aggressively from the very start of a swipe.
                        fun xOffsetFor(off: Float): Float {
                            val sign = if (off >= 0f) 1f else -1f
                            val absOff = abs(off)
                            val n = absOff.toInt()
                            val frac = absOff - n
                            val x0 = if (n == 0) 0f else sideXPx + (n - 1) * sideSpacingPx
                            val x1 = sideXPx + n * sideSpacingPx
                            val interpFrac = if (n == 0) Math.pow(frac.toDouble(), 0.7).toFloat() else frac
                            return sign * lerp(x0, x1, interpFrac)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .draggable(
                                    orientation = Orientation.Horizontal,
                                    state = rememberDraggableState { delta ->
                                        coroutineScope.launch {
                                            val newVal = (currentPage.value - delta / (albumSizePx * 0.55f))
                                                .coerceIn(0f, maxPage.toFloat())
                                            currentPage.snapTo(newVal)
                                        }
                                    },
                                    onDragStopped = { velocity ->
                                        coroutineScope.launch {
                                            // Convert pixel velocity → page velocity, then coast to a stop
                                            val pageVelocity = -velocity / (albumSizePx * 0.55f)
                                            currentPage.animateDecay(
                                                initialVelocity = pageVelocity,
                                                animationSpec = exponentialDecay(frictionMultiplier = 1.8f),
                                            )
                                            // Snap to nearest integer page after momentum settles
                                            val target = currentPage.value.roundToInt().coerceIn(0, maxPage)
                                            currentPage.animateTo(
                                                target.toFloat(),
                                                spring(dampingRatio = 0.8f, stiffness = 400f),
                                            )
                                        }
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            for (index in composedRange) {
                                val albumId = albumIds[index]
                                val album = context.symphony.groove.album.get(albumId)
                                // z-index based on settled page so the tree stays stable between frames
                                val distFromSettled = abs(index - settledPage)

                                Box(
                                    modifier = Modifier
                                        .size(albumSizeDp)
                                        // zIndex controls draw order: center album on top
                                        .zIndex(-distFromSettled.toFloat())
                                        // offset reads state lazily (layout phase, no recomposition)
                                        .offset {
                                            val off = index.toFloat() - currentPage.value
                                            IntOffset(xOffsetFor(off).roundToInt(), 0)
                                        }
                                        // graphicsLayer reads state lazily (draw phase, no recomposition)
                                        .graphicsLayer {
                                            val off = index.toFloat() - currentPage.value
                                            val absOff = abs(off).coerceIn(0f, 1f)
                                            // Power curve makes rotation ramp up aggressively from
                                            // center so albums clear each other faster during swipe.
                                            // Clamped at ±78° for albums beyond ±1 position.
                                            val rotFrac = Math.pow(absOff.toDouble(), 0.7).toFloat()
                                            rotationY = (if (off >= 0f) -1f else 1f) * rotFrac * 78f
                                            scaleX = lerp(0.8f, 1f, 1f - absOff)
                                            scaleY = lerp(0.8f, 1f, 1f - absOff)
                                            alpha = lerp(0.5f, 1f, 1f - absOff)
                                            cameraDistance = 12f * density
                                        }
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (settledPage == index) {
                                                context.navController.navigate(AlbumViewRoute(albumId))
                                            } else {
                                                coroutineScope.launch {
                                                    currentPage.animateTo(index.toFloat(), tween(300))
                                                }
                                            }
                                        },
                                ) {
                                    AsyncImage(
                                        model = album?.createArtworkImageRequest(context.symphony)?.build(),
                                        contentDescription = album?.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }

                    if (!isLandscape) {
                        Spacer(modifier = Modifier.height(24.dp))

                        currentAlbum?.let { album ->
                            Text(
                                text = album.name,
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 32.dp),
                            )
                            val artist = album.albumArtists.firstOrNull() ?: album.artists.firstOrNull()
                            if (artist != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 32.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }

            if (showFilterDialog) {
                AlbumFilterDialog(
                    context = context,
                    filterField = AlbumFilterField.CoverFlow,
                    onDismissRequest = { showFilterDialog = false },
                )
            }
        }
    )
}
