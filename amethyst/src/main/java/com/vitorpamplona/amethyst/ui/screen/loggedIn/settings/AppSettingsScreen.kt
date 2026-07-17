/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.AccentColorType
import com.vitorpamplona.amethyst.model.BooleanType
import com.vitorpamplona.amethyst.model.ConnectivityType
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.model.FontFamilyType
import com.vitorpamplona.amethyst.model.FontSizeType
import com.vitorpamplona.amethyst.model.ThemeType
import com.vitorpamplona.amethyst.model.UiSettingsFlow
import com.vitorpamplona.amethyst.ui.components.SpinnerSelectionDialog
import com.vitorpamplona.amethyst.ui.components.TextSpinner
import com.vitorpamplona.amethyst.ui.components.TitleExplainer
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.ui.theme.contentColorOnAccent
import com.vitorpamplona.amethyst.ui.theme.isLight
import com.vitorpamplona.amethyst.ui.theme.previewColor
import com.vitorpamplona.amethyst.ui.theme.toFontFamily
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.MutableStateFlow
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

@Composable
fun SettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = R.string.application_preferences), nav)
        },
    ) {
        SettingsScreen(accountViewModel.settings.uiSettingsFlow, Modifier.padding(it))
    }
}

// Full-screen preview: the real top bar + the redesigned content, shown side by side in dark and
// light so both grounds (and the card-hairline contrast) can be eyeballed at once.
@Preview(name = "UI Preferences", device = "spec:width=2160px,height=2340px,dpi=440")
@Composable
fun SettingsScreenPreview() {
    ThemeComparisonRow {
        Scaffold(
            topBar = {
                TopBarWithBackButton(stringRes(id = R.string.application_preferences), EmptyNav())
            },
        ) { padding ->
            SettingsScreen(UiSettingsFlow(), Modifier.padding(padding))
        }
    }
}

@Composable
fun SettingsScreen(
    sharedPrefs: UiSettingsFlow,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsSection(R.string.settings_section_appearance) {
            ThemeTile(sharedPrefs)
            SettingsDivider()
            AccentColorTile(sharedPrefs)
            SettingsDivider()
            FontFamilyTile(sharedPrefs)
            SettingsDivider()
            FontSizeTile(sharedPrefs)
        }

        SettingsSection(R.string.settings_section_media) {
            ImagePreviewTile(sharedPrefs)
            SettingsDivider()
            VideoPlaybackTile(sharedPrefs)
            SettingsDivider()
            AutoplayVideosTile(sharedPrefs)
            SettingsDivider()
            UrlPreviewTile(sharedPrefs)
            SettingsDivider()
            ProfilePictureTile(sharedPrefs)
        }

        SettingsSection(R.string.settings_section_general) {
            LanguageTile(sharedPrefs)
            SettingsDivider()
            UiModeTile(sharedPrefs)
            SettingsDivider()
            ImmersiveScrollingTile(sharedPrefs)
        }
    }
}

/**
 * A [SettingsBlockTile] whose control is a full-width [SingleChoiceSegmentedButtonRow].
 * This is the in-screen replacement for the old dropdown ([TextSpinner]) rows: every
 * option is visible and one tap away. Best for 2–4 mutually-exclusive options.
 *
 * [optionTextStyle] lets each option render its own label preview — e.g. the font tiles
 * draw each label in the very typeface / size it selects, so the row demonstrates the
 * choices instead of only naming them.
 */
@Composable
private fun <T> SegmentedChoiceTile(
    icon: MaterialSymbol,
    @StringRes title: Int,
    @StringRes description: Int,
    options: List<T>,
    labelRes: (T) -> Int,
    selected: T,
    onSelect: (T) -> Unit,
    optionTextStyle: (@Composable (T) -> TextStyle)? = null,
) {
    SettingsBlockTile(
        icon = icon,
        title = stringRes(title),
        description = stringRes(description),
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    // Drop the default check icon: the fill already signals selection, and the icon
                    // steals ~24dp that the label needs in a 3–4-up row.
                    icon = {},
                ) {
                    Text(
                        text = stringRes(labelRes(option)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = optionTextStyle?.invoke(option) ?: LocalTextStyle.current,
                    )
                }
            }
        }
    }
}

