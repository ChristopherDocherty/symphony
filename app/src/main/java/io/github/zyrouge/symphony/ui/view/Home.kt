package io.github.zyrouge.symphony.ui.view

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.SupervisorAccount
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.services.groove.Groove
import io.github.zyrouge.symphony.ui.components.IntroductoryDialog
import io.github.zyrouge.symphony.ui.components.NowPlayingBottomBar
import io.github.zyrouge.symphony.ui.components.TopAppBarMinimalTitle
import io.github.zyrouge.symphony.ui.components.swipeable
import io.github.zyrouge.symphony.ui.helpers.ScaleTransition
import io.github.zyrouge.symphony.ui.helpers.SlideTransition
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.home.AlbumArtistsView
import io.github.zyrouge.symphony.ui.view.home.AlbumsView
import io.github.zyrouge.symphony.ui.view.home.ArtistsView
import io.github.zyrouge.symphony.ui.view.home.BrowserView
import io.github.zyrouge.symphony.ui.view.home.FoldersView
import io.github.zyrouge.symphony.ui.view.home.ForYouView
import io.github.zyrouge.symphony.ui.view.home.GenresView
import io.github.zyrouge.symphony.ui.view.home.PlaylistsView
import io.github.zyrouge.symphony.ui.view.home.SongsView
import io.github.zyrouge.symphony.ui.view.home.TreeView
import kotlinx.serialization.Serializable

enum class HomePage(
    val kind: Groove.Kind? = null,
    val label: (context: ViewContext) -> String,
    val selectedIcon: @Composable () -> ImageVector,
    val unselectedIcon: @Composable () -> ImageVector,
) {
    ForYou(
        label = { it.symphony.t.ForYou },
        selectedIcon = { Icons.Filled.Face },
        unselectedIcon = { Icons.Outlined.Face }
    ),
    Songs(
        kind = Groove.Kind.SONG,
        label = { it.symphony.t.Songs },
        selectedIcon = { Icons.Filled.MusicNote },
        unselectedIcon = { Icons.Outlined.MusicNote }
    ),
    Artists(
        kind = Groove.Kind.ARTIST,
        label = { it.symphony.t.Artists },
        selectedIcon = { Icons.Filled.Group },
        unselectedIcon = { Icons.Outlined.Group }
    ),
    Albums(
        kind = Groove.Kind.ALBUM,
        label = { it.symphony.t.Albums },
        selectedIcon = { Icons.Filled.Album },
        unselectedIcon = { Icons.Outlined.Album }
    ),
    AlbumArtists(
        kind = Groove.Kind.ALBUM_ARTIST,
        label = { it.symphony.t.AlbumArtists },
        selectedIcon = { Icons.Filled.SupervisorAccount },
        unselectedIcon = { Icons.Outlined.SupervisorAccount }
    ),
    Genres(
        kind = Groove.Kind.GENRE,
        label = { it.symphony.t.Genres },
        selectedIcon = { Icons.Filled.Tune },
        unselectedIcon = { Icons.Outlined.Tune }
    ),
    Playlists(
        kind = Groove.Kind.PLAYLIST,
        label = { it.symphony.t.Playlists },
        selectedIcon = { Icons.AutoMirrored.Filled.QueueMusic },
        unselectedIcon = { Icons.AutoMirrored.Outlined.QueueMusic }
    ),
    Browser(
        label = { it.symphony.t.Browser },
        selectedIcon = { Icons.Filled.Folder },
        unselectedIcon = { Icons.Outlined.Folder }
    ),
    Folders(
        label = { it.symphony.t.Folders },
        selectedIcon = { Icons.Filled.FolderOpen },
        unselectedIcon = { Icons.Outlined.FolderOpen }
    ),
    Tree(
        label = { it.symphony.t.Tree },
        selectedIcon = { Icons.Filled.AccountTree },
        unselectedIcon = { Icons.Outlined.AccountTree }
    );
}

