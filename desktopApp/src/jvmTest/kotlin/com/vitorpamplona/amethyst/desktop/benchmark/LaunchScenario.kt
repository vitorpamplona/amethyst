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
package com.vitorpamplona.amethyst.desktop.benchmark

import com.vitorpamplona.amethyst.commons.keystorage.SecureKeyStorage
import com.vitorpamplona.amethyst.commons.model.account.AccountInfo
import com.vitorpamplona.amethyst.commons.model.account.SignerType
import com.vitorpamplona.amethyst.desktop.account.AccountManager
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.relay.LocalRelayStore
import com.vitorpamplona.amethyst.desktop.testrelay.LaunchFixture
import com.vitorpamplona.amethyst.desktop.testrelay.LaunchFixtureRelay
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Runs one iteration of the cold-boot scenario: empty home directory,
 * empty events.db, a single ViewOnly account preloaded into
 * `accounts.json.enc`, and an in-process [LaunchFixtureRelay] standing in
 * for the network.
 *
 * Records into [LaunchMarkers] the timestamps the benchmark cares about:
 *  - [LaunchMarkers.T_ACCOUNT_LOGGED_IN] — `AccountManager.accountState`
 *    reaches `LoggedIn(isReadOnly=true)`.
 *  - [LaunchMarkers.T_FIRST_EVENT] — first `kind:1` flows through
 *    `DesktopLocalCache.consume`.
 *  - [LaunchMarkers.T_N_EVENTS] — `n`th `kind:1` flows through the cache.
 *
 * Returns when [n] kind:1 events have been consumed, the EOSE for the
 * subscription arrived, or the 30s [overallTimeout] elapses (whichever is
 * first).
 *
 * This is the slim "non-Compose" benchmark path: it exercises the cold-boot
 * critical-path code (AccountManager + LocalCache + RelayConnectionManager
 * + LocalRelayStore) without trying to drive the full `App()` Compose
 * composable, which would require mocking DeckState / WorkspaceManager /
 * TorManager. A Compose-driven variant can layer onto this once the App()
 * smoke-test harness lands in Phase 1.4.
 *
 * See desktopApp/plans/2026-06-17-feat-app-launch-optimization-plan.md
 * § Phase 3.2.
 */
object LaunchScenario {
    data class Result(
        val markers: Map<String, Duration>,
        val eventsConsumed: Int,
    )

    fun coldBoot(
        n: Int = LaunchMarkers.DEFAULT_N,
        fixture: LaunchFixture = LaunchFixture.build(),
        overallTimeout: Duration = 30.seconds,
    ): Result =
        runBlocking {
            val tempHome = createTempDirectory("launch-scenario").toFile()
            val storage = mockk<SecureKeyStorage>(relaxed = true)
            coEvery { storage.getPrivateKey(any()) } returns null
            File(tempHome, ".amethyst").mkdirs()

            val account = AccountManager(storage, tempHome)
            val ownerNpub = fixture.ownerKeyPair.pubKey.toNpub()
            account.accountStorage.saveAccount(
                AccountInfo(npub = ownerNpub, signerType = SignerType.ViewOnly),
            )
            account.accountStorage.setCurrentAccount(ownerNpub)

            val cache = DesktopLocalCache()
            val storeScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val localRelayStore = LocalRelayStore(scope = storeScope, homeDir = tempHome)
            localRelayStore.openForAccount(fixture.ownerKeyPair.pubKey.toHexKey())

            val relay = LaunchFixtureRelay.open(fixture.events)
            val relayManager = DesktopRelayConnectionManager(relay.builder)

            val eventCounter = AtomicInteger(0)
            val eoseSignal = CompletableDeferred<Unit>()
            val nReached = CompletableDeferred<Unit>()

            try {
                LaunchMarkers.start()

                // Account decrypt + ViewOnly load. The accountState collector
                // below picks up the LoggedIn emission and records the marker.
                val accountWatcher =
                    storeScope.launch {
                        account.accountState.first { it is AccountState.LoggedIn }
                        LaunchMarkers.mark(LaunchMarkers.T_ACCOUNT_LOGGED_IN)
                    }

                account.loadSavedAccount()
                relayManager.connect()

                relayManager.client.subscribe(
                    subId = "launch-bench-home",
                    filters =
                        mapOf(
                            LaunchFixtureRelay.LAUNCH_TEST_RELAY_URL to
                                listOf(Filter(kinds = listOf(TextNoteEvent.KIND))),
                        ),
                    listener =
                        object : SubscriptionListener {
                            override fun onEvent(
                                event: com.vitorpamplona.quartz.nip01Core.core.Event,
                                isLive: Boolean,
                                relay: NormalizedRelayUrl,
                                forFilters: List<Filter>?,
                            ) {
                                if (event.kind != TextNoteEvent.KIND) return
                                val consumed =
                                    cache.consume(event, relay, wasVerified = true)
                                if (!consumed) return
                                val placed = eventCounter.incrementAndGet()
                                if (placed == 1) LaunchMarkers.mark(LaunchMarkers.T_FIRST_EVENT)
                                if (placed >= n) {
                                    LaunchMarkers.mark(LaunchMarkers.T_N_EVENTS)
                                    nReached.complete(Unit)
                                }
                            }

                            override fun onEose(
                                relay: NormalizedRelayUrl,
                                forFilters: List<Filter>?,
                            ) {
                                if (!eoseSignal.isCompleted) eoseSignal.complete(Unit)
                            }
                        },
                )

                withTimeout(overallTimeout) {
                    // Block until either N events landed or the fixture is
                    // exhausted (EOSE). Whichever happens first is the
                    // scenario terminator.
                    if (eventCounter.get() < n) {
                        kotlinx.coroutines.selects.select<Unit> {
                            nReached.onAwait { }
                            eoseSignal.onAwait { }
                        }
                    }
                }

                accountWatcher.cancel()
                Result(LaunchMarkers.snapshot(), eventCounter.get())
            } finally {
                relayManager.disconnect()
                relay.close()
                localRelayStore.close()
                storeScope.cancel()
                tempHome.deleteRecursively()
            }
        }
}
