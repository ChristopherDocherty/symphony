package io.github.zyrouge.symphony.ui.view.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.TextIncrease
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
import io.github.zyrouge.symphony.ThemeMode
import io.github.zyrouge.symphony.copy
import io.github.zyrouge.symphony.services.i18n.CommonTranslation
import io.github.zyrouge.symphony.ui.components.IconButtonPlaceholder
import io.github.zyrouge.symphony.ui.components.TopAppBarMinimalTitle
import io.github.zyrouge.symphony.ui.components.settings.SettingsFloatInputTile
import io.github.zyrouge.symphony.ui.components.settings.SettingsOptionTile
import io.github.zyrouge.symphony.ui.components.settings.SettingsSwitchTile
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.theme.PrimaryThemeColor
import io.github.zyrouge.symphony.ui.theme.SymphonyTypography
import io.github.zyrouge.symphony.ui.theme.ThemeColors
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

private val scalingPresets = listOf(
    0.25f, 0.5f, 0.75f, 0.9f, 1f,
    1.1f, 1.25f, 1.5f, 1.75f, 2f,
    2.25f, 2.5f, 2.75f, 3f,
)

@Serializable
object AppearanceSettingsViewRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsView(context: ViewContext) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val settings by context.symphony.settingsState.collectAsState()
    val language = settings.language
    val fontFamily = settings.fontFamily
    val themeMode = settings.themeMode
    val useMaterialYou = settings.materialYou
    val primaryColor = settings.primaryColor
    val fontScale = settings.fontScale
    val contentScale = settings.contentScale

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    TopAppBarMinimalTitle {
                        Text("${context.symphony.t.Settings} - ${context.symphony.t.Appearance}")
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
                    SettingsOptionTile(
                        icon = {
                            Icon(Icons.Filled.Language, null)
                        },
                        title = {
                            Text(context.symphony.t.Language_)
                        },
                        value = language,
                        values = run {
                            val defaultLocaleNativeName =
                                context.symphony.translator.getDefaultLocaleNativeName()
                            mapOf(
                                "" to "${context.symphony.t.System} (${defaultLocaleNativeName})"
                            ) + context.symphony.translator.translations.localeNativeNames
                        },
                        captions = run {
                            val defaultLocaleDisplayName =
                                context.symphony.translator.getDefaultLocaleDisplayName()
                            mapOf(
                                "" to "${CommonTranslation.System} (${defaultLocaleDisplayName})"
                            ) + context.symphony.translator.translations.localeDisplayNames
                        },
                        onChange = { value ->
                            scope.launch {
                                context.symphony.settings.updateData { s ->
                                    s.copy { this.language = value }
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                    SettingsOptionTile(
                        icon = {
                            Icon(Icons.Filled.TextFormat, null)
                        },
                        title = {
                            Text(context.symphony.t.Font)
                        },
                        value = SymphonyTypography.resolveFont(fontFamily.takeIf { it.isNotEmpty() }).fontName,
                        values = SymphonyTypography.all.keys.associateWith { it },
                        onChange = { value ->
                            scope.launch {
                                context.symphony.settings.updateData { s ->
                                    s.copy { this.fontFamily = value }
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                    SettingsFloatInputTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.TextIncrease, null)
                        },
                        title = {
                            Text(context.symphony.t.FontScale)
                        },
                        value = fontScale,
                        presets = scalingPresets,
                        labelText = { "x$it" },
                        onReset = {
                            scope.launch {
                                context.symphony.settings.updateData { s ->
                                    s.copy { this.fontScale = 1f }
                                }
                            }
                        },
                        onChange = { value ->
                            scope.launch {
                                context.symphony.settings.updateData { s ->
                                    s.copy { this.fontScale = value }
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                    SettingsFloatInputTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.PhotoSizeSelectLarge, null)
                        },
                        title = {
                            Text(context.symphony.t.ContentScale)
                        },
                        value = contentScale,
                        presets = scalingPresets,
                        labelText = { "x$it" },
                        onReset = {
                            scope.launch {
                                context.symphony.settings.updateData { s ->
                                    s.copy { this.contentScale = 1f }
                                }
                            }
                        },
                        onChange = { value ->
                            scope.launch {
                                context.symphony.settings.updateData { s ->
                                    s.copy { this.contentScale = value }
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                    SettingsOptionTile(
                        icon = {
                            Icon(Icons.Filled.Palette, null)
                        },
                        title = {
                            Text(context.symphony.t.Theme)
                        },
                        value = themeMode,
                        values = mapOf(
                            ThemeMode.THEME_SYSTEM to context.symphony.t.SystemLightDark,
                            ThemeMode.THEME_SYSTEM_BLACK to context.symphony.t.SystemLightBlack,
                            ThemeMode.THEME_LIGHT to context.symphony.t.Light,
                            ThemeMode.THEME_DARK to context.symphony.t.Dark,
                            ThemeMode.THEME_BLACK to context.symphony.t.Black,
                        ),
                        onChange = { value ->
                            scope.launch {
                                context.symphony.settings.updateData { s ->
                                    s.copy { this.themeMode = value }
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.Filled.Face, null)
                        },
                        title = {
                            Text(context.symphony.t.MaterialYou)
                        },
                        value = useMaterialYou,
                        onChange = { value ->
                            scope.launch {
                                context.symphony.settings.updateData { s ->
                                    s.copy { materialYou = value }
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                    SettingsOptionTile(
                        icon = {
                            Icon(Icons.Filled.Colorize, null)
                        },
                        title = {
                            Text(context.symphony.t.PrimaryColor)
                        },
                        value = ThemeColors.resolvePrimaryColorKey(primaryColor.takeIf { it.isNotEmpty() }),
                        values = PrimaryThemeColor.entries.associateWith { it.label(context) },
                        enabled = !useMaterialYou,
                        onChange = { value ->
                            scope.launch {
                                context.symphony.settings.updateData { s ->
                                    s.copy { this.primaryColor = value.name }
                                }
                            }
                        }
                    )
                }
            }
        }
    )
}

fun PrimaryThemeColor.label(context: ViewContext) = when (this) {
    PrimaryThemeColor.Red -> context.symphony.t.Red
    PrimaryThemeColor.Orange -> context.symphony.t.Orange
    PrimaryThemeColor.Amber -> context.symphony.t.Amber
    PrimaryThemeColor.Yellow -> context.symphony.t.Yellow
    PrimaryThemeColor.Lime -> context.symphony.t.Lime
    PrimaryThemeColor.Green -> context.symphony.t.Green
    PrimaryThemeColor.Emerald -> context.symphony.t.Emerald
    PrimaryThemeColor.Teal -> context.symphony.t.Teal
    PrimaryThemeColor.Cyan -> context.symphony.t.Cyan
    PrimaryThemeColor.Sky -> context.symphony.t.Sky
    PrimaryThemeColor.Blue -> context.symphony.t.Blue
    PrimaryThemeColor.Indigo -> context.symphony.t.Indigo
    PrimaryThemeColor.Violet -> context.symphony.t.Violet
    PrimaryThemeColor.Purple -> context.symphony.t.Purple
    PrimaryThemeColor.Fuchsia -> context.symphony.t.Fuchsia
    PrimaryThemeColor.Pink -> context.symphony.t.Pink
    PrimaryThemeColor.Rose -> context.symphony.t.Rose
}
