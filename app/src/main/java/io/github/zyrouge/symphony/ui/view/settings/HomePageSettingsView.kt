package io.github.zyrouge.symphony.ui.view.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Recommend
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.zyrouge.symphony.HomePageBottomBarLabelVisibility
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.ui.components.IconButtonPlaceholder
import io.github.zyrouge.symphony.ui.components.TopAppBarMinimalTitle
import io.github.zyrouge.symphony.ui.components.settings.SettingsMultiOptionTile
import io.github.zyrouge.symphony.ui.components.settings.SettingsOptionTile
import io.github.zyrouge.symphony.ui.components.settings.SettingsSideHeading
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.HomePage
import io.github.zyrouge.symphony.ui.view.home.ForYou
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import androidx.compose.ui.res.stringResource
import io.github.zyrouge.symphony.R

@Serializable
object HomePageSettingsViewRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePageSettingsView(context: ViewContext) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val settings by context.symphony.settingsState.collectAsState()

    val homeTabs = settings.homeTabsList
        .mapNotNull { runCatching { HomePage.valueOf(it) }.getOrNull() }
        .toSet()
    val forYouContents = settings.forYouContentsList
        .mapNotNull { runCatching { ForYou.valueOf(it) }.getOrNull() }
        .toSet()
    val homePageBottomBarLabelVisibility = settings.homePageBottomBarLabelVisibility

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    TopAppBarMinimalTitle {
                        Text("${stringResource(R.string.Settings)} - ${stringResource(R.string.Home)}")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                ),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            context.navController.popBackStack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButtonPlaceholder()
                },
            )
        },
        content = { contentPadding ->
            Box(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
            ) {
                Column(modifier = Modifier.verticalScroll(scrollState)) {
                    SettingsSideHeading(stringResource(R.string.Home))
                    SettingsMultiOptionTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.Home, null)
                        },
                        title = {
                            Text(stringResource(R.string.HomeTabs))
                        },
                        note = {
                            Text(stringResource(R.string.SelectAtleast2orAtmost5Tabs))
                        },
                        value = homeTabs,
                        values = HomePage.entries.associateWith { it.label(context) },
                        satisfies = { it.size in 2..5 },
                        onChange = { value ->
                            scope.launch {
                                context.symphony.settings.updateData {
                                    it.copy {
                                        this.homeTabs.clear()
                                        this.homeTabs.addAll(value.map { tab -> tab.name })
                                    }
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                    SettingsMultiOptionTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.Recommend, null)
                        },
                        title = {
                            Text(stringResource(R.string.ForYou))
                        },
                        value = forYouContents,
                        values = ForYou.entries.associateWith { it.label(context) },
                        onChange = { value ->
                            scope.launch {
                                context.symphony.settings.updateData {
                                    it.copy {
                                        this.forYouContents.clear()
                                        this.forYouContents.addAll(value.map { item -> item.name })
                                    }
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                    SettingsOptionTile(
                        icon = {
                            Icon(Icons.AutoMirrored.Filled.Label, null)
                        },
                        title = {
                            Text(stringResource(R.string.BottomBarLabelVisibility))
                        },
                        value = homePageBottomBarLabelVisibility,
                        values = HomePageBottomBarLabelVisibility.entries
                            .filter { it != HomePageBottomBarLabelVisibility.UNRECOGNIZED }
                            .associateWith { it.label(context) },
                        onChange = { value ->
                            scope.launch {
                                context.symphony.settings.updateData {
                                    it.copy { this.homePageBottomBarLabelVisibility = value }
                                }
                            }
                        }
                    )
                }
            }
        }
    )
}

fun HomePageBottomBarLabelVisibility.label(context: ViewContext) = when (this) {
    HomePageBottomBarLabelVisibility.BOTTOM_BAR_ALWAYS_VISIBLE -> context.activity.getString(R.string.AlwaysVisible)
    HomePageBottomBarLabelVisibility.BOTTOM_BAR_VISIBLE_WHEN_ACTIVE -> context.activity.getString(R.string.VisibleWhenActive)
    HomePageBottomBarLabelVisibility.BOTTOM_BAR_INVISIBLE -> context.activity.getString(R.string.Invisible)
    HomePageBottomBarLabelVisibility.UNRECOGNIZED -> "???"
}
