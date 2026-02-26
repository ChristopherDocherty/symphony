package io.github.zyrouge.symphony.ui.view.settings

import androidx.appcompat.app.AppCompatDelegate
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
import androidx.core.os.LocaleListCompat
import io.github.zyrouge.symphony.ThemeMode
import io.github.zyrouge.symphony.copy
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
import androidx.compose.ui.res.stringResource
import io.github.zyrouge.symphony.R

private val supportedLocaleNativeNames = mapOf(
    "" to "System",
    "be" to "беларуская",
    "zh-Hans" to "中文 (简体)",
    "en" to "English",
    "fi" to "suomi",
    "fr" to "français",
    "de" to "Deutsch",
    "it" to "italiano",
    "ja" to "日本語",
    "ryu" to "うちなーぐち",
    "fa" to "فارسی",
    "pl" to "polski",
    "pt" to "português",
    "ro" to "română",
    "ru" to "русский",
    "es" to "español",
    "tr" to "Türkçe",
    "uk" to "українська",
    "vi" to "Tiếng Việt",
)

private val supportedLocaleDisplayNames = mapOf(
    "" to "System",
    "be" to "Belarusian",
    "zh-Hans" to "Chinese (Simplified)",
    "en" to "English",
    "fi" to "Finnish",
    "fr" to "French",
    "de" to "German",
    "it" to "Italian",
    "ja" to "Japanese",
    "ryu" to "Okinawan",
    "fa" to "Persian",
    "pl" to "Polish",
    "pt" to "Portuguese",
    "ro" to "Romanian",
    "ru" to "Russian",
    "es" to "Spanish",
    "tr" to "Turkish",
    "uk" to "Ukrainian",
    "vi" to "Vietnamese",
)

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
    val currentLocale = AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag() ?: ""
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
                        Text("${stringResource(R.string.Settings)} - ${stringResource(R.string.Appearance)}")
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
                            Text(stringResource(R.string.Language_))
                        },
                        value = currentLocale,
                        values = supportedLocaleNativeNames,
                        captions = supportedLocaleDisplayNames,
                        onChange = { value ->
                            if (value.isEmpty()) {
                                AppCompatDelegate.setApplicationLocales(
                                    LocaleListCompat.getEmptyLocaleList()
                                )
                            } else {
                                AppCompatDelegate.setApplicationLocales(
                                    LocaleListCompat.forLanguageTags(value)
                                )
                            }
                        }
                    )
                    HorizontalDivider()
                    SettingsOptionTile(
                        icon = {
                            Icon(Icons.Filled.TextFormat, null)
                        },
                        title = {
                            Text(stringResource(R.string.Font))
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
                            Text(stringResource(R.string.FontScale))
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
                            Text(stringResource(R.string.ContentScale))
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
                            Text(stringResource(R.string.Theme))
                        },
                        value = themeMode,
                        values = mapOf(
                            ThemeMode.THEME_SYSTEM to stringResource(R.string.SystemLightDark),
                            ThemeMode.THEME_SYSTEM_BLACK to stringResource(R.string.SystemLightBlack),
                            ThemeMode.THEME_LIGHT to stringResource(R.string.Light),
                            ThemeMode.THEME_DARK to stringResource(R.string.Dark),
                            ThemeMode.THEME_BLACK to stringResource(R.string.Black),
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
                            Text(stringResource(R.string.MaterialYou))
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
                            Text(stringResource(R.string.PrimaryColor))
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
    PrimaryThemeColor.Red -> context.activity.getString(R.string.Red)
    PrimaryThemeColor.Orange -> context.activity.getString(R.string.Orange)
    PrimaryThemeColor.Amber -> context.activity.getString(R.string.Amber)
    PrimaryThemeColor.Yellow -> context.activity.getString(R.string.Yellow)
    PrimaryThemeColor.Lime -> context.activity.getString(R.string.Lime)
    PrimaryThemeColor.Green -> context.activity.getString(R.string.Green)
    PrimaryThemeColor.Emerald -> context.activity.getString(R.string.Emerald)
    PrimaryThemeColor.Teal -> context.activity.getString(R.string.Teal)
    PrimaryThemeColor.Cyan -> context.activity.getString(R.string.Cyan)
    PrimaryThemeColor.Sky -> context.activity.getString(R.string.Sky)
    PrimaryThemeColor.Blue -> context.activity.getString(R.string.Blue)
    PrimaryThemeColor.Indigo -> context.activity.getString(R.string.Indigo)
    PrimaryThemeColor.Violet -> context.activity.getString(R.string.Violet)
    PrimaryThemeColor.Purple -> context.activity.getString(R.string.Purple)
    PrimaryThemeColor.Fuchsia -> context.activity.getString(R.string.Fuchsia)
    PrimaryThemeColor.Pink -> context.activity.getString(R.string.Pink)
    PrimaryThemeColor.Rose -> context.activity.getString(R.string.Rose)
}
