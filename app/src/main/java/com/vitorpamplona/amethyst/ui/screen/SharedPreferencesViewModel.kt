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

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.window.layout.DisplayFeature
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.model.BooleanType
import com.vitorpamplona.amethyst.model.ConnectivityType
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.model.Settings
import com.vitorpamplona.amethyst.model.ThemeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Stable
class SettingsState() {
    var theme by mutableStateOf(ThemeType.SYSTEM)
    var language by mutableStateOf<String?>(null)

    var automaticallyShowImages by mutableStateOf(ConnectivityType.ALWAYS)
    var automaticallyStartPlayback by mutableStateOf(ConnectivityType.ALWAYS)
    var automaticallyShowUrlPreview by mutableStateOf(ConnectivityType.ALWAYS)
    var automaticallyHideNavigationBars by mutableStateOf(BooleanType.ALWAYS)
    var automaticallyShowProfilePictures by mutableStateOf(ConnectivityType.ALWAYS)
    var dontShowPushNotificationSelector by mutableStateOf<Boolean>(false)
    var dontAskForNotificationPermissions by mutableStateOf<Boolean>(false)
    var featureSet by mutableStateOf(FeatureSetType.COMPLETE)

    var isOnMobileData: State<Boolean> = mutableStateOf(false)

    var windowSizeClass = mutableStateOf<WindowSizeClass?>(null)
    var displayFeatures = mutableStateOf<List<DisplayFeature>>(emptyList())

    val showProfilePictures =
        derivedStateOf {
            when (automaticallyShowProfilePictures) {
                ConnectivityType.WIFI_ONLY -> !isOnMobileData.value
                ConnectivityType.NEVER -> false
                ConnectivityType.ALWAYS -> true
            }
        }

    val showUrlPreview =
        derivedStateOf {
            when (automaticallyShowUrlPreview) {
                ConnectivityType.WIFI_ONLY -> !isOnMobileData.value
                ConnectivityType.NEVER -> false
                ConnectivityType.ALWAYS -> true
            }
        }

    val startVideoPlayback =
        derivedStateOf {
            when (automaticallyStartPlayback) {
                ConnectivityType.WIFI_ONLY -> !isOnMobileData.value
                ConnectivityType.NEVER -> false
                ConnectivityType.ALWAYS -> true
            }
        }

    val showImages =
        derivedStateOf {
            when (automaticallyShowImages) {
                ConnectivityType.WIFI_ONLY -> !isOnMobileData.value
                ConnectivityType.NEVER -> false
                ConnectivityType.ALWAYS -> true
            }
        }
}

@Stable
class SharedPreferencesViewModel : ViewModel() {
    val sharedPrefs: SettingsState = SettingsState()

    fun init() {
        viewModelScope.launch(Dispatchers.IO) {
            val savedSettings =
                LocalPreferences.loadSharedSettings() ?: Settings()

            sharedPrefs.theme = savedSettings.theme
            sharedPrefs.language = savedSettings.preferredLanguage
            sharedPrefs.automaticallyShowImages = savedSettings.automaticallyShowImages
            sharedPrefs.automaticallyStartPlayback = savedSettings.automaticallyStartPlayback
            sharedPrefs.automaticallyShowUrlPreview = savedSettings.automaticallyShowUrlPreview
            sharedPrefs.automaticallyHideNavigationBars = savedSettings.automaticallyHideNavigationBars
            sharedPrefs.automaticallyShowProfilePictures = savedSettings.automaticallyShowProfilePictures
            sharedPrefs.dontShowPushNotificationSelector = savedSettings.dontShowPushNotificationSelector
            sharedPrefs.dontAskForNotificationPermissions = savedSettings.dontAskForNotificationPermissions
            sharedPrefs.featureSet = savedSettings.featureSet

            updateLanguageInTheUI()
        }
    }

    fun updateTheme(newTheme: ThemeType) {
        if (sharedPrefs.theme != newTheme) {
            sharedPrefs.theme = newTheme

            saveSharedSettings()
        }
    }

    fun updateLanguage(newLanguage: String?) {
        if (sharedPrefs.language != newLanguage) {
            sharedPrefs.language = newLanguage
            updateLanguageInTheUI()
            saveSharedSettings()
        }
    }