enum class HomePageBottomBarLabelVisibility {
    ALWAYS_VISIBLE,
    VISIBLE_WHEN_ACTIVE,
    INVISIBLE,
}

@Serializable
object HomeViewRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopAppBar(
    context: ViewContext,
    currentTab: HomePage,
    onSearchClick: () -> Unit,
    onMoreOptionsClick: () -> Unit, // Or manage dropdown state internally
) {
    var showOptionsDropdown by remember { mutableStateOf(false) } // Manage here or pass state

    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent
        ),
        navigationIcon = {
            IconButton(
                onClick = onSearchClick,
                content = { Icon(Icons.Filled.Search, null) }
            )
        },
        title = {
            Crossfade(
                label = "home-title",
                targetState = currentTab.label(context),
            ) { pageTitle ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    TopAppBarMinimalTitle { Text(pageTitle) }
                }
            }
        },
        actions = {
            IconButton(
                onClick = { showOptionsDropdown = !showOptionsDropdown },
                content = {
                    Icon(Icons.Filled.MoreVert, null)
                    HomeTopAppBarDropdownMenu(
                        context = context,
                        expanded = showOptionsDropdown,
                        onDismissRequest = { showOptionsDropdown = false },
                        onRescanClick = {
                            showOptionsDropdown = false
                            context.symphony.radio.stop()
                            context.symphony.groove.fetch(Groove.FetchOptions())
                        },
                        onSettingsClick = {
                            showOptionsDropdown = false
                            context.navController.navigate(SettingsViewRoute())
                        }
                    )
                }
            )
        }
    )
}

@Composable
private fun HomePageContent(
    modifier: Modifier = Modifier,
    context: ViewContext,
    currentPage: HomePage,
) {
    AnimatedContent(
        label = "home-content",
        targetState = currentPage,
        modifier = modifier,
        transitionSpec = {
            SlideTransition.slideUp.enterTransition()
                .togetherWith(ScaleTransition.scaleDown.exitTransition())
        },
    ) { page ->
        when (page) {
            HomePage.ForYou -> ForYouView(context)
            HomePage.Songs -> SongsView(context)
            HomePage.Albums -> AlbumsView(context)
            HomePage.Artists -> ArtistsView(context)
            HomePage.AlbumArtists -> AlbumArtistsView(context)
            HomePage.Genres -> GenresView(context)
            HomePage.Browser -> BrowserView(context)
            HomePage.Folders -> FoldersView(context)
            HomePage.Playlists -> PlaylistsView(context)
            HomePage.Tree -> TreeView(context)
        }
    }
}

@Composable
private fun HomeTopAppBarDropdownMenu(
    context: ViewContext,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onRescanClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        DropdownMenuItem(
            leadingIcon = {
                Icon(Icons.Filled.Refresh, context.symphony.t.Rescan)
            },
            text = { Text(context.symphony.t.Rescan) },
            onClick = onRescanClick
        )
        DropdownMenuItem(
            leadingIcon = {
                Icon(Icons.Filled.Settings, context.symphony.t.Settings)
            },
            text = { Text(context.symphony.t.Settings) },
            onClick = onSettingsClick
        )
    }
}

