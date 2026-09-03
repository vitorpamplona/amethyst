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

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Stable
import androidx.core.content.getSystemService
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.model.AccentColorType
import com.vitorpamplona.amethyst.model.BooleanType
import com.vitorpamplona.amethyst.model.ConnectivityType
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.model.FontFamilyType
import com.vitorpamplona.amethyst.model.FontSizeType
import com.vitorpamplona.amethyst.model.ProfileGalleryType
import com.vitorpamplona.amethyst.model.ThemeType
import com.vitorpamplona.amethyst.model.UiSettings
import com.vitorpamplona.amethyst.model.UiSettingsFlow
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
import kotlinx.coroutines.withContext
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
            .onEach { language -> applyLanguage(language) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                value.toSettings(),
            )

    val nightModeUpdate =
        value.theme
            .onEach { theme -> applyNightMode(theme) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                prefs.theme,
            )

    /**
     * Mirrors the in-app theme choice into the system's *per-application* night mode, so the
     * launch splash agrees with a theme that is pinned against the phone's own light/dark setting.
     *
     * The system composites the splash from the manifest theme before the process starts, resolving
     * it against this app's configuration -- so day/night resource qualifiers alone can only ever
     * follow the phone. [UiModeManager.setApplicationNightMode] commits a *persisted per-package
     * configuration override* (UiModeManagerService hands it to
     * ActivityTaskManagerInternal.PackageConfigurationUpdater), which the system then applies when
     * it launches the app. That is what carries a pinned LIGHT/DARK choice into the splash, from
     * the next cold start onwards -- the current launch is already painted.
     *
     * This is deliberately [UiModeManager.setApplicationNightMode] and not
     * [UiModeManager.setNightMode]: the latter changes the night mode for every app on the device
     * and is gated behind MODIFY_DAY_NIGHT_MODE, which this app does not hold -- that call was a
     * silent no-op and was removed. The per-application setter is the documented app-local
     * alternative and is not permission-checked; UiModeManagerService only validates the argument.
     *
     * MODE_NIGHT_AUTO is how [ThemeType.SYSTEM] is expressed: the service maps everything other
     * than YES/NO onto `Configuration.UI_MODE_NIGHT_UNDEFINED`, which clears the override and lets
     * the app fall back to the device configuration.
     *
     * Deduplicated against the last mode this app successfully applied, because the call is a
     * Binder round trip that pushes a configuration change into every running activity of the
     * package. The applied value is recorded only after the call returns, so a failure is retried
     * on the next launch rather than being remembered as done. MainActivity declares `uiMode` in
     * its `configChanges`, so the resulting change is delivered to `onConfigurationChanged` and
     * does not recreate the activity.
     */
    private suspend fun applyNightMode(theme: ThemeType) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val mode =
            when (theme) {
                ThemeType.DARK -> UiModeManager.MODE_NIGHT_YES
                ThemeType.LIGHT -> UiModeManager.MODE_NIGHT_NO
                ThemeType.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            }

        if (context.sharedPreferencesDataStore.data.first()[UI_APPLIED_NIGHT_MODE] == mode) return

        try {
            context.getSystemService<UiModeManager>()?.setApplicationNightMode(mode)
            context.sharedPreferencesDataStore.edit { it[UI_APPLIED_NIGHT_MODE] = mode }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("UiSharedPreferences", "Could not apply the per-application night mode", e)
        }
    }

    /**
     * Pushes the preferred language into AppCompat, skipping the call when the app already
     * runs in that locale.
     *
     * On API 33+, [AppCompatDelegate.setApplicationLocales] does not deduplicate: every call
     * is a blocking Binder round trip into the system's LocaleManagerService, which commits a
     * SharedPreferences file (and, on Samsung ROMs, appends to a log file) before returning.
     * That was measured at ~220ms on a Galaxy device, charged to the calling thread. Since
     * this flow starts eagerly, the app paid it on the main thread on every launch, even when
     * the locale had not changed since the previous run -- and StrictMode reported it as a
     * DiskReadViolation via the Binder call.
     *
     * [AppCompatDelegate.getApplicationLocales] is `@AnyThread` and only reads state, so the
     * comparison runs off the main thread. Actual changes still hop to the main thread:
     * below API 33 AppCompat applies them in process by reconfiguring (and possibly
     * recreating) the active activities.
     */
    private suspend fun applyLanguage(language: String?) {
        val newLocales = LocaleListCompat.forLanguageTags(language)
        if (newLocales == AppCompatDelegate.getApplicationLocales()) return

        withContext(Dispatchers.Main) {
            AppCompatDelegate.setApplicationLocales(newLocales)
        }
    }

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
        val UI_SHOW_HOME_NEW_THREADS_TAB = booleanPreferencesKey("ui.show_home_new_threads_tab")
        val UI_SHOW_HOME_CONVERSATIONS_TAB = booleanPreferencesKey("ui.show_home_conversations_tab")
        val UI_SHOW_HOME_EVERYTHING_TAB = booleanPreferencesKey("ui.show_home_everything_tab")
        val UI_SHOW_PROFILE_BADGES = booleanPreferencesKey("ui.show_profile_badges")
        val UI_SHOW_PROFILE_APP_RECOMMENDATIONS = booleanPreferencesKey("ui.show_profile_app_recommendations")
        val UI_SHOW_PROFILE_ZAP_RECEIVED_FEED = booleanPreferencesKey("ui.show_profile_zap_received_feed")
        val UI_SHOW_PROFILE_FOLLOWERS_FEED = booleanPreferencesKey("ui.show_profile_followers_feed")
        val UI_DONT_SHOW_ONCHAIN_PUBLIC_WARNING = booleanPreferencesKey("ui.dont_show_onchain_public_warning")
        val UI_SUGGEST_WORKOUTS_FROM_HEALTH_CONNECT = stringPreferencesKey("ui.suggest_workouts_from_health_connect")
        val UI_ACCENT_COLOR = stringPreferencesKey("ui.accent_color")
        val UI_FONT_FAMILY = stringPreferencesKey("ui.font_family")
        val UI_FONT_SIZE = stringPreferencesKey("ui.font_size")
        val UI_COMPOSE_SIGNATURE = stringPreferencesKey("ui.compose_signature")
        val UI_SHOW_ONCHAIN_WALLET = booleanPreferencesKey("ui.show_onchain_wallet")

        /**
         * Bookkeeping for [applyNightMode], not a user setting: the per-application night mode
         * this app last handed to UiModeManager. The system persists its own copy until the app
         * is uninstalled or its data cleared -- which also clears this store, so the two stay in
         * step. Deliberately kept out of [UiSettings] so it is not part of the settings model.
         */
        val UI_APPLIED_NIGHT_MODE = intPreferencesKey("ui.applied_night_mode")

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
                    showHomeNewThreadsTab = preferences[UI_SHOW_HOME_NEW_THREADS_TAB] ?: true,
                    showHomeConversationsTab = preferences[UI_SHOW_HOME_CONVERSATIONS_TAB] ?: true,
                    showHomeEverythingTab = preferences[UI_SHOW_HOME_EVERYTHING_TAB] ?: false,
                    showProfileBadges = preferences[UI_SHOW_PROFILE_BADGES] ?: true,
                    showProfileAppRecommendations = preferences[UI_SHOW_PROFILE_APP_RECOMMENDATIONS] ?: true,
                    showProfileZapReceivedFeed = preferences[UI_SHOW_PROFILE_ZAP_RECEIVED_FEED] ?: true,
                    showProfileFollowersFeed = preferences[UI_SHOW_PROFILE_FOLLOWERS_FEED] ?: true,
                    dontShowOnchainPublicWarning = preferences[UI_DONT_SHOW_ONCHAIN_PUBLIC_WARNING] ?: false,
                    suggestWorkoutsFromHealthConnect =
                        preferences[UI_SUGGEST_WORKOUTS_FROM_HEALTH_CONNECT]?.let { BooleanType.valueOf(it) } ?: BooleanType.ALWAYS,
                    accentColor = preferences[UI_ACCENT_COLOR]?.let { AccentColorType.valueOf(it) } ?: AccentColorType.PURPLE,
                    fontFamily = preferences[UI_FONT_FAMILY]?.let { FontFamilyType.valueOf(it) } ?: FontFamilyType.SYSTEM,
                    fontSize = preferences[UI_FONT_SIZE]?.let { FontSizeType.valueOf(it) } ?: FontSizeType.NORMAL,
                    composeSignature = preferences[UI_COMPOSE_SIGNATURE] ?: "",
                    showOnchainWallet = preferences[UI_SHOW_ONCHAIN_WALLET] ?: true,
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
                    preferences[UI_SHOW_HOME_NEW_THREADS_TAB] = sharedSettings.showHomeNewThreadsTab
                    preferences[UI_SHOW_HOME_CONVERSATIONS_TAB] = sharedSettings.showHomeConversationsTab
                    preferences[UI_SHOW_HOME_EVERYTHING_TAB] = sharedSettings.showHomeEverythingTab
                    preferences[UI_SHOW_PROFILE_BADGES] = sharedSettings.showProfileBadges
                    preferences[UI_SHOW_PROFILE_APP_RECOMMENDATIONS] = sharedSettings.showProfileAppRecommendations
                    preferences[UI_SHOW_PROFILE_ZAP_RECEIVED_FEED] = sharedSettings.showProfileZapReceivedFeed
                    preferences[UI_SHOW_PROFILE_FOLLOWERS_FEED] = sharedSettings.showProfileFollowersFeed
                    preferences[UI_DONT_SHOW_ONCHAIN_PUBLIC_WARNING] = sharedSettings.dontShowOnchainPublicWarning
                    preferences[UI_SUGGEST_WORKOUTS_FROM_HEALTH_CONNECT] = sharedSettings.suggestWorkoutsFromHealthConnect.name
                    preferences[UI_ACCENT_COLOR] = sharedSettings.accentColor.name
                    preferences[UI_FONT_FAMILY] = sharedSettings.fontFamily.name
                    preferences[UI_FONT_SIZE] = sharedSettings.fontSize.name
                    preferences[UI_COMPOSE_SIGNATURE] = sharedSettings.composeSignature
                    preferences[UI_SHOW_ONCHAIN_WALLET] = sharedSettings.showOnchainWallet
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Log any errors that occur while reading the DataStore.
                Log.e("SharedPreferences") { "Error saving DataStore preferences: ${e.message}" }
            }
        }
    }
}