    fun updateLanguageInTheUI() {
        if (sharedPrefs.language != null) {
            viewModelScope.launch(Dispatchers.Main) {
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(sharedPrefs.language),
                )
            }
        }
    }

    fun updateAutomaticallyStartPlayback(newAutomaticallyStartPlayback: ConnectivityType) {
        if (sharedPrefs.automaticallyStartPlayback != newAutomaticallyStartPlayback) {
            sharedPrefs.automaticallyStartPlayback = newAutomaticallyStartPlayback
            saveSharedSettings()
        }
    }

    fun updateAutomaticallyShowUrlPreview(newAutomaticallyShowUrlPreview: ConnectivityType) {
        if (sharedPrefs.automaticallyShowUrlPreview != newAutomaticallyShowUrlPreview) {
            sharedPrefs.automaticallyShowUrlPreview = newAutomaticallyShowUrlPreview
            saveSharedSettings()
        }
    }

    fun updateAutomaticallyShowProfilePicture(newAutomaticallyShowProfilePictures: ConnectivityType) {
        if (sharedPrefs.automaticallyShowProfilePictures != newAutomaticallyShowProfilePictures) {
            sharedPrefs.automaticallyShowProfilePictures = newAutomaticallyShowProfilePictures
            saveSharedSettings()
        }
    }

    fun updateAutomaticallyHideNavBars(newAutomaticallyHideHavBars: BooleanType) {
        if (sharedPrefs.automaticallyHideNavigationBars != newAutomaticallyHideHavBars) {
            sharedPrefs.automaticallyHideNavigationBars = newAutomaticallyHideHavBars
            saveSharedSettings()
        }
    }

    fun updateAutomaticallyShowImages(newAutomaticallyShowImages: ConnectivityType) {
        if (sharedPrefs.automaticallyShowImages != newAutomaticallyShowImages) {
            sharedPrefs.automaticallyShowImages = newAutomaticallyShowImages
            saveSharedSettings()
        }
    }

    fun updateFeatureSetType(newFeatureSetType: FeatureSetType) {
        if (sharedPrefs.featureSet != newFeatureSetType) {
            sharedPrefs.featureSet = newFeatureSetType
            saveSharedSettings()
        }
    }

    fun dontShowPushNotificationSelector() {
        if (sharedPrefs.dontShowPushNotificationSelector == false) {
            sharedPrefs.dontShowPushNotificationSelector = true
            saveSharedSettings()
        }
    }

    fun dontAskForNotificationPermissions() {
        if (sharedPrefs.dontAskForNotificationPermissions == false) {
            sharedPrefs.dontAskForNotificationPermissions = true
            saveSharedSettings()
        }
    }

    fun updateConnectivityStatusState(isOnMobileDataState: State<Boolean>) {
        if (sharedPrefs.isOnMobileData != isOnMobileDataState) {
            sharedPrefs.isOnMobileData = isOnMobileDataState
        }
    }

    fun updateDisplaySettings(
        windowSizeClass: WindowSizeClass,
        displayFeatures: List<DisplayFeature>,
    ) {
        if (sharedPrefs.windowSizeClass.value != windowSizeClass) {
            sharedPrefs.windowSizeClass.value = windowSizeClass
        }
        if (sharedPrefs.displayFeatures.value != displayFeatures) {
            sharedPrefs.displayFeatures.value = displayFeatures
        }
    }

    fun saveSharedSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            LocalPreferences.saveSharedSettings(
                Settings(
                    sharedPrefs.theme,
                    sharedPrefs.language,
                    sharedPrefs.automaticallyShowImages,
                    sharedPrefs.automaticallyStartPlayback,
                    sharedPrefs.automaticallyShowUrlPreview,
                    sharedPrefs.automaticallyHideNavigationBars,
                    sharedPrefs.automaticallyShowProfilePictures,
                    sharedPrefs.dontShowPushNotificationSelector,
                    sharedPrefs.dontAskForNotificationPermissions,
                    sharedPrefs.featureSet,
                ),
            )
        }
    }
}