/** A [SettingsControlRow] with a [Switch] backed by a [BooleanType] flow (ALWAYS ⇔ on). */
@Composable
private fun BooleanSwitchTile(
    flow: MutableStateFlow<BooleanType>,
    icon: MaterialSymbol,
    @StringRes title: Int,
    @StringRes description: Int,
) {
    val value by flow.collectAsState()
    val checked = value == BooleanType.ALWAYS
    // Explicit Unit return: tryEmit returns Boolean, so the lambda would otherwise infer as
    // (Boolean) -> Boolean and not fit Switch.onCheckedChange / SettingsControlRow.onClick.
    val toggle: (Boolean) -> Unit = { isOn -> flow.tryEmit(if (isOn) BooleanType.ALWAYS else BooleanType.NEVER) }

    SettingsControlRow(
        icon = icon,
        title = stringRes(title),
        description = stringRes(description),
        onClick = { toggle(!checked) },
    ) {
        Switch(checked = checked, onCheckedChange = toggle)
    }
}

@Composable
private fun ThemeTile(sharedPrefs: UiSettingsFlow) {
    val theme by sharedPrefs.theme.collectAsState()
    SegmentedChoiceTile(
        icon = MaterialSymbols.BrightnessMedium,
        title = R.string.theme,
        description = R.string.theme_description,
        options = ThemeType.entries,
        labelRes = { it.resourceId },
        selected = theme,
        onSelect = { sharedPrefs.theme.tryEmit(it) },
    )
}

// Compact labels for the connectivity segmented rows — "Unmetered WiFi" does not fit a 3-up
// segment, so it collapses to "Wi-Fi"; the others are already short.
@StringRes
private fun ConnectivityType.shortLabelRes(): Int =
    when (this) {
        ConnectivityType.ALWAYS -> R.string.connectivity_type_always
        ConnectivityType.WIFI_ONLY -> R.string.connectivity_type_unmetered_wifi_only_short
        ConnectivityType.NEVER -> R.string.connectivity_type_never
    }

@StringRes
private fun FontFamilyType.shortLabelRes(): Int =
    when (this) {
        FontFamilyType.SYSTEM -> R.string.font_family_system_short
        FontFamilyType.SANS_SERIF -> R.string.font_family_sans_serif_short
        FontFamilyType.SERIF -> R.string.font_family_serif_short
        FontFamilyType.MONOSPACE -> R.string.font_family_monospace_short
    }

@StringRes
private fun FeatureSetType.shortLabelRes(): Int =
    when (this) {
        FeatureSetType.COMPLETE -> R.string.ui_feature_set_type_complete_short
        FeatureSetType.SIMPLIFIED -> R.string.ui_feature_set_type_simplified_short
        FeatureSetType.PERFORMANCE -> R.string.ui_feature_set_type_performance_short
    }

@Composable
private fun FontFamilyTile(sharedPrefs: UiSettingsFlow) {
    val fontFamily by sharedPrefs.fontFamily.collectAsState()
    SegmentedChoiceTile(
        icon = MaterialSymbols.Description,
        title = R.string.font_family,
        description = R.string.font_family_description,
        options = FontFamilyType.entries,
        labelRes = { it.shortLabelRes() },
        selected = fontFamily,
        onSelect = { sharedPrefs.fontFamily.tryEmit(it) },
        // Draw each option's name in the very typeface it selects.
        optionTextStyle = { LocalTextStyle.current.copy(fontFamily = it.toFontFamily() ?: FontFamily.Default) },
    )
}

@Composable
private fun FontSizeTile(sharedPrefs: UiSettingsFlow) {
    val fontSize by sharedPrefs.fontSize.collectAsState()
    SegmentedChoiceTile(
        icon = MaterialSymbols.ZoomOutMap,
        title = R.string.font_size,
        description = R.string.font_size_description,
        options = FontSizeType.entries,
        labelRes = { it.resourceId },
        selected = fontSize,
        onSelect = { sharedPrefs.fontSize.tryEmit(it) },
        // Scale each label to preview its size — Small looks small, Huge looks huge. A small 13sp
        // base keeps the spread gentle (~11–17sp) so the row stays tidy instead of lurching taller.
        optionTextStyle = { LocalTextStyle.current.copy(fontSize = 13.sp * it.scale) },
    )
}

