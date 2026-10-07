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
package com.vitorpamplona.amethyst.commons.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.UriHandler
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.model.UiSettingsFlow
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.payments.PayToAppProbe
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.ScanOutcome
import com.vitorpamplona.amethyst.commons.ui.settings.SettingsCategory
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf

/**
 * The app shell's pieces that shared screens embed but only the front end can draw yet: the
 * bottom navigation bar (its pinned favorites and groups read app stores) and the "around me"
 * location label (it asks for the location permission). Installed at the composition root
 * through [LocalAppPlatform]; each member draws nothing by default.
 */
@Stable
interface AppPlatform {
    /** Whether this build can cast media to a TV (the Play flavour's Cast SDK). */
    val isCastingAvailable: Boolean get() = false

    /** This build's version name ("1.04.2"), shown in the donation card; empty where unknown. */
    val appVersionName: String get() = ""

    /** This build's distribution flavour ("play", "fdroid"), shown next to the version; empty where none. */
    val appFlavor: String get() = ""

    /** Whether this is a debug build, which unlocks the drawer's in-progress destinations. */
    val isDebugBuild: Boolean get() = false

    /** Which installed apps can take a `payto` hand-off from the zap picker. */
    val payToApps: PayToAppProbe get() = PayToAppProbe.None

    /** The note announcing this release, which the notifications tab's donation card links to; null shows no card. */
    val releaseNotesId: String? get() = null

    /** Dumps the app's runtime state (memory, caches, subscriptions) to the log, for the logo's debug tap. */
    fun logDebugState() {}

    /** The settings screen's legal links, when the build's distribution calls for them. */
    fun legalSettingsCategory(uriHandler: UriHandler): SettingsCategory? = null

    /**
     * The languages the app is translated into, as display name ("Deutsch") to language tag
     * ("de"), for the language picker. Empty where the platform follows the system language only.
     */
    @Composable
    fun rememberAppLanguages(): ImmutableMap<String, String> = persistentMapOf()

    @Composable
    fun AppBottomBar(
        selectedRoute: Route?,
        nav: INav,
        accountViewModel: AccountViewModel,
        onClick: (Route) -> Unit,
    ) {}

    @Composable
    fun AroundMeLocationLabel() {}

    /**
     * Asks for the OS permission to post notifications and, where the build has a choice of push
     * servers (F-Droid's UnifiedPush), lets the user pick one. Shown when the notifications tab opens.
     */
    @Composable
    fun NotificationProviderPrompt(sharedPrefs: UiSettingsFlow) {}

    /**
     * How notifications reach this device: the push provider, the background service and its
     * per-account participation, battery optimization. Empty where the platform has none.
     */
    @Composable
    fun NotificationDeliverySettings(accountViewModel: AccountViewModel) {}

    /** The per-category notification settings the system owns (Android's channels). */
    @Composable
    fun NotificationCategorySettings() {}

    /** A button that reads [name] aloud with the platform's text-to-speech; nothing where there is none. */
    @Composable
    fun SpeakNameButton(name: String) {}

    /** A full-screen dialog to pick a place (map, search, "use my location"), as a geohash. */
    @Composable
    fun GeohashLocationPickerDialog(
        initialGeohash: String?,
        onDismiss: () -> Unit,
        onConfirm: (String) -> Unit,
    ) {}

    /** The picker's body, for screens that host it in their own scaffold. */
    @Composable
    fun GeohashLocationPickerContent(
        initialGeohash: String?,
        confirmLabel: String,
        onConfirm: (String) -> Unit,
        modifier: Modifier,
    ) {}

    /** The camera QR scanner, full screen. [onScan] says whether the payload was used. */
    @Composable
    fun QrCodeScannerDialog(
        onDismiss: () -> Unit,
        onScan: (String) -> ScanOutcome,
    ) {}

    /** Opens favorite apps and web links in the platform's app surface. */
    @Composable
    fun rememberAppLauncher(): AppLauncher = AppLauncher.None

    /**
     * Starts a [callType] call with [peers], asking for the call permissions first where the
     * platform needs them. Null where calls are not supported, so callers hide their buttons.
     */
    @Composable
    fun rememberCallStarter(
        peers: Set<HexKey>,
        callType: CallType,
    ): (() -> Unit)? = null

    /** Whether [GeocacheMapTab] draws a map here; the geocaches screen hides its Map tab when not. */
    val hasGeocacheMap: Boolean get() = false

