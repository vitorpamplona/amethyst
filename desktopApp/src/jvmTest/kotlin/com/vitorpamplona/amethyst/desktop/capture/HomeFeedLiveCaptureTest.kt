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
package com.vitorpamplona.amethyst.desktop.capture

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.vitorpamplona.amethyst.commons.keystorage.SecureKeyStorage
import com.vitorpamplona.amethyst.commons.model.account.AccountInfo
import com.vitorpamplona.amethyst.commons.model.account.SignerType
import com.vitorpamplona.amethyst.commons.tor.ITorManager
import com.vitorpamplona.amethyst.commons.tor.TorServiceStatus
import com.vitorpamplona.amethyst.commons.tor.TorSettings
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.amethyst.desktop.App
import com.vitorpamplona.amethyst.desktop.LaunchTestOverrides
import com.vitorpamplona.amethyst.desktop.LayoutMode
import com.vitorpamplona.amethyst.desktop.account.AccountManager
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopHttpClient
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.relay.LocalRelayStore
import com.vitorpamplona.amethyst.desktop.ui.deck.DeckState
import com.vitorpamplona.amethyst.desktop.ui.deck.WorkspaceManager
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Live capture of the desktop HOME (following) feed for a given pubkey, logged in
 * read-only (ViewOnly), against REAL public relays. This is the data-driven
 * counterpart to [FeatureShowcaseDesktopTest] — it proves the harness can grab a
 * real screen with real network content, not just an isolated composable.
 *
 * Because it depends on live relays it is OPT-IN: pass `-Pamethyst.live.capture=true`
 * (or set the `AMETHYST_LIVE_CAPTURE` env var). Without it the test is skipped so CI
 * and offline runs stay deterministic.
 *
 * Run:
 * ```
 * xvfb-run --auto-servernum ./gradlew :desktopApp:test \
 *   --tests '*HomeFeedLiveCaptureTest*' -Pamethyst.live.capture=true
 * ```
 * Artifact: `desktopApp/build/screenshots/desktop-home-feed.png`.
 */
class HomeFeedLiveCaptureTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var tempDir: File
    private lateinit var storage: SecureKeyStorage
    private lateinit var harnessScope: CoroutineScope

    private val liveEnabled: Boolean =
        System.getProperty("amethyst.live.capture")?.toBoolean() == true ||
            System.getenv("AMETHYST_LIVE_CAPTURE")?.toBoolean() == true

    @BeforeTest
    fun setup() {
        tempDir = createTempDirectory("home-feed-live-capture").toFile()
        File(tempDir, ".amethyst").mkdirs()
        storage = mockk(relaxed = true)
        coEvery { storage.getPrivateKey(any()) } returns null
        harnessScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }

    @AfterTest
    fun teardown() {
        harnessScope.cancel()
        tempDir.deleteRecursively()
    }

    @Test
    fun captureHomeFeed() {
        if (!liveEnabled) {
            println("HomeFeedLiveCaptureTest skipped — pass -Pamethyst.live.capture=true to run.")
            return
        }

        val pubKeyHex = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
        val npub = NPub.create(pubKeyHex)

        // Pre-seed a ViewOnly account so App()'s loadSavedAccount logs in on startup.
        val accountManager = AccountManager(storage, tempDir)
        runBlocking {
            accountManager.accountStorage.saveAccount(AccountInfo(npub = npub, signerType = SignerType.ViewOnly))
            accountManager.accountStorage.setCurrentAccount(npub)
        }

        val localCache = DesktopLocalCache()
        val localRelayStore = LocalRelayStore(scope = harnessScope, homeDir = tempDir)
        val torManager = OffTorManager()

        // REAL relay manager over OkHttp (Tor OFF) — connects to live default relays.
        val httpClient =
            DesktopHttpClient(
                torManager = torManager,
                shouldUseTorForRelay = { false },
                torTypeProvider = { TorType.OFF },
                scope = harnessScope,
            ).also { DesktopHttpClient.setInstance(it) }
        val relayManager = DesktopRelayConnectionManager(httpClient)

        compose.setContent {
            MaterialTheme {
                App(
                    layoutMode = LayoutMode.SINGLE_PANE, // default screen is the Home feed
                    onLayoutModeChange = {},
                    deckState = DeckState(harnessScope),
                    workspaceManager = WorkspaceManager(harnessScope),
                    accountManager = accountManager,
                    showComposeDialog = false,
                    showAppDrawer = false,
                    onShowComposeDialog = {},
                    onShowReplyDialog = {},
                    onDismissComposeDialog = {},
                    onDismissAppDrawer = {},
                    onShowAppDrawer = {},
                    replyToNote = null,
                    torManager = torManager,
                    torTypeFlow = MutableStateFlow(TorType.OFF),
                    externalPortFlow = MutableStateFlow(9050),
                    initialTorSettings = OFF_TOR_SETTINGS,
                    testOverrides =
                        LaunchTestOverrides(
                            localCache = localCache,
                            relayManager = relayManager,
                            localRelayStore = localRelayStore,
                            // Run the production addDefaultRelays/connect path so the
                            // home-feed subscriptions actually reach live relays.
                            skipStartupRelayBootstrap = false,
                            torSettingsOverride = OFF_TOR_SETTINGS,
                        ),
                )
            }
        }

        // Wait for login, then for the following feed to pull notes off live relays.
        compose.waitUntil(timeoutMillis = 20_000) {
            accountManager.accountState.value is AccountState.LoggedIn
        }
        compose.waitUntil(timeoutMillis = 45_000) { localCache.noteCount() > 8 }
        // Let a couple of frames settle (avatars, layout) before the grab.
        compose.waitForIdle()
        runBlocking { kotlinx.coroutines.delay(2_500) }
        compose.waitForIdle()

        val png = compose.onRoot().saveScreenshot("desktop-home-feed")
        println("Home feed capture: $png (${png.length()} bytes, notes=${localCache.noteCount()})")
    }

    companion object {
        private val OFF_TOR_SETTINGS =
            TorSettings(
                torType = TorType.OFF,
                externalSocksPort = 9050,
                onionRelaysViaTor = false,
                dmRelaysViaTor = false,
                newRelaysViaTor = false,
                trustedRelaysViaTor = false,
                urlPreviewsViaTor = false,
                profilePicsViaTor = false,
                imagesViaTor = false,
                videosViaTor = false,
                moneyOperationsViaTor = false,
                nip05VerificationsViaTor = false,
                mediaUploadsViaTor = false,
            )
    }
}

/** Tor stand-in that reports Off forever (no real kmp-tor runtime in a headless test). */
private class OffTorManager : ITorManager {
    private val _status = MutableStateFlow<TorServiceStatus>(TorServiceStatus.Off)
    override val status: StateFlow<TorServiceStatus> = _status.asStateFlow()
    override val activePortOrNull: StateFlow<Int?> = MutableStateFlow<Int?>(null).asStateFlow()

    override suspend fun dormant() = Unit

    override suspend fun active() = Unit

    override suspend fun newIdentity() = Unit
}