@Composable
private fun UiModeTile(sharedPrefs: UiSettingsFlow) {
    val featureSet by sharedPrefs.featureSet.collectAsState()
    SegmentedChoiceTile(
        icon = MaterialSymbols.Tune,
        title = R.string.ui_style,
        description = R.string.ui_style_description,
        options = FeatureSetType.entries,
        labelRes = { it.shortLabelRes() },
        selected = featureSet,
        onSelect = { sharedPrefs.featureSet.tryEmit(it) },
    )
}

@Composable
private fun ImagePreviewTile(sharedPrefs: UiSettingsFlow) {
    val value by sharedPrefs.automaticallyShowImages.collectAsState()
    SegmentedChoiceTile(
        icon = MaterialSymbols.Image,
        title = R.string.automatically_load_images_gifs,
        description = R.string.automatically_load_images_gifs_description,
        options = ConnectivityType.entries,
        labelRes = { it.shortLabelRes() },
        selected = value,
        onSelect = { sharedPrefs.automaticallyShowImages.tryEmit(it) },
    )
}

@Composable
private fun VideoPlaybackTile(sharedPrefs: UiSettingsFlow) {
    val value by sharedPrefs.automaticallyStartPlayback.collectAsState()
    SegmentedChoiceTile(
        icon = MaterialSymbols.Videocam,
        title = R.string.automatically_play_videos,
        description = R.string.automatically_play_videos_description,
        options = ConnectivityType.entries,
        labelRes = { it.shortLabelRes() },
        selected = value,
        onSelect = { sharedPrefs.automaticallyStartPlayback.tryEmit(it) },
    )
}

@Composable
private fun UrlPreviewTile(sharedPrefs: UiSettingsFlow) {
    val value by sharedPrefs.automaticallyShowUrlPreview.collectAsState()
    SegmentedChoiceTile(
        icon = MaterialSymbols.Link,
        title = R.string.automatically_show_url_preview,
        description = R.string.automatically_show_url_preview_description,
        options = ConnectivityType.entries,
        labelRes = { it.shortLabelRes() },
        selected = value,
        onSelect = { sharedPrefs.automaticallyShowUrlPreview.tryEmit(it) },
    )
}

@Composable
private fun ProfilePictureTile(sharedPrefs: UiSettingsFlow) {
    val value by sharedPrefs.automaticallyShowProfilePictures.collectAsState()
    SegmentedChoiceTile(
        icon = MaterialSymbols.AccountCircle,
        title = R.string.automatically_show_profile_picture,
        description = R.string.automatically_show_profile_picture_description,
        options = ConnectivityType.entries,
        labelRes = { it.shortLabelRes() },
        selected = value,
        onSelect = { sharedPrefs.automaticallyShowProfilePictures.tryEmit(it) },
    )
}

@Composable
private fun AutoplayVideosTile(sharedPrefs: UiSettingsFlow) {
    BooleanSwitchTile(
        flow = sharedPrefs.automaticallyPlayVideos,
        icon = MaterialSymbols.PlayCircle,
        title = R.string.autoplay_videos,
        description = R.string.autoplay_videos_description,
    )
}

@Composable
private fun ImmersiveScrollingTile(sharedPrefs: UiSettingsFlow) {
    BooleanSwitchTile(
        flow = sharedPrefs.automaticallyHideNavigationBars,
        icon = MaterialSymbols.Fullscreen,
        title = R.string.automatically_hide_nav_bars,
        description = R.string.automatically_hide_nav_bars_description,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentColorTile(sharedPrefs: UiSettingsFlow) {
    val accent by sharedPrefs.accentColor.collectAsState()
    val dark = !MaterialTheme.colorScheme.isLight

    SettingsBlockTile(
        icon = MaterialSymbols.Circle,
        title = stringRes(R.string.accent_color),
        description = stringRes(R.string.accent_color_description),
    ) {
        // FlowRow so every swatch stays visible (wraps to a second line on narrow screens) instead
        // of scrolling the last ones off-edge with no affordance.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccentColorType.entries.forEach { option ->
                AccentColorSwatch(
                    color = option.previewColor(dark),
                    label = stringRes(option.resourceId),
                    selected = option == accent,
                    onClick = { sharedPrefs.accentColor.tryEmit(option) },
                )
            }
        }
    }
}

