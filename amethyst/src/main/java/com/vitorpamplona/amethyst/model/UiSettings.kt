/**
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
import kotlinx.serialization.Serializable

@Stable
@Serializable
data class UiSettings(
    val theme: ThemeType = ThemeType.SYSTEM,
    val preferredLanguage: String? = null,
    val automaticallyShowImages: ConnectivityType = ConnectivityType.ALWAYS,
    val automaticallyStartPlayback: ConnectivityType = ConnectivityType.ALWAYS,
    val automaticallyShowUrlPreview: ConnectivityType = ConnectivityType.ALWAYS,
    val automaticallyHideNavigationBars: BooleanType = BooleanType.ALWAYS,
    val automaticallyShowProfilePictures: ConnectivityType = ConnectivityType.ALWAYS,
    val dontShowPushNotificationSelector: Boolean = false,
    val dontAskForNotificationPermissions: Boolean = false,
    val featureSet: FeatureSetType = FeatureSetType.SIMPLIFIED,
    val gallerySet: ProfileGalleryType = ProfileGalleryType.CLASSIC,
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
        else -> {
            ThemeType.SYSTEM
        }
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
        else -> {
            ConnectivityType.ALWAYS
        }
    }

fun parseConnectivityType(screenCode: Int): ConnectivityType =
    when (screenCode) {
        ConnectivityType.ALWAYS.screenCode -> ConnectivityType.ALWAYS
        ConnectivityType.WIFI_ONLY.screenCode -> ConnectivityType.WIFI_ONLY
        ConnectivityType.NEVER.screenCode -> ConnectivityType.NEVER
        else -> {
            ConnectivityType.ALWAYS
        }
    }

fun parseFeatureSetType(screenCode: Int): FeatureSetType =
    when (screenCode) {
        FeatureSetType.COMPLETE.screenCode -> FeatureSetType.COMPLETE
        FeatureSetType.SIMPLIFIED.screenCode -> FeatureSetType.SIMPLIFIED
        FeatureSetType.PERFORMANCE.screenCode -> FeatureSetType.PERFORMANCE
        else -> {
            FeatureSetType.COMPLETE
        }
    }

fun parseGalleryType(screenCode: Int): ProfileGalleryType =
    when (screenCode) {
        ProfileGalleryType.CLASSIC.screenCode -> ProfileGalleryType.CLASSIC
        ProfileGalleryType.MODERN.screenCode -> ProfileGalleryType.MODERN
        else -> {
            ProfileGalleryType.CLASSIC
        }
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
        else -> {
            BooleanType.ALWAYS
        }
    }

fun parseBooleanType(screenCode: Int): BooleanType =
    when (screenCode) {
        BooleanType.ALWAYS.screenCode -> BooleanType.ALWAYS
        BooleanType.NEVER.screenCode -> BooleanType.NEVER
        else -> {
            BooleanType.ALWAYS
        }
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
        else -> {
            WarningType.WARN
        }
    }

fun parseWarningType(code: Boolean?): WarningType =
    when (code) {
        WarningType.WARN.prefCode -> WarningType.WARN
        WarningType.HIDE.prefCode -> WarningType.HIDE
        WarningType.SHOW.prefCode -> WarningType.SHOW
        else -> {
            WarningType.WARN
        }
    }
