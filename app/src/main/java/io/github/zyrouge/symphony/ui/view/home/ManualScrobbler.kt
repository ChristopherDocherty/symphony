package io.github.zyrouge.symphony.ui.view.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.services.lastfm.LastFmRecentTrack
import io.github.zyrouge.symphony.services.lastfm.LastFmScrobbler
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ManualScrobblerView(context: ViewContext) {
    val scope = rememberCoroutineScope()
    val settings by context.symphony.settingsState.collectAsState()
    val sessionKey = settings.lastFmSessionKey
    val apiKey = settings.lastFmApiKey
    val apiSecret = settings.lastFmApiSecret
    val username = settings.lastFmUsername

    var artistInput by remember { mutableStateOf("") }
    var trackInput by remember { mutableStateOf("") }
    var albumInput by remember { mutableStateOf("") }
    var timestampInput by remember { mutableStateOf(formatTimestamp(System.currentTimeMillis() / 1000)) }
    var isScrobbling by remember { mutableStateOf(false) }

    var isLoadingRecent by remember { mutableStateOf(false) }
    var recentTracks by remember { mutableStateOf<List<LastFmRecentTrack>?>(null) }
    var recentError by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    fun loadRecentTracks() {
        if (apiKey.isBlank() || username.isBlank()) return
        scope.launch {
            isLoadingRecent = true
            recentError = false
            val tracks = withContext(Dispatchers.IO) {
                LastFmScrobbler.getRecentTracks(apiKey, username)
            }
            if (tracks == null) {
                recentError = true
            } else {
                recentTracks = tracks.filter { it.timestampSeconds > 0 }
            }
            isLoadingRecent = false
        }
    }

    LaunchedEffect(Unit) {
        loadRecentTracks()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (sessionKey.isBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.LastFmAuthenticateFirst),
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = artistInput,
                    onValueChange = { artistInput = it },
                    label = { Text(stringResource(R.string.Artist)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            item {
                OutlinedTextField(
                    value = trackInput,
                    onValueChange = { trackInput = it },
                    label = { Text(stringResource(R.string.TrackName)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            item {
                OutlinedTextField(
                    value = albumInput,
                    onValueChange = { albumInput = it },
                    label = { Text(stringResource(R.string.Album)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            item {
                OutlinedTextField(
                    value = timestampInput,
                    onValueChange = { timestampInput = it },
                    label = { Text(stringResource(R.string.Timestamp)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            item {
                Button(
                    onClick = {
                        val ts = parseTimestamp(timestampInput) ?: (System.currentTimeMillis() / 1000)
                        scope.launch {
                            isScrobbling = true
                            val success = withContext(Dispatchers.IO) {
                                LastFmScrobbler.scrobble(
                                    apiKey = apiKey,
                                    apiSecret = apiSecret,
                                    sessionKey = sessionKey,
                                    artist = artistInput,
                                    track = trackInput,
                                    album = albumInput.takeIf { it.isNotBlank() },
                                    timestampSeconds = ts,
                                )
                            }
                            snackbarMessage = if (success) {
                                context.activity.getString(R.string.ScrobbleSuccess)
                            } else {
                                context.activity.getString(R.string.ScrobbleFailed)
                            }
                            isScrobbling = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = artistInput.isNotBlank() && trackInput.isNotBlank() && sessionKey.isNotBlank() && !isScrobbling,
                ) {
                    Text(stringResource(R.string.ScrobbleTrack))
                }
            }

            item {
                HorizontalDivider()
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.RecentScrobbles),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { loadRecentTracks() }) {
                        Icon(Icons.Filled.Refresh, null)
                    }
                }
            }

            when {
                isLoadingRecent -> item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                recentError -> item {
                    Text(
                        text = stringResource(R.string.ScrobbleFailed),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                recentTracks != null && recentTracks!!.isEmpty() -> item {
                    Text(stringResource(R.string.DamnThisIsSoEmpty))
                }
                recentTracks != null -> items(recentTracks!!) { track ->
                    RecentTrackCard(
                        track = track,
                        onCopyToForm = {
                            artistInput = track.artist
                            trackInput = track.track
                            albumInput = track.album
                            timestampInput = formatTimestamp(track.timestampSeconds)
                        },
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun RecentTrackCard(
    track: LastFmRecentTrack,
    onCopyToForm: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(track.track, fontWeight = FontWeight.Bold)
                Text(track.artist, style = MaterialTheme.typography.bodyMedium)
                if (track.album.isNotBlank()) {
                    Text(track.album, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = formatTimestamp(track.timestampSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onCopyToForm) {
                Icon(Icons.Filled.ContentCopy, stringResource(R.string.CopyToForm))
            }
        }
    }
}

private fun formatTimestamp(epochSeconds: Long): String {
    val date = Date(epochSeconds * 1000)
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date)
}

private fun parseTimestamp(input: String): Long? {
    return try {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(input)?.time?.div(1000)
    } catch (_: Exception) {
        input.toLongOrNull()
    }
}