// Language can't be a segmented row (50+ locales), so it reads as a disclosure row — icon + title +
// the current language on the right + a chevron — and tapping anywhere opens the picker dialog. That
// matches the app's other "opens a picker" rows instead of leaving a lone text-field dropdown here.
@Composable
private fun LanguageTile(sharedPrefs: UiSettingsFlow) {
    val context = LocalContext.current

    val languageEntries = remember { context.getLangPreferenceDropdownEntries() }
    val languageList = remember { languageEntries.keys.map { TitleExplainer(it) }.toImmutableList() }

    val language by sharedPrefs.preferredLanguage.collectAsState()
    val languageIndex = getLanguageIndex(languageEntries, language)
    val currentLabel = languageList.getOrNull(languageIndex)?.title ?: ""

    var showPicker by remember { mutableStateOf(false) }

    SettingsControlRow(
        icon = MaterialSymbols.Language,
        title = stringRes(R.string.language),
        description = stringRes(R.string.language_description),
        onClick = { showPicker = true },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = currentLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
            Icon(
                symbol = MaterialSymbols.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).size(20.dp),
            )
        }
    }

    if (showPicker) {
        SpinnerSelectionDialog(
            options = languageList,
            onDismiss = { showPicker = false },
        ) { index ->
            showPicker = false
            sharedPrefs.preferredLanguage.tryEmit(languageEntries[languageList[index].title])
        }
    }
}

fun Context.getLocaleListFromXml(): LocaleListCompat {
    val tagsList = mutableListOf<CharSequence>()
    try {
        val xpp: XmlPullParser = resources.getXml(R.xml.locales_config)
        while (xpp.eventType != XmlPullParser.END_DOCUMENT) {
            if (xpp.eventType == XmlPullParser.START_TAG && xpp.name == "locale") {
                tagsList.add(xpp.getAttributeValue(0))
            }
            xpp.next()
        }
    } catch (e: XmlPullParserException) {
        e.printStackTrace()
    } catch (e: IOException) {
        e.printStackTrace()
    }

    return LocaleListCompat.forLanguageTags(tagsList.joinToString(","))
}

fun Context.getLangPreferenceDropdownEntries(): ImmutableMap<String, String> {
    val localeList = getLocaleListFromXml()
    val map = mutableMapOf<String, String>()

    for (a in 0 until localeList.size()) {
        localeList[a].let {
            map.put(
                it!!.getDisplayName(it).replaceFirstChar { char -> char.uppercase() },
                it.toLanguageTag(),
            )
        }
    }
    return map.toImmutableMap()
}

fun getLanguageIndex(
    languageEntries: ImmutableMap<String, String>,
    language: String?,
): Int {
    var languageIndex: Int
    languageIndex =
        if (language != null) {
            languageEntries.values.toTypedArray().indexOf(language)
        } else {
            languageEntries.values.toTypedArray().indexOf(Locale.current.toLanguageTag())
        }
    if (languageIndex == -1) {
        languageIndex = languageEntries.values.toTypedArray().indexOf(Locale.current.language)
    }
    if (languageIndex == -1) languageIndex = languageEntries.values.toTypedArray().indexOf("en")
    return languageIndex
}

@Composable
private fun AccentColorSwatch(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    } else {
                        Modifier
                    },
                ).clickable(onClick = onClick)
                .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                symbol = MaterialSymbols.Done,
                contentDescription = null,
                tint = contentColorOnAccent(color),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun SettingsRow(
    name: Int,
    description: Int,
    selectedItems: ImmutableList<TitleExplainer>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    SettingsRow(name, description) {
        TextSpinner(
            label = "",
            placeholder = selectedItems[selectedIndex].title,
            options = selectedItems,
            onSelect = onSelect,
            modifier = Modifier.windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)),
        )
    }
}

@Composable
fun SettingsRow(
    name: Int,
    description: Int,
    modifier: Modifier = Modifier.fillMaxWidth(),
    content: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Column(
            modifier = Modifier.weight(2.0f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = stringRes(name),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringRes(description),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.End,
        ) {
            content()
        }
    }
}

@Composable
fun SettingsRow(
    name: Int,
    description: Int,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Column(
            modifier = Modifier.weight(2.0f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = stringRes(name),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringRes(description),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
