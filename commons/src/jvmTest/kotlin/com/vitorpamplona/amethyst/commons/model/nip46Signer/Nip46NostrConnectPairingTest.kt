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
package com.vitorpamplona.amethyst.commons.model.nip46Signer

import com.vitorpamplona.amethyst.commons.connectedApps.nip46.InMemoryNip46ClientStore
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46ClientInfo
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46ClientStore
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46PermissionAuthorizer
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppSignerPolicy
import com.vitorpamplona.amethyst.commons.domain.nip46.NostrConnectLoginUseCase
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerClientMetadata
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Pairing a `nostrconnect://` app with Amethyst's signer, with Amethyst's own client as the app.
 */
class Nip46NostrConnectPairingTest {
    private val appRelay = NormalizedRelayUrl("wss://app-relay.example.com/")
    private val inboxRelay = NormalizedRelayUrl("wss://inbox.example.com/")

    /**
     * The app answers the pairing ack at once, on the relay its code named, and kind 24133 is
     * ephemeral: if the signer is not listening there yet, the app's first request is lost and it
     * waits out its retry (65 s). Pairing must not answer before it listens there.
     */
    private fun pairsWithoutARetry(signerAlreadyOn: Boolean) =
        runBlocking(Dispatchers.Default) {
            val net = Nip46SignerTestNet(listOf(appRelay, inboxRelay))
            try {
                val state = net.signerState(setOf(inboxRelay))
                if (signerAlreadyOn) {
                    state.setEnabled(true)
                    withTimeout(10_000) { state.listeningRelays.first { it == setOf(inboxRelay) } }
                }

                val code = NostrConnectLoginUseCase.generateUri(KeyPair(), listOf(appRelay.url), BunkerClientMetadata(name = "App"))
                val coordinate = Nip46PermissionAuthorizer.coordinateFor(net.identity.pubKey, code.ephemeralKeyPair.pubKey.toHexKey())
                // Already trusted, so pairing skips the consent dialog; used just now, so the idle prune keeps it.
                net.ledger.setPolicy(coordinate, AppSignerPolicy.FULL_TRUST)
                net.ledger.updateLastUsed(coordinate)

                val login = net.scope.async { NostrConnectLoginUseCase.awaitAndLogin(code, net.client) }
                // The code's subscription is open before the signer answers.
                delay(1_000)
                assertIs<Nip46SignerState.ConnectResult.Connected>(state.connectViaNostrConnect(code.uri))

                // Well inside the app's 65 s retry: only a first request that got through is this fast.
                val app = withTimeout(20_000) { login.await() }
                assertEquals(net.identity.pubKey, app.pubKeyHex)
                app.signer.closeSubscription()
            } finally {
                net.close()
            }
        }

    /** Pairing turns the signer on: the listener starts for the first time. */
    @Test
    fun pairingWithTheSignerOffAnswersTheFirstRequest() = pairsWithoutARetry(signerAlreadyOn = false)

    /** The signer runs on the inbox relays: pairing restarts it to add the app's relay. */
    @Test
    fun pairingWithTheSignerOnAnswersTheFirstRequest() = pairsWithoutARetry(signerAlreadyOn = true)

    /**
     * The launch loads the paired apps' relays from the store. On a slow machine that load can read
     * the store before pairing saves the app and land after pairing's own load: its stale answer
     * must not take the app's relay back out of the listener (the app's next request is then lost).
     */
    @Test
    fun aLaunchLoadThatLandsAfterPairingKeepsTheAppRelay() =
        runBlocking(Dispatchers.Default) {
            val store = LaunchLoadLandsLast()
            val net = Nip46SignerTestNet(listOf(appRelay, inboxRelay), clientStore = store)
            try {
                val state = net.signerState(setOf(inboxRelay))

                val code = NostrConnectLoginUseCase.generateUri(KeyPair(), listOf(appRelay.url), BunkerClientMetadata(name = "App"))
                val coordinate = Nip46PermissionAuthorizer.coordinateFor(net.identity.pubKey, code.ephemeralKeyPair.pubKey.toHexKey())
                net.ledger.setPolicy(coordinate, AppSignerPolicy.FULL_TRUST)
                net.ledger.updateLastUsed(coordinate)

                val login = net.scope.async { NostrConnectLoginUseCase.awaitAndLogin(code, net.client) }
                delay(1_000)
                assertIs<Nip46SignerState.ConnectResult.Connected>(state.connectViaNostrConnect(code.uri))

                withTimeout(10_000) { store.launchLoadReturned.await() }
                assertTrue(store.launchLoadWasStale, "the launch load read the store before pairing saved the app")
                // Whatever the stale answer would do to the listener has had time to happen.
                delay(500)
                assertEquals(setOf(appRelay, inboxRelay), state.listeningRelays.value)

                val app = withTimeout(20_000) { login.await() }
                assertEquals(net.identity.pubKey, app.pubKeyHex)
                app.signer.closeSubscription()
            } finally {
                net.close()
            }
        }

    /**
     * Its first [all] (the launch's load) reads before the first save and returns just after the
     * second [all] (pairing's load) has, or after 2 s if that load has to wait for this one.
     */
    private class LaunchLoadLandsLast(
        private val inner: Nip46ClientStore = InMemoryNip46ClientStore(),
    ) : Nip46ClientStore by inner {
        private val loads = AtomicInteger(0)
        private val saved = CompletableDeferred<Unit>()
        private val pairingLoaded = CompletableDeferred<Unit>()
        val launchLoadReturned = CompletableDeferred<Unit>()

        @Volatile
        var launchLoadWasStale = false

        override suspend fun store(
            coordinate: String,
            info: Nip46ClientInfo,
        ) {
            inner.store(coordinate, info)
            saved.complete(Unit)
        }

        override suspend fun all(): Map<String, Nip46ClientInfo> {
            if (loads.getAndIncrement() > 0) return inner.all().also { pairingLoaded.complete(Unit) }

            val beforePairing = inner.all()
            saved.await()
            withTimeoutOrNull(2_000) { pairingLoaded.await() }
            // Lets pairing's load write its answer first.
            delay(100)
            launchLoadWasStale = beforePairing.isEmpty()
            launchLoadReturned.complete(Unit)
            return beforePairing
        }
    }
}
