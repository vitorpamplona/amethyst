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
package com.vitorpamplona.amethyst.ui.screen

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.window.layout.DisplayFeature
import com.vitorpamplona.amethyst.model.BooleanType
import com.vitorpamplona.amethyst.model.ConnectivityType
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.model.ProfileGalleryType
import com.vitorpamplona.amethyst.model.ThemeType

@Stable
class SharedSettingsState {
    var theme by mutableStateOf(ThemeType.SYSTEM)
    var language by mutableStateOf<String?>(null)

    var automaticallyShowImages by mutableStateOf(ConnectivityType.ALWAYS)
    var automaticallyStartPlayback by mutableStateOf(ConnectivityType.ALWAYS)
    var automaticallyShowUrlPreview by mutableStateOf(ConnectivityType.ALWAYS)
    var automaticallyHideNavigationBars by mutableStateOf(BooleanType.ALWAYS)
    var automaticallyShowProfilePictures by mutableStateOf(ConnectivityType.ALWAYS)
    var dontShowPushNotificationSelector by mutableStateOf<Boolean>(false)
    var dontAskForNotificationPermissions by mutableStateOf<Boolean>(false)
    var featureSet by mutableStateOf(FeatureSetType.SIMPLIFIED)
    var gallerySet by mutableStateOf(ProfileGalleryType.CLASSIC)

    var isOnMobileOrMeteredConnection by mutableStateOf(false)
    var currentNetworkId by mutableStateOf(0L)

    var windowSizeClass = mutableStateOf<WindowSizeClass?>(null)
    var displayFeatures = mutableStateOf<List<DisplayFeature>>(emptyList())

    val showProfilePictures =
        derivedStateOf {
            when (automaticallyShowProfilePictures) {
                ConnectivityType.WIFI_ONLY -> !isOnMobileOrMeteredConnection
                ConnectivityType.NEVER -> false
                ConnectivityType.ALWAYS -> true
            }
        }

    val modernGalleryStyle =
        derivedStateOf {
            when (gallerySet) {
                ProfileGalleryType.CLASSIC -> false
                ProfileGalleryType.MODERN -> true
            }
        }

    val showUrlPreview =
        derivedStateOf {
            when (automaticallyShowUrlPreview) {
                ConnectivityType.WIFI_ONLY -> !isOnMobileOrMeteredConnection
                ConnectivityType.NEVER -> false
                ConnectivityType.ALWAYS -> true
            }
        }

    val startVideoPlayback =
        derivedStateOf {
            when (automaticallyStartPlayback) {
                ConnectivityType.WIFI_ONLY -> !isOnMobileOrMeteredConnection
                ConnectivityType.NEVER -> false
                ConnectivityType.ALWAYS -> true
            }
        }

    val showImages =
        derivedStateOf {
            when (automaticallyShowImages) {
                ConnectivityType.WIFI_ONLY -> !isOnMobileOrMeteredConnection
                ConnectivityType.NEVER -> false
                ConnectivityType.ALWAYS -> true
            }
        }
}
