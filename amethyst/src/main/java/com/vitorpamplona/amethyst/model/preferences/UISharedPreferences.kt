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
package com.vitorpamplona.amethyst.model.preferences

import android.content.Context
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
import com.vitorpamplona.amethyst.ui.navigation.bottombars.DefaultBottomBarItems
import com.vitorpamplona.amethyst.ui.navigation.bottombars.NavBarItem
import com.vitorpamplona.quartz.utils.Log
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
import kotlin.coroutines.cancellation.CancellationException

val Context.sharedPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "shared_settings")

@Stable
class UiSharedPreferences(
    prefs: UiSettings,
    val context: Context,
    val scope: CoroutineScope,
) {
    // UI Preferences. Makes sure to wait for it to avoid blinking themes and language preferences
    val value = UiSettingsFlow.build(prefs)

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
            .onEach {
                save(it, context)
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                value.toSettings(),
            )

    companion object {
        // loads faster when individualized
        val UI_THEME = stringPreferencesKey("ui.theme")
        val UI_LANGUAGE = stringPreferencesKey("ui.language")
        val UI_SHOW_IMAGES = stringPreferencesKey("ui.show_images")
        val UI_START_PLAYBACK = stringPreferencesKey("ui.start_playback")
        val UI_PLAY_VIDEOS = stringPreferencesKey("ui.play_videos")
        val UI_SHOW_URL_PREVIEW = stringPreferencesKey("ui.show_url_preview")
        val UI_HIDE_NAVIGATION_BARS = stringPreferencesKey("ui.hide_navigation_bars")
        val UI_SHOW_PROFILE_PICTURES = stringPreferencesKey("ui.show_profile_pictures")
        val UI_DONT_SHOW_PUSH_NOTIFICATION_SELECTOR = booleanPreferencesKey("ui.dont_show_push_notification_selector")
        val UI_DONT_ASK_FOR_NOTIFICATION_PERMISSIONS = booleanPreferencesKey("ui.dont_ask_for_notification_permissions")
        val UI_FEATURE_SET = stringPreferencesKey("ui.feature_set")
        val UI_GALLERY_SET = stringPreferencesKey("ui.gallery_set")
        val UI_PROPOSE_AI_IMPROVEMENTS = stringPreferencesKey("ui.propose_ai_improvements")
        val UI_USE_TRACKED_BROADCASTS = stringPreferencesKey("ui.use_tracked_broadcasts")
        val UI_AUTOMATICALLY_CREATE_DRAFTS = stringPreferencesKey("ui.automatically_create_drafts")
        val UI_BOTTOM_BAR_ITEMS = stringPreferencesKey("ui.bottom_bar_items")
        val UI_SHOW_HOME_NEW_THREADS_TAB = booleanPreferencesKey("ui.show_home_new_threads_tab")
        val UI_SHOW_HOME_CONVERSATIONS_TAB = booleanPreferencesKey("ui.show_home_conversations_tab")
        val UI_SHOW_HOME_EVERYTHING_TAB = booleanPreferencesKey("ui.show_home_everything_tab")
        val UI_SHOW_PROFILE_BADGES = booleanPreferencesKey("ui.show_profile_badges")
        val UI_SHOW_PROFILE_APP_RECOMMENDATIONS = booleanPreferencesKey("ui.show_profile_app_recommendations")
        val UI_SHOW_PROFILE_ZAP_RECEIVED_FEED = booleanPreferencesKey("ui.show_profile_zap_received_feed")
        val UI_SHOW_PROFILE_FOLLOWERS_FEED = booleanPreferencesKey("ui.show_profile_followers_feed")

        suspend fun uiPreferences(context: Context): UiSettings? =
            try {
                // Get the preference flow and take the first value.
                val preferences = context.sharedPreferencesDataStore.data.first()

                val featureSet = preferences[UI_FEATURE_SET]?.let { FeatureSetType.valueOf(it) } ?: FeatureSetType.SIMPLIFIED

                UiSettings(
                    theme = preferences[UI_THEME]?.let { ThemeType.valueOf(it) } ?: ThemeType.SYSTEM,
                    preferredLanguage = preferences[UI_LANGUAGE]?.ifBlank { null },
                    automaticallyShowImages = preferences[UI_SHOW_IMAGES]?.let { ConnectivityType.valueOf(it) } ?: ConnectivityType.ALWAYS,
                    automaticallyStartPlayback = preferences[UI_START_PLAYBACK]?.let { ConnectivityType.valueOf(it) } ?: ConnectivityType.ALWAYS,
                    automaticallyPlayVideos = preferences[UI_PLAY_VIDEOS]?.let { BooleanType.valueOf(it) } ?: BooleanType.ALWAYS,
                    automaticallyShowUrlPreview = preferences[UI_SHOW_URL_PREVIEW]?.let { ConnectivityType.valueOf(it) } ?: ConnectivityType.ALWAYS,
                    automaticallyHideNavigationBars = preferences[UI_HIDE_NAVIGATION_BARS]?.let { BooleanType.valueOf(it) } ?: BooleanType.ALWAYS,
                    automaticallyShowProfilePictures = preferences[UI_SHOW_PROFILE_PICTURES]?.let { ConnectivityType.valueOf(it) } ?: ConnectivityType.ALWAYS,
                    dontShowPushNotificationSelector = preferences[UI_DONT_SHOW_PUSH_NOTIFICATION_SELECTOR] ?: false,
                    dontAskForNotificationPermissions = preferences[UI_DONT_ASK_FOR_NOTIFICATION_PERMISSIONS] ?: false,
                    featureSet = featureSet,
                    gallerySet = preferences[UI_GALLERY_SET]?.let { ProfileGalleryType.valueOf(it) } ?: ProfileGalleryType.CLASSIC,
                    automaticallyProposeAiImprovements = preferences[UI_PROPOSE_AI_IMPROVEMENTS]?.let { BooleanType.valueOf(it) } ?: BooleanType.ALWAYS,
                    useTrackedBroadcasts =
                        preferences[UI_USE_TRACKED_BROADCASTS]?.let { BooleanType.valueOf(it) }
                            ?: if (featureSet == FeatureSetType.COMPLETE) BooleanType.ALWAYS else BooleanType.NEVER,
                    automaticallyCreateDrafts = preferences[UI_AUTOMATICALLY_CREATE_DRAFTS]?.let { BooleanType.valueOf(it) } ?: BooleanType.ALWAYS,
                    bottomBarItems = preferences[UI_BOTTOM_BAR_ITEMS]?.let { decodeBottomBarItems(it) } ?: DefaultBottomBarItems,
                    showHomeNewThreadsTab = preferences[UI_SHOW_HOME_NEW_THREADS_TAB] ?: true,
                    showHomeConversationsTab = preferences[UI_SHOW_HOME_CONVERSATIONS_TAB] ?: true,
                    showHomeEverythingTab = preferences[UI_SHOW_HOME_EVERYTHING_TAB] ?: false,
                    showProfileBadges = preferences[UI_SHOW_PROFILE_BADGES] ?: true,
                    showProfileAppRecommendations = preferences[UI_SHOW_PROFILE_APP_RECOMMENDATIONS] ?: true,
                    showProfileZapReceivedFeed = preferences[UI_SHOW_PROFILE_ZAP_RECEIVED_FEED] ?: true,
                    showProfileFollowersFeed = preferences[UI_SHOW_PROFILE_FOLLOWERS_FEED] ?: true,
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Log any errors that occur while reading the DataStore.
                Log.e("SharedPreferences") { "Error reading DataStore preferences: ${e.message}" }

                try {
                    val oldVersion = LocalPreferences.loadSharedSettings()
                    if (oldVersion != null) {
                        save(oldVersion, context)
                    }
                    oldVersion
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    null
                }
            }

        suspend fun save(
            sharedSettings: UiSettings,
            context: Context,
        ) {
            try {
                context.sharedPreferencesDataStore.edit { preferences ->
                    preferences[UI_THEME] = sharedSettings.theme.name
                    preferences[UI_LANGUAGE] = sharedSettings.preferredLanguage ?: ""
                    preferences[UI_SHOW_IMAGES] = sharedSettings.automaticallyShowImages.name
                    preferences[UI_START_PLAYBACK] = sharedSettings.automaticallyStartPlayback.name
                    preferences[UI_PLAY_VIDEOS] = sharedSettings.automaticallyPlayVideos.name
                    preferences[UI_SHOW_URL_PREVIEW] = sharedSettings.automaticallyShowUrlPreview.name
                    preferences[UI_HIDE_NAVIGATION_BARS] = sharedSettings.automaticallyHideNavigationBars.name
                    preferences[UI_SHOW_PROFILE_PICTURES] = sharedSettings.automaticallyShowProfilePictures.name
                    preferences[UI_DONT_SHOW_PUSH_NOTIFICATION_SELECTOR] = sharedSettings.dontShowPushNotificationSelector
                    preferences[UI_DONT_ASK_FOR_NOTIFICATION_PERMISSIONS] = sharedSettings.dontAskForNotificationPermissions
                    preferences[UI_FEATURE_SET] = sharedSettings.featureSet.name
                    preferences[UI_GALLERY_SET] = sharedSettings.gallerySet.name
                    preferences[UI_PROPOSE_AI_IMPROVEMENTS] = sharedSettings.automaticallyProposeAiImprovements.name
                    preferences[UI_USE_TRACKED_BROADCASTS] = sharedSettings.useTrackedBroadcasts.name
                    preferences[UI_AUTOMATICALLY_CREATE_DRAFTS] = sharedSettings.automaticallyCreateDrafts.name
                    preferences[UI_BOTTOM_BAR_ITEMS] = sharedSettings.bottomBarItems.joinToString(",") { it.name }
                    preferences[UI_SHOW_HOME_NEW_THREADS_TAB] = sharedSettings.showHomeNewThreadsTab
                    preferences[UI_SHOW_HOME_CONVERSATIONS_TAB] = sharedSettings.showHomeConversationsTab
                    preferences[UI_SHOW_HOME_EVERYTHING_TAB] = sharedSettings.showHomeEverythingTab
                    preferences[UI_SHOW_PROFILE_BADGES] = sharedSettings.showProfileBadges
                    preferences[UI_SHOW_PROFILE_APP_RECOMMENDATIONS] = sharedSettings.showProfileAppRecommendations
                    preferences[UI_SHOW_PROFILE_ZAP_RECEIVED_FEED] = sharedSettings.showProfileZapReceivedFeed
                    preferences[UI_SHOW_PROFILE_FOLLOWERS_FEED] = sharedSettings.showProfileFollowersFeed
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Log any errors that occur while reading the DataStore.
                Log.e("SharedPreferences") { "Error saving DataStore preferences: ${e.message}" }
            }
        }

        private fun decodeBottomBarItems(raw: String): List<NavBarItem> {
            if (raw.isEmpty()) return emptyList()
            return raw
                .split(",")
                .mapNotNull { name ->
                    try {
                        NavBarItem.valueOf(name)
                    } catch (_: IllegalArgumentException) {
                        null
                    }
                }
        }
    }
}
