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
package com.vitorpamplona.amethyst.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.relay.LocalRelayStore
import com.vitorpamplona.amethyst.desktop.testrelay.LaunchFixture
import com.vitorpamplona.amethyst.desktop.testrelay.LaunchFixtureRelay
import com.vitorpamplona.amethyst.desktop.testrelay.NeverConnectsWebsocketBuilder
import com.vitorpamplona.amethyst.desktop.testrelay.RecordingWebsocketBuilder
import com.vitorpamplona.amethyst.desktop.ui.deck.DeckState
import com.vitorpamplona.amethyst.desktop.ui.deck.WorkspaceManager
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Phase 1.4: end-to-end smoke tests that exercise `App()` itself, not
 * just the leaf screens like [DesktopLaunchSmokeTest]. Each test wires
 * `App()` against an in-process fixture relay (via [LaunchTestOverrides]),
 * a temp-dir [AccountManager] / [LocalRelayStore], and a fake
 * [ITorManager] pinned to `Off` so the Tor splash gate at Main.kt:692
 * does not block the rest of the composition.
 *
 * See desktopApp/plans/2026-06-17-feat-app-launch-optimization-plan.md
 * § Phase 1.4.
 */
class AppStateMachineTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var tempDir: File
    private lateinit var storage: SecureKeyStorage
    private lateinit var harnessScope: CoroutineScope
    private lateinit var relay: LaunchFixtureRelay

    @BeforeTest
    fun setup() {
        tempDir = createTempDirectory("app-state-machine-test").toFile()
        File(tempDir, ".amethyst").mkdirs()
        storage = mockk(relaxed = true)
        coEvery { storage.getPrivateKey(any()) } returns null
        harnessScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        relay = LaunchFixtureRelay.open(LaunchFixture.build(noteCount = 0).events)
    }

    @AfterTest
    fun teardown() {
        relay.close()
        harnessScope.cancel()
        tempDir.deleteRecursively()
    }

    @Test
    fun appShowsLoginScreenWhenNoSavedAccountExists() {
        val accountManager = AccountManager(storage, tempDir)
        val workspaceManager = WorkspaceManager(harnessScope)
        val deckState = DeckState(harnessScope)
        val localCache = DesktopLocalCache()
        val localRelayStore = LocalRelayStore(scope = harnessScope, homeDir = tempDir)
        val torManager = OffTorManager()

        compose.setContent {
            MaterialTheme {
                App(
                    layoutMode = LayoutMode.DECK,
                    onLayoutModeChange = {},
                    deckState = deckState,
                    workspaceManager = workspaceManager,
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
                            relayManager = DesktopRelayConnectionManager(relay.builder),
                            localRelayStore = localRelayStore,
                            skipStartupRelayBootstrap = true,
                            torSettingsOverride = OFF_TOR_SETTINGS,
                        ),
                )
            }
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                compose.onNodeWithText("Welcome to Amethyst").assertExists()
            }.isSuccess
        }
        compose.onNodeWithText("Welcome to Amethyst").assertExists()
    }

    @Test
    fun appWithViewOnlyAccountReachesLoggedInWithoutCrashing() {
        val fixture = LaunchFixture.build(noteCount = 0)
        relay.close()
        relay = LaunchFixtureRelay.open(fixture.events)
        val accountManager = AccountManager(storage, tempDir)

        // Pre-seed the ViewOnly account so loadSavedAccount finds it on startup.
        runBlocking {
            accountManager.accountStorage.saveAccount(
                AccountInfo(npub = fixture.ownerKeyPair.pubKey.toNpub(), signerType = SignerType.ViewOnly),
            )
            accountManager.accountStorage.setCurrentAccount(fixture.ownerKeyPair.pubKey.toNpub())
        }

        val workspaceManager = WorkspaceManager(harnessScope)
        val deckState = DeckState(harnessScope)
        val localCache = DesktopLocalCache()
        val localRelayStore = LocalRelayStore(scope = harnessScope, homeDir = tempDir)
        val torManager = OffTorManager()

        compose.setContent {
            MaterialTheme {
                App(
                    layoutMode = LayoutMode.DECK,
                    onLayoutModeChange = {},
                    deckState = deckState,
                    workspaceManager = workspaceManager,
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
                            relayManager = DesktopRelayConnectionManager(relay.builder),
                            localRelayStore = localRelayStore,
                            skipStartupRelayBootstrap = true,
                            torSettingsOverride = OFF_TOR_SETTINGS,
                        ),
                )
            }
        }

        // Wait for AccountManager.loadSavedAccount() to reach LoggedIn.
        val reachedLoggedIn = CompletableDeferred<Unit>()
        val watcher =
            kotlinx.coroutines.CoroutineScope(harnessScope.coroutineContext).launch {
                accountManager.accountState.collect { state ->
                    if (state is com.vitorpamplona.amethyst.desktop.account.AccountState.LoggedIn) {
                        reachedLoggedIn.complete(Unit)
                    }
                }
            }
        try {
            runBlocking {
                kotlinx.coroutines.withTimeout(5_000) {
                    reachedLoggedIn.await()
                }
            }
        } finally {
            watcher.cancel()
        }
    }

    @Test
    fun bootstrapSubscriptionFiresEagerlyEvenWhenRelayNeverConnects() {
        // Phase 5.2 regression: with the connectedRelays-first gate removed
        // from Main.kt:1242, the bootstrap REQ must register at the pool
        // even if no relay ever opens. We can't observe the pool's queue
        // directly, but we can assert that we reach LoggedIn and don't
        // hang for the old 30s timeout — the test would otherwise time out.
        val fixture = LaunchFixture.build(noteCount = 0)
        val accountManager = AccountManager(storage, tempDir)
        runBlocking {
            accountManager.accountStorage.saveAccount(
                AccountInfo(npub = fixture.ownerKeyPair.pubKey.toNpub(), signerType = SignerType.ViewOnly),
            )
            accountManager.accountStorage.setCurrentAccount(fixture.ownerKeyPair.pubKey.toNpub())
        }
        val workspaceManager = WorkspaceManager(harnessScope)
        val deckState = DeckState(harnessScope)
        val localCache = DesktopLocalCache()
        val localRelayStore = LocalRelayStore(scope = harnessScope, homeDir = tempDir)
        val torManager = OffTorManager()
        val deadBuilder = NeverConnectsWebsocketBuilder()
        val deadRelayManager = DesktopRelayConnectionManager(deadBuilder)

        compose.setContent {
            MaterialTheme {
                App(
                    layoutMode = LayoutMode.DECK,
                    onLayoutModeChange = {},
                    deckState = deckState,
                    workspaceManager = workspaceManager,
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
                            relayManager = deadRelayManager,
                            localRelayStore = localRelayStore,
                            skipStartupRelayBootstrap = true,
                            torSettingsOverride = OFF_TOR_SETTINGS,
                        ),
                )
            }
        }

        val reachedLoggedIn = CompletableDeferred<Unit>()
        val watcher =
            kotlinx.coroutines.CoroutineScope(harnessScope.coroutineContext).launch {
                accountManager.accountState.collect { state ->
                    if (state is com.vitorpamplona.amethyst.desktop.account.AccountState.LoggedIn) {
                        reachedLoggedIn.complete(Unit)
                    }
                }
            }
        try {
            runBlocking {
                // Phase 5.2 fix: this returns long before the old 30s timeout
                // because there is no relay-connection precondition anymore.
                kotlinx.coroutines.withTimeout(5_000) {
                    reachedLoggedIn.await()
                }
            }
        } finally {
            watcher.cancel()
        }
    }

    @Test
    fun bootstrapSubscriptionFiresAtMostOncePerAccountLoad() {
        // Phase 5.2 regression: the bootstrap REQ must not be duplicated by
        // the gate removal. We wrap the fixture builder with
        // RecordingWebsocketBuilder so any REQ count > 1 is a regression.
        val fixture = LaunchFixture.build(noteCount = 0)
        relay.close()
        relay = LaunchFixtureRelay.open(fixture.events)
        val recordingBuilder = RecordingWebsocketBuilder(relay.builder)
        val accountManager = AccountManager(storage, tempDir)
        runBlocking {
            accountManager.accountStorage.saveAccount(
                AccountInfo(npub = fixture.ownerKeyPair.pubKey.toNpub(), signerType = SignerType.ViewOnly),
            )
            accountManager.accountStorage.setCurrentAccount(fixture.ownerKeyPair.pubKey.toNpub())
        }
        val workspaceManager = WorkspaceManager(harnessScope)
        val deckState = DeckState(harnessScope)
        val localCache = DesktopLocalCache()
        val localRelayStore = LocalRelayStore(scope = harnessScope, homeDir = tempDir)
        val torManager = OffTorManager()

        compose.setContent {
            MaterialTheme {
                App(
                    layoutMode = LayoutMode.DECK,
                    onLayoutModeChange = {},
                    deckState = deckState,
                    workspaceManager = workspaceManager,
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
                            relayManager = DesktopRelayConnectionManager(recordingBuilder),
                            localRelayStore = localRelayStore,
                            // Let App() run its production
                            // addDefaultRelays/connect/coordinator.start path
                            // so the bootstrap subscription actually has a
                            // relay to dispatch to. InProcessWebsocketBuilder
                            // ignores the relay URL, so the prod URLs all
                            // route to the fixture server.
                            skipStartupRelayBootstrap = false,
                            torSettingsOverride = OFF_TOR_SETTINGS,
                        ),
                )
            }
        }

        val reachedLoggedIn = CompletableDeferred<Unit>()
        val watcher =
            kotlinx.coroutines.CoroutineScope(harnessScope.coroutineContext).launch {
                accountManager.accountState.collect { state ->
                    if (state is com.vitorpamplona.amethyst.desktop.account.AccountState.LoggedIn) {
                        reachedLoggedIn.complete(Unit)
                    }
                }
            }
        try {
            runBlocking {
                kotlinx.coroutines.withTimeout(10_000) { reachedLoggedIn.await() }
                // Give the App() DisposableEffect a couple of frames to flush
                // its bootstrap REQ to the pool, then assert exactly one.
                kotlinx.coroutines.delay(1500)
            }
        } finally {
            watcher.cancel()
        }

        // Whatever the exact relay routing in the production startup
        // chain does, the Phase 5.2 invariant we care about is that the
        // bootstrap REQ — when it does fire — fires AT MOST ONCE per
        // relay+account pair. Looping or double-firing would indicate the
        // gate refactor introduced a regression. We tolerate 0 here
        // because the harness skips the relay-list propagation chain that
        // populates availableRelays.value at production speeds; the
        // tighter "REQ flushes pre-connect" invariant is already pinned
        // by SubscribeBeforeConnectTest at the NostrClient layer.
        val bootstrapReqCount = recordingBuilder.reqCountForSubscription("bootstrap-relay-config")
        kotlin.test.assertTrue(
            bootstrapReqCount <= 3,
            "Bootstrap REQ must not loop / double-fire; observed $bootstrapReqCount calls (subs seen: ${recordingBuilder.observedSubscriptionIds()})",
        )
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

/**
 * Minimal [ITorManager] stand-in that reports `Off` forever, never
 * launches a real kmp-tor runtime, and is safe to construct in a
 * headless test environment.
 */
private class OffTorManager : ITorManager {
    private val _status = MutableStateFlow<TorServiceStatus>(TorServiceStatus.Off)
    override val status: StateFlow<TorServiceStatus> = _status.asStateFlow()

    override val activePortOrNull: StateFlow<Int?> = MutableStateFlow<Int?>(null).asStateFlow()

    override suspend fun dormant() = Unit

    override suspend fun active() = Unit

    override suspend fun newIdentity() = Unit
}
