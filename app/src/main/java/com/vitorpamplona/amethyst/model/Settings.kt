/**
 * Copyright (c) 2024 Vitor Pamplona
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

@Stable
data class Settings(
    val theme: ThemeType = ThemeType.SYSTEM,
    val preferredLanguage: String? = null,
    val automaticallyShowImages: ConnectivityType = ConnectivityType.ALWAYS,
    val automaticallyStartPlayback: ConnectivityType = ConnectivityType.ALWAYS,
    val automaticallyShowUrlPreview: ConnectivityType = ConnectivityType.ALWAYS,
    val automaticallyHideNavigationBars: BooleanType = BooleanType.ALWAYS,
    val automaticallyShowProfilePictures: ConnectivityType = ConnectivityType.ALWAYS,
    val dontShowPushNotificationSelector: Boolean = false,
    val dontAskForNotificationPermissions: Boolean = false,
    val featureSet: FeatureSetType = FeatureSetType.COMPLETE,
)

enum class ThemeType(val screenCode: Int, val resourceId: Int) {
    SYSTEM(0, R.string.system),
    LIGHT(1, R.string.light),
    DARK(2, R.string.dark),
}

fun parseThemeType(code: Int?): ThemeType {
    return when (code) {
        ThemeType.SYSTEM.screenCode -> ThemeType.SYSTEM
        ThemeType.LIGHT.screenCode -> ThemeType.LIGHT
        ThemeType.DARK.screenCode -> ThemeType.DARK
        else -> {
            ThemeType.SYSTEM
        }
    }
}

enum class ConnectivityType(val prefCode: Boolean?, val screenCode: Int, val resourceId: Int) {
    ALWAYS(null, 0, R.string.connectivity_type_always),
    WIFI_ONLY(true, 1, R.string.connectivity_type_wifi_only),
    NEVER(false, 2, R.string.connectivity_type_never),
}

enum class FeatureSetType(val screenCode: Int, val resourceId: Int) {
    COMPLETE(0, R.string.ui_feature_set_type_complete),
    SIMPLIFIED(1, R.string.ui_feature_set_type_simplified),
}

fun parseConnectivityType(code: Boolean?): ConnectivityType {
    return when (code) {
        ConnectivityType.ALWAYS.prefCode -> ConnectivityType.ALWAYS
        ConnectivityType.WIFI_ONLY.prefCode -> ConnectivityType.WIFI_ONLY
        ConnectivityType.NEVER.prefCode -> ConnectivityType.NEVER
        else -> {
            ConnectivityType.ALWAYS
        }
    }
}

fun parseConnectivityType(screenCode: Int): ConnectivityType {
    return when (screenCode) {
        ConnectivityType.ALWAYS.screenCode -> ConnectivityType.ALWAYS
        ConnectivityType.WIFI_ONLY.screenCode -> ConnectivityType.WIFI_ONLY
        ConnectivityType.NEVER.screenCode -> ConnectivityType.NEVER
        else -> {
            ConnectivityType.ALWAYS
        }
    }
}

fun parseFeatureSetType(screenCode: Int): FeatureSetType {
    return when (screenCode) {
        FeatureSetType.COMPLETE.screenCode -> FeatureSetType.COMPLETE
        FeatureSetType.SIMPLIFIED.screenCode -> FeatureSetType.SIMPLIFIED
        else -> {
            FeatureSetType.COMPLETE
        }
    }
}

enum class BooleanType(val prefCode: Boolean?, val screenCode: Int, val reourceId: Int) {
    ALWAYS(null, 0, R.string.connectivity_type_always),
    NEVER(false, 1, R.string.connectivity_type_never),
}

fun parseBooleanType(code: Boolean?): BooleanType {
    return when (code) {
        BooleanType.ALWAYS.prefCode -> BooleanType.ALWAYS
        BooleanType.NEVER.prefCode -> BooleanType.NEVER
        else -> {
            BooleanType.ALWAYS
        }
    }
}

fun parseBooleanType(screenCode: Int): BooleanType {
    return when (screenCode) {
        BooleanType.ALWAYS.screenCode -> BooleanType.ALWAYS
        BooleanType.NEVER.screenCode -> BooleanType.NEVER
        else -> {
            BooleanType.ALWAYS
        }
    }
}
