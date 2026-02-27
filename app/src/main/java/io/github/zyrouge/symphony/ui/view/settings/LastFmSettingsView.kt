package io.github.zyrouge.symphony.ui.view.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.res.stringResource
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.ui.components.IconButtonPlaceholder
import io.github.zyrouge.symphony.ui.components.TopAppBarMinimalTitle
import io.github.zyrouge.symphony.ui.components.settings.SettingsLinkTile
import io.github.zyrouge.symphony.ui.components.settings.SettingsSideHeading
import io.github.zyrouge.symphony.ui.components.settings.SettingsTextInputTile
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
object LastFmSettingsViewRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LastFmSettingsView(context: ViewContext) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val settings by context.symphony.settingsState.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    TopAppBarMinimalTitle {
                        Text("${stringResource(R.string.Settings)} - ${stringResource(R.string.LastFm)}")
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
                    SettingsSideHeading(stringResource(R.string.LastFm))
                    SettingsTextInputTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.Key, null)
                        },
                        title = {
                            Text(stringResource(R.string.LastFmApiKey))
                        },
                        value = settings.lastFmApiKey,
                        onChange = { value ->
                            scope.launch {
                                context.symphony.settings.updateData { it.copy { lastFmApiKey = value } }
                            }
                        },
                    )
                    HorizontalDivider()
                    SettingsTextInputTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.Person, null)
                        },
                        title = {
                            Text(stringResource(R.string.LastFmUsername))
                        },
                        value = settings.lastFmUsername,
                        onChange = { value ->
                            scope.launch {
                                context.symphony.settings.updateData { it.copy { lastFmUsername = value } }
                            }
                        },
                    )
                    HorizontalDivider()
                    SettingsLinkTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.Link, null)
                        },
                        title = {
                            Text("Get an API key")
                        },
                        url = "https://www.last.fm/api/account/create",
                    )
                }
            }
        }
    )
}
