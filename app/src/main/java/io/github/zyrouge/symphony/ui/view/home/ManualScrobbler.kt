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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val TWO_WEEKS_MS = 14L * 24 * 60 * 60 * 1000

@OptIn(ExperimentalMaterial3Api::class)
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
    var selectedEpochMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isScrobbling by remember { mutableStateOf(false) }

    // Timestamp date/time picker state
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDateUtcMs by remember { mutableLongStateOf(0L) }

    // Recent scrobbles day filter state
    var filterDateMs by remember { mutableStateOf<Long?>(null) }
    var showFilterDatePicker by remember { mutableStateOf(false) }

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
                val date = filterDateMs
                if (date != null) {
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = date
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    val from = cal.timeInMillis / 1000
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    val to = cal.timeInMillis / 1000
                    LastFmScrobbler.getRecentTracksPage(
                        apiKey = apiKey,
                        username = username,
                        page = 1,
                        limit = 200,
                        from = from,
                        to = to,
                    )?.tracks
                } else {
                    LastFmScrobbler.getRecentTracks(apiKey, username)
                }
            }
            if (tracks == null) {
                recentError = true
            } else {
                recentTracks = tracks.filter { it.timestampSeconds > 0 }
            }
            isLoadingRecent = false
        }
    }

    // Reload whenever the day filter changes
    LaunchedEffect(filterDateMs) {
        loadRecentTracks()
    }

    // Selectable date range for the timestamp picker (last 14 days only)
    val nowMs = System.currentTimeMillis()
    val todayUtcMidnight = utcMidnight(nowMs)
    val minUtcMidnight = utcMidnight(nowMs - TWO_WEEKS_MS)

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = utcMidnight(selectedEpochMs),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return utcTimeMillis in minUtcMidnight..todayUtcMidnight
            }
            override fun isSelectableYear(year: Int): Boolean {
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                return year >= currentYear - 1 && year <= currentYear
            }
        },
    )

    LaunchedEffect(showDatePicker) {
        if (showDatePicker) {
            datePickerState.selectedDateMillis = utcMidnight(selectedEpochMs)
                .coerceIn(minUtcMidnight, todayUtcMidnight)
        }
    }

    val calForTime = Calendar.getInstance().apply { timeInMillis = selectedEpochMs }
    val timePickerState = rememberTimePickerState(
        initialHour = calForTime.get(Calendar.HOUR_OF_DAY),
        initialMinute = calForTime.get(Calendar.MINUTE),
        is24Hour = true,
    )

    // Filter date picker — no restriction except max = today
    val filterDatePickerState = rememberDatePickerState(
        initialSelectedDateMillis = todayUtcMidnight,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return utcTimeMillis <= todayUtcMidnight
            }
            override fun isSelectableYear(year: Int): Boolean {
                return year <= Calendar.getInstance().get(Calendar.YEAR)
            }
        },
    )

    LaunchedEffect(showFilterDatePicker) {
        if (showFilterDatePicker) {
            filterDatePickerState.selectedDateMillis =
                filterDateMs?.let { utcMidnight(it) } ?: todayUtcMidnight
        }
    }

    // Timestamp date picker dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    pendingDateUtcMs = datePickerState.selectedDateMillis ?: utcMidnight(selectedEpochMs)
                    showTimePicker = true
                }) {
                    Text(stringResource(R.string.Done))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.Cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Timestamp time picker dialog
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.SelectTime)) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    showTimePicker = false
                    val dateCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                        timeInMillis = pendingDateUtcMs
                    }
                    val combined = Calendar.getInstance().apply {
                        set(Calendar.YEAR, dateCal.get(Calendar.YEAR))
                        set(Calendar.MONTH, dateCal.get(Calendar.MONTH))
                        set(Calendar.DAY_OF_MONTH, dateCal.get(Calendar.DAY_OF_MONTH))
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    selectedEpochMs = combined.timeInMillis.coerceAtMost(System.currentTimeMillis())
                }) {
                    Text(stringResource(R.string.Done))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.Cancel))
                }
            },
        )
    }

    // Recent scrobbles filter date picker dialog
    if (showFilterDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showFilterDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showFilterDatePicker = false
                    filterDatePickerState.selectedDateMillis?.let { utcMs ->
                        // Convert UTC midnight → local epoch ms for use in Calendar
                        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = utcMs
                        }
                        val localCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
                            set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
                            set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        filterDateMs = localCal.timeInMillis
                    }
                }) {
                    Text(stringResource(R.string.Done))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFilterDatePicker = false }) {
                    Text(stringResource(R.string.Cancel))
                }
            },
        ) {
            DatePicker(state = filterDatePickerState)
        }
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
                    value = formatTimestamp(selectedEpochMs / 1000),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.Timestamp)) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Filled.CalendarMonth, stringResource(R.string.SelectDate))
                        }
                    },
                )
            }

            item {
                Button(
                    onClick = {
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
                                    timestampSeconds = selectedEpochMs / 1000,
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
                    val activeFilter = filterDateMs
                    if (activeFilter != null) {
                        Text(
                            text = formatDate(activeFilter / 1000),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        IconButton(onClick = { filterDateMs = null }) {
                            Icon(Icons.Filled.Close, null)
                        }
                    }
                    IconButton(onClick = { showFilterDatePicker = true }) {
                        Icon(Icons.Filled.CalendarMonth, stringResource(R.string.SelectDate))
                    }
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
                            selectedEpochMs = track.timestampSeconds * 1000
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

private fun utcMidnight(epochMs: Long): Long {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = epochMs
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun formatTimestamp(epochSeconds: Long): String {
    val date = Date(epochSeconds * 1000)
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date)
}

private fun formatDate(epochSeconds: Long): String {
    val date = Date(epochSeconds * 1000)
    return SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(date)
}
