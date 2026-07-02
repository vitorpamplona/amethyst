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
package com.vitorpamplona.amethyst.model

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.navigation.bottombars.BottomBarEntry
import com.vitorpamplona.amethyst.ui.navigation.bottombars.DefaultBottomBarEntries
import kotlinx.serialization.Serializable

@Stable
@Serializable
data class UiSettings(
    val theme: ThemeType = ThemeType.SYSTEM,
    val preferredLanguage: String? = null,
    val automaticallyShowImages: ConnectivityType = ConnectivityType.ALWAYS,
    val automaticallyStartPlayback: ConnectivityType = ConnectivityType.ALWAYS,
    val automaticallyPlayVideos: BooleanType = BooleanType.ALWAYS,
    val automaticallyShowUrlPreview: ConnectivityType = ConnectivityType.ALWAYS,
    val automaticallyHideNavigationBars: BooleanType = BooleanType.ALWAYS,
    val automaticallyShowProfilePictures: ConnectivityType = ConnectivityType.ALWAYS,
    val dontShowPushNotificationSelector: Boolean = false,
    val dontAskForNotificationPermissions: Boolean = false,
    val featureSet: FeatureSetType = FeatureSetType.SIMPLIFIED,
    val gallerySet: ProfileGalleryType = ProfileGalleryType.CLASSIC,
    val automaticallyProposeAiImprovements: BooleanType = BooleanType.ALWAYS,
    val useTrackedBroadcasts: BooleanType = BooleanType.ALWAYS,
    val automaticallyCreateDrafts: BooleanType = BooleanType.ALWAYS,
    val bottomBarItems: List<BottomBarEntry> = DefaultBottomBarEntries,
    val showHomeNewThreadsTab: Boolean = true,
    val showHomeConversationsTab: Boolean = true,
    val showHomeEverythingTab: Boolean = false,
    val showProfileBadges: Boolean = true,
    val showProfileAppRecommendations: Boolean = true,
    val showProfileZapReceivedFeed: Boolean = true,
    val showProfileFollowersFeed: Boolean = true,
    val dontShowOnchainPublicWarning: Boolean = false,
    val suggestWorkoutsFromHealthConnect: BooleanType = BooleanType.ALWAYS,
    val accentColor: AccentColorType = AccentColorType.PURPLE,
    val fontFamily: FontFamilyType = FontFamilyType.SYSTEM,
    val fontSize: FontSizeType = FontSizeType.NORMAL,
    val composeSignature: String = "",
)

enum class ThemeType(
    val screenCode: Int,
    val resourceId: Int,
) {
    SYSTEM(0, R.string.system),
    LIGHT(1, R.string.light),
    DARK(2, R.string.dark),
}

fun parseThemeType(code: Int?): ThemeType =
    when (code) {
        ThemeType.SYSTEM.screenCode -> ThemeType.SYSTEM
        ThemeType.LIGHT.screenCode -> ThemeType.LIGHT
        ThemeType.DARK.screenCode -> ThemeType.DARK
        else -> ThemeType.SYSTEM
    }

enum class AccentColorType(
    val screenCode: Int,
    val resourceId: Int,
) {
    PURPLE(0, R.string.accent_color_purple),
    BLUE(1, R.string.accent_color_blue),
    GREEN(2, R.string.accent_color_green),
    ORANGE(3, R.string.accent_color_orange),
    RED(4, R.string.accent_color_red),
    PINK(5, R.string.accent_color_pink),
}

fun parseAccentColorType(screenCode: Int): AccentColorType =
    when (screenCode) {
        AccentColorType.PURPLE.screenCode -> AccentColorType.PURPLE
        AccentColorType.BLUE.screenCode -> AccentColorType.BLUE
        AccentColorType.GREEN.screenCode -> AccentColorType.GREEN
        AccentColorType.ORANGE.screenCode -> AccentColorType.ORANGE
        AccentColorType.RED.screenCode -> AccentColorType.RED
        AccentColorType.PINK.screenCode -> AccentColorType.PINK
        else -> AccentColorType.PURPLE
    }

enum class FontFamilyType(
    val screenCode: Int,
    val resourceId: Int,
) {
    SYSTEM(0, R.string.font_family_system),
    SANS_SERIF(1, R.string.font_family_sans_serif),
    SERIF(2, R.string.font_family_serif),
    MONOSPACE(3, R.string.font_family_monospace),
}

fun parseFontFamilyType(screenCode: Int): FontFamilyType =
    when (screenCode) {
        FontFamilyType.SYSTEM.screenCode -> FontFamilyType.SYSTEM
        FontFamilyType.SANS_SERIF.screenCode -> FontFamilyType.SANS_SERIF
        FontFamilyType.SERIF.screenCode -> FontFamilyType.SERIF
        FontFamilyType.MONOSPACE.screenCode -> FontFamilyType.MONOSPACE
        else -> FontFamilyType.SYSTEM
    }