@OptIn(ExperimentalFoundationApi::class) // For detectTapGestures if still needed, or remove if swipeable is enough
@Composable
private fun HomeBottomBar(
    context: ViewContext,
    currentTab: HomePage,
    tabs: Set<HomePage>,
    labelVisibility: HomePageBottomBarLabelVisibility,
    onTabClick: (HomePage) -> Unit, // For setting the current tab
    onShowTabsSheet: () -> Unit, // To trigger the modal sheet
) {
    Column {
        NowPlayingBottomBar(context, false)
        NavigationBar(
            modifier = Modifier
                .pointerInput(Unit) { // Consider if this is still the best way to show tabs sheet
                    detectTapGestures {
                        onShowTabsSheet()
                    }
                }
                .swipeable(onSwipeUp = { // swipeable is likely from your custom components
                    onShowTabsSheet()
                })
        ) {
            Spacer(modifier = Modifier.width(2.dp))
            tabs.forEach { page -> // Use forEach for clarity if order doesn't change
                val isSelected = currentTab == page
                val label = page.label(context)

                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onTabClick(page) }, // Update current tab
                    icon = {
                        Crossfade(
                            label = "home-bottom-bar-icon-${page.name}", // More specific label
                            targetState = isSelected,
                        ) { selected ->
                            Icon(
                                if (selected) page.selectedIcon() else page.unselectedIcon(),
                                contentDescription = label
                            )
                        }
                    },
                    label = if (labelVisibility != HomePageBottomBarLabelVisibility.INVISIBLE) {
                        { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    } else null,
                    alwaysShowLabel = labelVisibility == HomePageBottomBarLabelVisibility.ALWAYS_VISIBLE,
                )
            }
            Spacer(modifier = Modifier.width(2.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeView(context: ViewContext) {
    val coroutineScope = rememberCoroutineScope()
    val readIntroductoryMessage by context.symphony.settingsOLD.readIntroductoryMessage.flow.collectAsState()
    val tabs by context.symphony.settingsOLD.homeTabs.flow.collectAsState()
    val labelVisibility by context.symphony.settingsOLD.homePageBottomBarLabelVisibility.flow.collectAsState()
    val currentTab by context.symphony.settingsOLD.lastHomeTab.flow.collectAsState()
    var showOptionsDropdown by remember { mutableStateOf(false) }
    var showTabsSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
                HomeTopAppBar(
                    context = context,
                    currentTab = currentTab, // or currentTabState
                    onSearchClick = {
                        context.navController.navigate(SearchViewRoute(currentTab.kind?.name)) // or currentTabState.kind
                    },
                    onMoreOptionsClick = { /* Logic to show dropdown or handle directly in HomeTopAppBar */ }
                )
        },
        content = { contentPadding ->
            HomePageContent(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize(),
                context = context,
                currentPage = currentTab // or currentTabState
            )
        },
        bottomBar = {
            HomeBottomBar(
                context = context,
                currentTab = currentTab, // or currentTabState
                tabs = tabs,
                labelVisibility = labelVisibility,
                onTabClick = { newTab ->
                    context.symphony.settingsOLD.lastHomeTab.setValue(newTab) // Persist the tab change
                },
                onShowTabsSheet = { showTabsSheet = true }
            )
        }
    )

    if (showTabsSheet) {
        val sheetState = rememberModalBottomSheetState()
        val orderedTabs = remember {
            setOf<HomePage>(*tabs.toTypedArray(), *HomePage.entries.toTypedArray())
        }

        ModalBottomSheet(
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = {
                showTabsSheet = false
            },
        ) {
            LazyVerticalGrid(
                modifier = Modifier.padding(6.dp),
                columns = GridCells.Fixed(tabs.size),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(orderedTabs.toList(), key = { it.ordinal }) { x ->
                    val isSelected = x == currentTab
                    val label = x.label(context)

                    val containerColor = when {
                        isSelected -> MaterialTheme.colorScheme.secondaryContainer
                        else -> Color.Unspecified
                    }
                    val contentColor = when {
                        isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> Color.Unspecified
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp, 0.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                context.symphony.settingsOLD.lastHomeTab.setValue(x)
                                showTabsSheet = false
                            }
                            .background(containerColor)
                            .padding(0.dp, 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when {
                            isSelected -> Icon(x.selectedIcon(), label, tint = contentColor)
                            else -> Icon(x.unselectedIcon(), label)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall.copy(color = contentColor),
                            modifier = Modifier.padding(8.dp, 0.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (!readIntroductoryMessage) {
        IntroductoryDialog(
            context,
            onDismissRequest = {
                context.symphony.settingsOLD.readIntroductoryMessage.setValue(true)
            },
        )
    }
}
