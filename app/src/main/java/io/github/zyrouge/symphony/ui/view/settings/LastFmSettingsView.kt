package io.github.zyrouge.symphony.ui.view.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.services.lastfm.LastFmScrobbler
import io.github.zyrouge.symphony.ui.components.IconButtonPlaceholder
import io.github.zyrouge.symphony.ui.components.TopAppBarMinimalTitle
import io.github.zyrouge.symphony.ui.components.settings.SettingsLinkTile
import io.github.zyrouge.symphony.ui.components.settings.SettingsSideHeading
import io.github.zyrouge.symphony.ui.components.settings.SettingsTextInputTile
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
object LastFmSettingsViewRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LastFmSettingsView(context: ViewContext) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val settings by context.symphony.settingsState.collectAsState()
    val uriHandler = LocalUriHandler.current

    var pendingToken by remember { mutableStateOf<String?>(null) }
    var showAuthDialog by remember { mutableStateOf(false) }

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
                            Icon(Icons.Filled.Lock, null)
                        },
                        title = {
                            Text(stringResource(R.string.LastFmApiSecret))
                        },
                        value = settings.lastFmApiSecret,
                        onChange = { value ->
                            scope.launch {
                                context.symphony.settings.updateData { it.copy { lastFmApiSecret = value } }
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
                    SettingsSideHeading(stringResource(R.string.LastFmAuthenticate))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val isAuthenticated = settings.lastFmSessionKey.isNotEmpty()
                        Text(
                            text = if (isAuthenticated) {
                                stringResource(R.string.LastFmAuthenticated)
                            } else {
                                stringResource(R.string.LastFmNotAuthenticated)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (isAuthenticated) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        context.symphony.settings.updateData { it.copy { lastFmSessionKey = "" } }
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.LastFmDisconnect))
                            }
                        } else {
                            Button(
                                onClick = {
                                    scope.launch {
                                        val token = withContext(Dispatchers.IO) {
                                            LastFmScrobbler.getToken(
                                                settings.lastFmApiKey,
                                                settings.lastFmApiSecret,
                                            )
                                        }
                                        if (token != null) {
                                            pendingToken = token
                                            uriHandler.openUri(
                                                LastFmScrobbler.buildAuthUrl(settings.lastFmApiKey, token)
                                            )
                                            showAuthDialog = true
                                        }
                                    }
                                },
                                enabled = settings.lastFmApiKey.isNotBlank() && settings.lastFmApiSecret.isNotBlank(),
                            ) {
                                Text(stringResource(R.string.LastFmAuthenticate))
                            }
                        }
                    }
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

    if (showAuthDialog) {
        AlertDialog(
            onDismissRequest = { showAuthDialog = false },
            title = { Text(stringResource(R.string.LastFmAuthenticate)) },
            text = { Text(stringResource(R.string.LastFmAuthInstructions)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val token = pendingToken ?: return@TextButton
                        scope.launch {
                            val sessionKey = withContext(Dispatchers.IO) {
                                LastFmScrobbler.getSession(
                                    settings.lastFmApiKey,
                                    settings.lastFmApiSecret,
                                    token,
                                )
                            }
                            if (sessionKey != null) {
                                context.symphony.settings.updateData { it.copy { lastFmSessionKey = sessionKey } }
                            }
                            pendingToken = null
                            showAuthDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.Done))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAuthDialog = false }) {
                    Text(stringResource(R.string.Cancel))
                }
            },
        )
    }
}