enum class FontSizeType(
    val scale: Float,
    val screenCode: Int,
    val resourceId: Int,
) {
    SMALL(0.85f, 0, R.string.font_size_small),
    NORMAL(1.0f, 1, R.string.font_size_normal),
    LARGE(1.15f, 2, R.string.font_size_large),
    HUGE(1.3f, 3, R.string.font_size_huge),
}

fun parseFontSizeType(screenCode: Int): FontSizeType =
    when (screenCode) {
        FontSizeType.SMALL.screenCode -> FontSizeType.SMALL
        FontSizeType.NORMAL.screenCode -> FontSizeType.NORMAL
        FontSizeType.LARGE.screenCode -> FontSizeType.LARGE
        FontSizeType.HUGE.screenCode -> FontSizeType.HUGE
        else -> FontSizeType.NORMAL
    }

enum class ConnectivityType(
    val prefCode: Boolean?,
    val screenCode: Int,
    val resourceId: Int,
) {
    ALWAYS(null, 0, R.string.connectivity_type_always),
    WIFI_ONLY(true, 1, R.string.connectivity_type_unmetered_wifi_only),
    NEVER(false, 2, R.string.connectivity_type_never),
}

enum class FeatureSetType(
    val screenCode: Int,
    val resourceId: Int,
) {
    COMPLETE(0, R.string.ui_feature_set_type_complete),
    SIMPLIFIED(1, R.string.ui_feature_set_type_simplified),
    PERFORMANCE(2, R.string.ui_feature_set_type_performance),
}

enum class ProfileGalleryType(
    val screenCode: Int,
    val resourceId: Int,
) {
    CLASSIC(0, R.string.gallery_type_classic),
    MODERN(1, R.string.gallery_type_modern),
}

fun parseConnectivityType(code: Boolean?): ConnectivityType =
    when (code) {
        ConnectivityType.ALWAYS.prefCode -> ConnectivityType.ALWAYS
        ConnectivityType.WIFI_ONLY.prefCode -> ConnectivityType.WIFI_ONLY
        ConnectivityType.NEVER.prefCode -> ConnectivityType.NEVER
        else -> ConnectivityType.ALWAYS
    }

fun parseConnectivityType(screenCode: Int): ConnectivityType =
    when (screenCode) {
        ConnectivityType.ALWAYS.screenCode -> ConnectivityType.ALWAYS
        ConnectivityType.WIFI_ONLY.screenCode -> ConnectivityType.WIFI_ONLY
        ConnectivityType.NEVER.screenCode -> ConnectivityType.NEVER
        else -> ConnectivityType.ALWAYS
    }

fun parseFeatureSetType(screenCode: Int): FeatureSetType =
    when (screenCode) {
        FeatureSetType.COMPLETE.screenCode -> FeatureSetType.COMPLETE
        FeatureSetType.SIMPLIFIED.screenCode -> FeatureSetType.SIMPLIFIED
        FeatureSetType.PERFORMANCE.screenCode -> FeatureSetType.PERFORMANCE
        else -> FeatureSetType.COMPLETE
    }

fun parseGalleryType(screenCode: Int): ProfileGalleryType =
    when (screenCode) {
        ProfileGalleryType.CLASSIC.screenCode -> ProfileGalleryType.CLASSIC
        ProfileGalleryType.MODERN.screenCode -> ProfileGalleryType.MODERN
        else -> ProfileGalleryType.CLASSIC
    }

enum class BooleanType(
    val prefCode: Boolean?,
    val screenCode: Int,
    val reourceId: Int,
) {
    ALWAYS(null, 0, R.string.connectivity_type_always),
    NEVER(false, 1, R.string.connectivity_type_never),
}

fun parseBooleanType(code: Boolean?): BooleanType =
    when (code) {
        BooleanType.ALWAYS.prefCode -> BooleanType.ALWAYS
        BooleanType.NEVER.prefCode -> BooleanType.NEVER
        else -> BooleanType.ALWAYS
    }

fun parseBooleanType(screenCode: Int): BooleanType =
    when (screenCode) {
        BooleanType.ALWAYS.screenCode -> BooleanType.ALWAYS
        BooleanType.NEVER.screenCode -> BooleanType.NEVER
        else -> BooleanType.ALWAYS
    }

enum class WarningType(
    val prefCode: Boolean?,
    val screenCode: Int,
    val resourceId: Int,
) {
    WARN(null, 0, R.string.content_warning_see_warnings_option),
    SHOW(true, 1, R.string.content_warning_show_all_sensitive_content_option),
    HIDE(false, 2, R.string.content_warning_hide_all_sensitive_content_option),
}

fun parseWarningType(screenCode: Int): WarningType =
    when (screenCode) {
        WarningType.WARN.screenCode -> WarningType.WARN
        WarningType.SHOW.screenCode -> WarningType.SHOW
        WarningType.HIDE.screenCode -> WarningType.HIDE
        else -> WarningType.WARN
    }

fun parseWarningType(code: Boolean?): WarningType =
    when (code) {
        WarningType.WARN.prefCode -> WarningType.WARN
        WarningType.HIDE.prefCode -> WarningType.HIDE
        WarningType.SHOW.prefCode -> WarningType.SHOW
        else -> WarningType.WARN
    }
