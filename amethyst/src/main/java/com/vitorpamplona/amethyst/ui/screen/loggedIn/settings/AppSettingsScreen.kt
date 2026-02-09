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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.ConnectivityType
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.model.ProfileGalleryType
import com.vitorpamplona.amethyst.model.ThemeType
import com.vitorpamplona.amethyst.model.UiSettingsFlow
import com.vitorpamplona.amethyst.model.parseBooleanType
import com.vitorpamplona.amethyst.model.parseConnectivityType
import com.vitorpamplona.amethyst.model.parseFeatureSetType
import com.vitorpamplona.amethyst.model.parseGalleryType
import com.vitorpamplona.amethyst.model.parseThemeType
import com.vitorpamplona.amethyst.ui.components.PushNotificationSettingsRow
import com.vitorpamplona.amethyst.ui.components.TextSpinner
import com.vitorpamplona.amethyst.ui.components.TitleExplainer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.RowColSpacing
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonRow
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
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
            TopBarWithBackButton(stringRes(id = R.string.application_preferences), nav::popBack)
        },
    ) {
        Column(Modifier.padding(it)) {
            SettingsScreen(accountViewModel.settings.uiSettingsFlow)
        }
    }
}

@Preview(device = "spec:width=2160px,height=2340px,dpi=440")
@Composable
fun SettingsScreenPreview() {
    ThemeComparisonRow {
        SettingsScreen(UiSettingsFlow())
    }
}

@Composable
fun SettingsScreen(sharedPrefs: UiSettingsFlow) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = Size10dp, start = Size20dp, end = Size20dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = RowColSpacing,
    ) {
        ShowLanguageChoice(sharedPrefs)
        ShowThemeChoice(sharedPrefs)
        ShowImagePreviewChoice(sharedPrefs)
        ShowVideoPlaybackChoice(sharedPrefs)
        ShowUrlPreviewChoice(sharedPrefs)
        ShowProfilePictureChoice(sharedPrefs)
        ImmersiveScrollingChoice(sharedPrefs)
        FeatureSetChoice(sharedPrefs)
        GalleryChoice(sharedPrefs)
        PushNotificationSettingsRow(sharedPrefs)
    }
}