    /** The map view of the nearby geocaches. Empty where the platform has no map. */
    @Composable
    fun GeocacheMapTab(
        feedContentState: FeedContentState,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    /**
     * Recent workouts the device already recorded (Android's Health Connect), offered as a tap to
     * pre-fill the new-workout form. Empty where there is no health store.
     */
    @Composable
    fun DetectedWorkoutCarousel(
        accountViewModel: AccountViewModel,
        onPick: (Route.NewWorkout) -> Unit,
    ) {}

    /**
     * The image model for a CORD-02 community picture: the plain URL for a url-only pointer, or a
     * local copy of the decrypted blob for an encrypted one (null while it loads or if it fails).
     */
    @Composable
    fun rememberConcordImageModel(
        pointer: ImagePointer?,
        accountViewModel: AccountViewModel,
    ): String? = pointer?.takeIf { !it.isResolvable() }?.url?.ifBlank { null }

    /** The cached icon of the napplet or nsite at [coordinate], if one was fetched. */
    @Composable
    fun rememberNappletIconModel(coordinate: String): String? = null

    /** The icon a napplet manifest by [author] / [identifier] declares, if cached. */
    @Composable
    fun rememberManifestIconModel(
        author: String,
        identifier: String,
    ): String? = null

    /** The favicon the browser captured for [url]'s host, if any. */
    @Composable
    fun rememberWebAppIconModel(url: String): String? = null

    /**
     * Opens the audio room at [addressValue] (a kind-30312 address) in its own window; callers
     * hand the account over through `NestBridge` first. Does nothing where rooms are not wired.
     */
    fun openNestRoom(addressValue: String) {}

    /** Draws nothing: previews, and front ends still wiring their pieces. */
    object None : AppPlatform
}

/** The front end's [AppPlatform]. Static: it is set once at the root and never changes. */
val LocalAppPlatform = staticCompositionLocalOf<AppPlatform> { AppPlatform.None }

@Composable
fun AppBottomBar(
    selectedRoute: Route?,
    nav: INav,
    accountViewModel: AccountViewModel,
    onClick: (Route) -> Unit,
) = LocalAppPlatform.current.AppBottomBar(selectedRoute, nav, accountViewModel, onClick)

@Composable
fun AroundMeLocationLabel() = LocalAppPlatform.current.AroundMeLocationLabel()

@Composable
fun SpeakNameButton(name: String) = LocalAppPlatform.current.SpeakNameButton(name)

@Composable
fun NotificationProviderPrompt(sharedPrefs: UiSettingsFlow) = LocalAppPlatform.current.NotificationProviderPrompt(sharedPrefs)

@Composable
fun NotificationDeliverySettings(accountViewModel: AccountViewModel) = LocalAppPlatform.current.NotificationDeliverySettings(accountViewModel)

@Composable
fun NotificationCategorySettings() = LocalAppPlatform.current.NotificationCategorySettings()

@Composable
fun GeohashLocationPickerDialog(
    initialGeohash: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) = LocalAppPlatform.current.GeohashLocationPickerDialog(initialGeohash, onDismiss, onConfirm)

@Composable
fun GeohashLocationPickerContent(
    initialGeohash: String?,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier,
) = LocalAppPlatform.current.GeohashLocationPickerContent(initialGeohash, confirmLabel, onConfirm, modifier)

@Composable
fun QrCodeScannerDialog(
    onDismiss: () -> Unit,
    onScan: (String) -> ScanOutcome,
) = LocalAppPlatform.current.QrCodeScannerDialog(onDismiss, onScan)

@Composable
fun rememberAppLauncher(): AppLauncher = LocalAppPlatform.current.rememberAppLauncher()

@Composable
fun rememberConcordImageModel(
    pointer: ImagePointer?,
    accountViewModel: AccountViewModel,
): String? = LocalAppPlatform.current.rememberConcordImageModel(pointer, accountViewModel)

@Composable
fun rememberNappletIconModel(coordinate: String): String? = LocalAppPlatform.current.rememberNappletIconModel(coordinate)

@Composable
fun rememberManifestIconModel(
    author: String,
    identifier: String,
): String? = LocalAppPlatform.current.rememberManifestIconModel(author, identifier)

@Composable
fun rememberWebAppIconModel(url: String): String? = LocalAppPlatform.current.rememberWebAppIconModel(url)

/**
 * Opens a favorite app or a web link in the platform's app surface (on Android, the sandboxed
 * full-screen WebView in its own task).
 */
interface AppLauncher {
    /** [stillLoading] is shown when a Nostr app's defining event has not arrived yet. */
    fun launch(
        app: FavoriteApp,
        stillLoading: String,
    )

    /** [preferTor] forces Tor when available, e.g. for `.onion` hosts. */
    fun launchUrl(
        url: String,
        preferTor: Boolean = false,
    )

    /** Launches nothing: front ends without an app surface yet. */
    object None : AppLauncher {
        override fun launch(
            app: FavoriteApp,
            stillLoading: String,
        ) {}

        override fun launchUrl(
            url: String,
            preferTor: Boolean,
        ) {}
    }
}

@Composable
fun rememberCallStarter(
    peers: Set<HexKey>,
    callType: CallType,
): (() -> Unit)? = LocalAppPlatform.current.rememberCallStarter(peers, callType)

@Composable
fun GeocacheMapTab(
    feedContentState: FeedContentState,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalAppPlatform.current.GeocacheMapTab(feedContentState, accountViewModel, nav)

@Composable
fun DetectedWorkoutCarousel(
    accountViewModel: AccountViewModel,
    onPick: (Route.NewWorkout) -> Unit,
) = LocalAppPlatform.current.DetectedWorkoutCarousel(accountViewModel, onPick)
