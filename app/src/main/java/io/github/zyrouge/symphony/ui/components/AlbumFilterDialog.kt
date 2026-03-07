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
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.AlbumFilter
import io.github.zyrouge.symphony.AlbumFilterPreset
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.services.groove.ALBUM_DEBUG_FILTER_FIELDS
import io.github.zyrouge.symphony.services.groove.ALBUM_STRING_FILTER_FIELDS
import io.github.zyrouge.symphony.services.groove.BLANK_TAG_VALUE
import io.github.zyrouge.symphony.services.groove.DebugAlbumFilterField
import io.github.zyrouge.symphony.services.groove.StringFilterField
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class AlbumFilterField { Grid, CoverFlow }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlbumFilterDialog(
    context: ViewContext,
    filterField: AlbumFilterField = AlbumFilterField.Grid,
    onDismissRequest: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var showHiddenAlbums by remember { mutableStateOf(false) }
    var debugMode by remember { mutableStateOf(false) }

    val fieldStates = remember {
        ALBUM_STRING_FILTER_FIELDS.map { it to mutableStateListOf<String>() }
    }
    val availableValues = remember {
        ALBUM_STRING_FILTER_FIELDS.map { it to mutableStateListOf<String>() }
    }
    val debugFieldStates = remember {
        ALBUM_DEBUG_FILTER_FIELDS.map { it to mutableStateListOf<String>() }
    }
    val yearState = remember { mutableStateListOf<Int>() }
    val yearAvailable = remember { mutableStateListOf<Int>() }

    val presets = remember { mutableStateListOf<AlbumFilterPreset>() }
    var loadedPresetName by remember { mutableStateOf<String?>(null) }
    var showPresetPicker by remember { mutableStateOf(false) }
    var showSavePreset by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val settings = context.symphony.settings.data.first()
        val filter = when (filterField) {
            AlbumFilterField.Grid -> settings.uiAlbumGridAlbumFilter
            AlbumFilterField.CoverFlow -> settings.uiCoverFlowAlbumFilter
        }
        fieldStates.forEach { (field, state) ->
            state.addAll(field.getSelected(filter))
        }
        availableValues.forEach { (field, avail) ->
            avail.addAll(field.sortValues(context.symphony.groove.album.getAvailableTagValues(field.tagName)))
        }
        debugFieldStates.forEach { (field, state) ->
            state.addAll(field.getSelected(filter))
        }
        yearState.addAll(filter.releaseYearList)
        yearAvailable.addAll(context.symphony.groove.album.getAvailableYears())
        presets.addAll(settings.uiAlbumFilterPresetsList)
        showHiddenAlbums = settings.showHiddenAlbums
        debugMode = settings.debugMode
        isLoading = false
    }

    fun buildCurrentFilter(): AlbumFilter {
        val base = fieldStates.fold(AlbumFilter.newBuilder()) { builder, (field, state) ->
            field.applyTo(builder, state.toList())
        }
        return debugFieldStates.fold(base) { builder, (field, state) ->
            field.applyTo(builder, state.toList())
        }.clearReleaseYear().addAllReleaseYear(yearState.toList()).build()
    }

    ScaffoldDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Album Filter") },
        titleTrailing = {
            IconButton(
                enabled = !isLoading,
                onClick = { showSavePreset = true },
            ) {
                Icon(Icons.Filled.BookmarkAdd, contentDescription = null)
            }
        },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (presets.isNotEmpty()) Modifier.clickable { showPresetPicker = true }
                        else Modifier
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = loadedPresetName ?: "Load preset...",
                    modifier = Modifier.weight(1f),
                    color = if (loadedPresetName != null) MaterialTheme.colorScheme.primary
                    else LocalContentColor.current.copy(alpha = 0.5f),
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = LocalContentColor.current.copy(alpha = if (presets.isNotEmpty()) 1f else 0.5f),
                )
            }
        },
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
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                    fieldStates.zip(availableValues).forEach { (fieldState, fieldAvail) ->
                        val (field, state) = fieldState
                        val (_, avail) = fieldAvail
                        item {
                            FilterSection(
                                field = field,
                                state = state,
                                available = avail,
                            )
                        }
                    }
                    if (yearAvailable.isNotEmpty()) {
                        item {
                            YearFilterSection(
                                state = yearState,
                                available = yearAvailable,
                            )
                        }
                    }
                    if (debugMode) {
                        item {
                            Text(
                                "Debug Filters",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                            )
                        }
                        debugFieldStates.forEach { (field, state) ->
                            item {
                                DebugFilterSection(field = field, state = state)
                            }
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Show hidden albums",
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = showHiddenAlbums,
                                onCheckedChange = { checked ->
                                    showHiddenAlbums = checked
                                    coroutineScope.launch {
                                        context.symphony.settings.updateData { s ->
                                            s.copy { this.showHiddenAlbums = checked }
                                        }
                                    }
                                },
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
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
                                when (filterField) {
                                    AlbumFilterField.Grid -> uiAlbumGridAlbumFilter = buildCurrentFilter()
                                    AlbumFilterField.CoverFlow -> uiCoverFlowAlbumFilter = buildCurrentFilter()
                                }
                            }
                        }
                        onDismissRequest()
                    }
                }
            ) {
                Text("Apply")
            }
        }
    )

    if (showSavePreset) {
        SavePresetDialog(
            buildFilter = ::buildCurrentFilter,
            onSave = { newPreset ->
                presets.add(newPreset)
                val toSave = presets.toList()
                coroutineScope.launch {
                    context.symphony.settings.updateData { settings ->
                        settings.copy {
                            uiAlbumFilterPresets.clear()
                            uiAlbumFilterPresets.addAll(toSave)
                        }
                    }
                }
                loadedPresetName = newPreset.name
                showSavePreset = false
            },
            onDismissRequest = { showSavePreset = false },
        )
    }

    if (showPresetPicker) {
        PresetPickerDialog(
            presets = presets,
            fieldStates = fieldStates,
            debugFieldStates = debugFieldStates,
            yearState = yearState,
            loadedPresetName = loadedPresetName,
            onPresetLoaded = { name -> loadedPresetName = name },
            onDeletePreset = { preset ->
                presets.remove(preset)
                val toSave = presets.toList()
                coroutineScope.launch {
                    context.symphony.settings.updateData { settings ->
                        settings.copy {
                            uiAlbumFilterPresets.clear()
                            uiAlbumFilterPresets.addAll(toSave)
                        }
                    }
                }
                if (loadedPresetName == preset.name) {
                    loadedPresetName = null
                }
            },
            onDismissRequest = { showPresetPicker = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(
    field: StringFilterField,
    state: SnapshotStateList<String>,
    available: List<String>,
) {
    var showPicker by remember { mutableStateOf(false) }
    val pickable = available.filter { it !in state }

    Text(
        field.label,
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
                    if (value == BLANK_TAG_VALUE) "(blank)" else value,
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
            title = field.label,
            options = pickable,
            onSelect = { value ->
                state.add(value)
                showPicker = false
            },
            onDismissRequest = { showPicker = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DebugFilterSection(
    field: DebugAlbumFilterField,
    state: SnapshotStateList<String>,
) {
    var showPicker by remember { mutableStateOf(false) }
    val pickable = field.values.filter { it !in state }

    Text(
        field.label,
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
            title = field.label,
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
                        headlineContent = { Text(if (option == BLANK_TAG_VALUE) "(blank)" else option) },
                        modifier = Modifier.clickable { onSelect(option) },
                    )
                }
            }
        },
    )
}

@Composable
private fun SavePresetDialog(
    buildFilter: () -> AlbumFilter,
    onSave: (AlbumFilterPreset) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    ScaffoldDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Save Preset") },
        content = {
            Box(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)
            ) {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        unfocusedIndicatorColor = DividerDefaults.color,
                    ),
                    placeholder = { Text("Preset name") },
                    value = input,
                    onValueChange = { input = it },
                )
            }
        },
        actions = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
            TextButton(
                enabled = input.isNotBlank(),
                onClick = {
                    val newPreset = AlbumFilterPreset.newBuilder()
                        .setName(input.trim())
                        .setFilter(buildFilter())
                        .build()
                    onSave(newPreset)
                }
            ) {
                Text("Save")
            }
        },
    )
}