fun Context.getLocaleListFromXml(): LocaleListCompat {
    val tagsList = mutableListOf<CharSequence>()
    try {
        val xpp: XmlPullParser = resources.getXml(R.xml.locales_config)
        while (xpp.eventType != XmlPullParser.END_DOCUMENT) {
            if (xpp.eventType == XmlPullParser.START_TAG) {
                if (xpp.name == "locale") {
                    tagsList.add(xpp.getAttributeValue(0))
                }
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
fun ShowLanguageChoice(sharedPrefs: UiSettingsFlow) {
    val context = LocalContext.current

    val languageEntries = remember { context.getLangPreferenceDropdownEntries() }
    val languageList = remember { languageEntries.keys.map { TitleExplainer(it) }.toImmutableList() }

    val language by sharedPrefs.preferredLanguage.collectAsState()

    val languageIndex = getLanguageIndex(languageEntries, language)

    SettingsRow(
        R.string.language,
        R.string.language_description,
        languageList,
        languageIndex,
    ) {
        sharedPrefs.preferredLanguage.tryEmit(languageEntries[languageList[it].title])
    }
}

@Composable
fun ShowThemeChoice(sharedPrefs: UiSettingsFlow) {
    val themeOptions =
        persistentListOf(
            TitleExplainer(stringRes(ThemeType.SYSTEM.resourceId)),
            TitleExplainer(stringRes(ThemeType.LIGHT.resourceId)),
            TitleExplainer(stringRes(ThemeType.DARK.resourceId)),
        )

    val themeIndex by sharedPrefs.theme.collectAsState()

    SettingsRow(
        R.string.theme,
        R.string.theme_description,
        themeOptions,
        themeIndex.screenCode,
    ) {
        sharedPrefs.theme.tryEmit(parseThemeType(it))
    }
}

@Composable
fun ShowImagePreviewChoice(sharedPrefs: UiSettingsFlow) {
    val connectivityBasedOptions =
        persistentListOf(
            TitleExplainer(stringRes(ConnectivityType.ALWAYS.resourceId)),
            TitleExplainer(stringRes(ConnectivityType.WIFI_ONLY.resourceId)),
            TitleExplainer(stringRes(ConnectivityType.NEVER.resourceId)),
        )

    val showImagesIndex by sharedPrefs.automaticallyShowImages.collectAsState()

    SettingsRow(
        R.string.automatically_load_images_gifs,
        R.string.automatically_load_images_gifs_description,
        connectivityBasedOptions,
        showImagesIndex.screenCode,
    ) {
        sharedPrefs.automaticallyShowImages.tryEmit(parseConnectivityType(it))
    }
}

@Composable
fun ShowVideoPlaybackChoice(sharedPrefs: UiSettingsFlow) {
    val connectivityBasedOptions =
        persistentListOf(
            TitleExplainer(stringRes(ConnectivityType.ALWAYS.resourceId)),
            TitleExplainer(stringRes(ConnectivityType.WIFI_ONLY.resourceId)),
            TitleExplainer(stringRes(ConnectivityType.NEVER.resourceId)),
        )

    val videoIndex by sharedPrefs.automaticallyStartPlayback.collectAsState()

    SettingsRow(
        R.string.automatically_play_videos,
        R.string.automatically_play_videos_description,
        connectivityBasedOptions,
        videoIndex.screenCode,
    ) {
        sharedPrefs.automaticallyStartPlayback.tryEmit(parseConnectivityType(it))
    }
}

@Composable
fun ShowUrlPreviewChoice(sharedPrefs: UiSettingsFlow) {
    val connectivityBasedOptions =
        persistentListOf(
            TitleExplainer(stringRes(ConnectivityType.ALWAYS.resourceId)),
            TitleExplainer(stringRes(ConnectivityType.WIFI_ONLY.resourceId)),
            TitleExplainer(stringRes(ConnectivityType.NEVER.resourceId)),
        )

    val linkIndex by sharedPrefs.automaticallyShowUrlPreview.collectAsState()

    SettingsRow(
        R.string.automatically_show_url_preview,
        R.string.automatically_show_url_preview_description,
        connectivityBasedOptions,
        linkIndex.screenCode,
    ) {
        sharedPrefs.automaticallyShowUrlPreview.tryEmit(parseConnectivityType(it))
    }
}

@Composable
fun ShowProfilePictureChoice(sharedPrefs: UiSettingsFlow) {
    val profilePictureIndex by sharedPrefs.automaticallyShowProfilePictures.collectAsState()

    val connectivityBasedOptions =
        persistentListOf(
            TitleExplainer(stringRes(ConnectivityType.ALWAYS.resourceId)),
            TitleExplainer(stringRes(ConnectivityType.WIFI_ONLY.resourceId)),
            TitleExplainer(stringRes(ConnectivityType.NEVER.resourceId)),
        )

    SettingsRow(
        R.string.automatically_show_profile_picture,
        R.string.automatically_show_profile_picture_description,
        connectivityBasedOptions,
        profilePictureIndex.screenCode,
    ) {
        sharedPrefs.automaticallyShowProfilePictures.tryEmit(parseConnectivityType(it))
    }
}

@Composable
fun ImmersiveScrollingChoice(sharedPrefs: UiSettingsFlow) {
    val hideNavBarsIndex by sharedPrefs.automaticallyHideNavigationBars.collectAsState()

    val booleanItems =
        persistentListOf(
            TitleExplainer(stringRes(ConnectivityType.ALWAYS.resourceId)),
            TitleExplainer(stringRes(ConnectivityType.NEVER.resourceId)),
        )

    SettingsRow(
        R.string.automatically_hide_nav_bars,
        R.string.automatically_hide_nav_bars_description,
        booleanItems,
        hideNavBarsIndex.screenCode,
    ) {
        sharedPrefs.automaticallyHideNavigationBars.tryEmit(parseBooleanType(it))
    }
}

@Composable
fun FeatureSetChoice(sharedPrefs: UiSettingsFlow) {
    val featureItems =
        persistentListOf(
            TitleExplainer(stringRes(FeatureSetType.COMPLETE.resourceId)),
            TitleExplainer(stringRes(FeatureSetType.SIMPLIFIED.resourceId)),
            TitleExplainer(stringRes(FeatureSetType.PERFORMANCE.resourceId)),
        )

    val featureSetIndex by sharedPrefs.featureSet.collectAsState()

    SettingsRow(
        R.string.ui_style,
        R.string.ui_style_description,
        featureItems,
        featureSetIndex.screenCode,
    ) {
        sharedPrefs.featureSet.tryEmit(parseFeatureSetType(it))
    }
}

@Composable
fun GalleryChoice(sharedPrefs: UiSettingsFlow) {
    val galleryItems =
        persistentListOf(
            TitleExplainer(stringRes(ProfileGalleryType.CLASSIC.resourceId)),
            TitleExplainer(stringRes(ProfileGalleryType.MODERN.resourceId)),
        )

    val galleryIndex by sharedPrefs.gallerySet.collectAsState()

    SettingsRow(
        R.string.gallery_style,
        R.string.gallery_style_description,
        galleryItems,
        galleryIndex.screenCode,
    ) {
        sharedPrefs.gallerySet.tryEmit(parseGalleryType(it))
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
    content: @Composable () -> Unit,
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

        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.End,
        ) {
            content()
        }
    }
}
