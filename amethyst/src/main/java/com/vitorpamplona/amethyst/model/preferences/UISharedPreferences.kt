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
package com.vitorpamplona.amethyst.model.preferences

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Stable
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.model.BooleanType
import com.vitorpamplona.amethyst.model.ConnectivityType
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.model.ProfileGalleryType
import com.vitorpamplona.amethyst.model.ThemeType
import com.vitorpamplona.amethyst.model.UiSettings
import com.vitorpamplona.amethyst.model.UiSettingsFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.cancellation.CancellationException

val Context.sharedPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "shared_settings")

@Stable
class UiSharedPreferences(
    val context: Context,
    val scope: CoroutineScope,
) {
    companion object {
        // loads faster when individualized
        val UI_THEME = stringPreferencesKey("ui.theme")
        val UI_LANGUAGE = stringPreferencesKey("ui.language")
        val UI_SHOW_IMAGES = stringPreferencesKey("ui.show_images")
        val UI_START_PLAYBACK = stringPreferencesKey("ui.start_playback")
        val UI_SHOW_URL_PREVIEW = stringPreferencesKey("ui.show_url_preview")
        val UI_HIDE_NAVIGATION_BARS = stringPreferencesKey("ui.hide_navigation_bars")
        val UI_SHOW_PROFILE_PICTURES = stringPreferencesKey("ui.show_profile_pictures")
        val UI_DONT_SHOW_PUSH_NOTIFICATION_SELECTOR = booleanPreferencesKey("ui.dont_show_push_notification_selector")
        val UI_DONT_ASK_FOR_NOTIFICATION_PERMISSIONS = booleanPreferencesKey("ui.dont_ask_for_notification_permissions")
        val UI_FEATURE_SET = stringPreferencesKey("ui.feature_set")
        val UI_GALLERY_SET = stringPreferencesKey("ui.gallery_set")
    }

    // UI Preferences. Makes sure to wait for it to avoid blinking themes and language preferences
    val value =
        runBlocking {
            UiSettingsFlow.build(uiPreferences() ?: UiSettings())
        }

    val languageUpdate =
        value.preferredLanguage
            .onEach { language ->
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(language),
                )
            }.flowOn(Dispatchers.Main)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                value.toSettings(),
            )

    @OptIn(FlowPreview::class)
    val saving =
        value.propertyWatchFlow
            .debounce(1000)
            .distinctUntilChanged()
            .onEach(::save)
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                value.toSettings(),
            )

    suspend fun uiPreferences(): UiSettings? =
        try {
            // Get the preference flow and take the first value.
            val preferences = context.sharedPreferencesDataStore.data.first()

            UiSettings(
                theme = preferences[UI_THEME]?.let { ThemeType.valueOf(it) } ?: ThemeType.SYSTEM,
                preferredLanguage = preferences[UI_LANGUAGE]?.ifBlank { null },
                automaticallyShowImages = preferences[UI_SHOW_IMAGES]?.let { ConnectivityType.valueOf(it) } ?: ConnectivityType.ALWAYS,
                automaticallyStartPlayback = preferences[UI_START_PLAYBACK]?.let { ConnectivityType.valueOf(it) } ?: ConnectivityType.ALWAYS,
                automaticallyShowUrlPreview = preferences[UI_SHOW_URL_PREVIEW]?.let { ConnectivityType.valueOf(it) } ?: ConnectivityType.ALWAYS,
                automaticallyHideNavigationBars = preferences[UI_HIDE_NAVIGATION_BARS]?.let { BooleanType.valueOf(it) } ?: BooleanType.ALWAYS,
                automaticallyShowProfilePictures = preferences[UI_SHOW_PROFILE_PICTURES]?.let { ConnectivityType.valueOf(it) } ?: ConnectivityType.ALWAYS,
                dontShowPushNotificationSelector = preferences[UI_DONT_SHOW_PUSH_NOTIFICATION_SELECTOR] ?: false,
                dontAskForNotificationPermissions = preferences[UI_DONT_ASK_FOR_NOTIFICATION_PERMISSIONS] ?: false,
                featureSet = preferences[UI_FEATURE_SET]?.let { FeatureSetType.valueOf(it) } ?: FeatureSetType.SIMPLIFIED,
                gallerySet = preferences[UI_GALLERY_SET]?.let { ProfileGalleryType.valueOf(it) } ?: ProfileGalleryType.CLASSIC,
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Log any errors that occur while reading the DataStore.
            Log.e("SharedPreferences", "Error reading DataStore preferences: ${e.message}")

            try {
                val oldVersion = LocalPreferences.loadSharedSettings()
                if (oldVersion != null) {
                    save(oldVersion)
                }
                oldVersion
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
        }

    suspend fun save(sharedSettings: UiSettings) {
        try {
            context.sharedPreferencesDataStore.edit { preferences ->
                preferences[UI_THEME] = sharedSettings.theme.name
                preferences[UI_LANGUAGE] = sharedSettings.preferredLanguage ?: ""
                preferences[UI_SHOW_IMAGES] = sharedSettings.automaticallyShowImages.name
                preferences[UI_START_PLAYBACK] = sharedSettings.automaticallyStartPlayback.name
                preferences[UI_SHOW_URL_PREVIEW] = sharedSettings.automaticallyShowUrlPreview.name
                preferences[UI_HIDE_NAVIGATION_BARS] = sharedSettings.automaticallyHideNavigationBars.name
                preferences[UI_SHOW_PROFILE_PICTURES] = sharedSettings.automaticallyShowProfilePictures.name
                preferences[UI_DONT_SHOW_PUSH_NOTIFICATION_SELECTOR] = sharedSettings.dontShowPushNotificationSelector
                preferences[UI_DONT_ASK_FOR_NOTIFICATION_PERMISSIONS] = sharedSettings.dontAskForNotificationPermissions
                preferences[UI_FEATURE_SET] = sharedSettings.featureSet.name
                preferences[UI_GALLERY_SET] = sharedSettings.gallerySet.name
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Log any errors that occur while reading the DataStore.
            Log.e("SharedPreferences", "Error saving DataStore preferences: ${e.message}")
        }
    }
}