@Composable
private fun PresetPickerDialog(
    presets: SnapshotStateList<AlbumFilterPreset>,
    fieldStates: List<Pair<StringFilterField, SnapshotStateList<String>>>,
    debugFieldStates: List<Pair<DebugAlbumFilterField, SnapshotStateList<String>>>,
    yearState: SnapshotStateList<Int>,
    loadedPresetName: String?,
    onPresetLoaded: (String) -> Unit,
    onDeletePreset: (AlbumFilterPreset) -> Unit,
    onDismissRequest: () -> Unit,
) {
    ScaffoldDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Presets") },
        content = {
            LazyColumn {
                items(presets.toList()) { preset ->
                    ListItem(
                        headlineContent = { Text(preset.name) },
                        trailingContent = {
                            IconButton(onClick = { onDeletePreset(preset) }) {
                                Icon(Icons.Filled.Delete, contentDescription = null)
                            }
                        },
                        modifier = Modifier.clickable {
                            fieldStates.forEach { (field, state) ->
                                state.clear()
                                state.addAll(field.getSelected(preset.filter))
                            }
                            debugFieldStates.forEach { (field, state) ->
                                state.clear()
                                state.addAll(field.getSelected(preset.filter))
                            }
                            yearState.clear()
                            yearState.addAll(preset.filter.releaseYearList)
                            onPresetLoaded(preset.name)
                            onDismissRequest()
                        },
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YearFilterSection(
    state: SnapshotStateList<Int>,
    available: List<Int>,
) {
    var showPicker by remember { mutableStateOf(false) }
    val pickable = available.filter { it !in state }

    Text(
        "Release Year",
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
                    value.toString(),
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
            title = "Release Year",
            options = pickable.map { it.toString() },
            onSelect = { value ->
                state.add(value.toInt())
                showPicker = false
            },
            onDismissRequest = { showPicker = false },
        )
    }
}
