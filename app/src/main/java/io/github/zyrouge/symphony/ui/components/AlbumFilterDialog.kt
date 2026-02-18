package io.github.zyrouge.symphony.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.AlbumFilter
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Hardcoded available values per field — will be replaced by library data in a later step
private val RELEASE_TYPE_OPTIONS = listOf("Album", "EP", "Single", "Compilation", "Live", "Demo", "Remix")
private val ORIGINAL_OWNER_OPTIONS = listOf("Self", "Gift", "Inherited", "Borrowed")
private val LISTENED_TO_STATUS_OPTIONS = listOf("Unlistened", "In Progress", "Listened", "Revisiting")
private val COUNTRY_OPTIONS = listOf("US", "UK", "JP", "DE", "FR", "SE", "AU", "CA")
private val COLLECTIONS_OPTIONS = listOf("Favourites", "Wishlist", "Loaned Out", "To Rip")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlbumFilterDialog(
    context: ViewContext,
    onDismissRequest: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }

    val releaseTypeState = remember { mutableStateListOf<String>() }
    val originalOwnerState = remember { mutableStateListOf<String>() }
    val listenedToStatusState = remember { mutableStateListOf<String>() }
    val countryState = remember { mutableStateListOf<String>() }
    val collectionsState = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        val filter = context.symphony.settings.data.first().uiAlbumGridAlbumFilter
        releaseTypeState.addAll(filter.releaseTypeList)
        originalOwnerState.addAll(filter.originalOwnerList)
        listenedToStatusState.addAll(filter.listenedToStatusList)
        countryState.addAll(filter.countryList)
        collectionsState.addAll(filter.collectionsList)
        isLoading = false
    }

    ScaffoldDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Album Filter") },
        content = {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.padding(horizontal = 20.dp)) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        FilterSection(
                            label = "Release Type",
                            state = releaseTypeState,
                            available = RELEASE_TYPE_OPTIONS,
                        )
                    }
                    item {
                        FilterSection(
                            label = "Original Owner",
                            state = originalOwnerState,
                            available = ORIGINAL_OWNER_OPTIONS,
                        )
                    }
                    item {
                        FilterSection(
                            label = "Listened To Status",
                            state = listenedToStatusState,
                            available = LISTENED_TO_STATUS_OPTIONS,
                        )
                    }
                    item {
                        FilterSection(
                            label = "Country",
                            state = countryState,
                            available = COUNTRY_OPTIONS,
                        )
                    }
                    item {
                        FilterSection(
                            label = "Collections",
                            state = collectionsState,
                            available = COLLECTIONS_OPTIONS,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        },
        actions = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
            TextButton(
                enabled = !isLoading,
                onClick = {
                    coroutineScope.launch {
                        context.symphony.settings.updateData { settings ->
                            settings.copy {
                                uiAlbumGridAlbumFilter = AlbumFilter.newBuilder()
                                    .addAllReleaseType(releaseTypeState)
                                    .addAllOriginalOwner(originalOwnerState)
                                    .addAllListenedToStatus(listenedToStatusState)
                                    .addAllCountry(countryState)
                                    .addAllCollections(collectionsState)
                                    .build()
                            }
                        }
                    }
                    onDismissRequest()
                }
            ) {
                Text("Apply")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(
    label: String,
    state: SnapshotStateList<String>,
    available: List<String>,
) {
    var showPicker by remember { mutableStateOf(false) }
    val pickable = available.filter { it !in state }

    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        state.forEachIndexed { i, value ->
            Row(
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .padding(start = 10.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    value,
                    color = MaterialTheme.colorScheme.primary,
                    style = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    modifier = Modifier.size(20.dp),
                    onClick = { state.removeAt(i) },
                ) {
                    Icon(Icons.Filled.Close, null, modifier = Modifier.size(12.dp))
                }
            }
        }
        if (pickable.isNotEmpty()) {
            TextButton(onClick = { showPicker = true }) {
                Text("+ Add")
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))

    if (showPicker) {
        PickerDialog(
            title = label,
            options = pickable,
            onSelect = { value ->
                state.add(value)
                showPicker = false
            },
            onDismissRequest = { showPicker = false },
        )
    }
}

@Composable
private fun PickerDialog(
    title: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    ScaffoldDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        content = {
            LazyColumn {
                items(options) { option ->
                    ListItem(
                        headlineContent = { Text(option) },
                        modifier = Modifier.clickable { onSelect(option) },
                    )
                }
            }
        },
    )
}
