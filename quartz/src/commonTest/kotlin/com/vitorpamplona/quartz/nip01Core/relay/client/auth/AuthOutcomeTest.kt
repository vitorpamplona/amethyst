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
package com.vitorpamplona.quartz.nip01Core.relay.client.auth

import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The timing contract of [awaitAuthOutcome] on virtual time — what actually decides
 * whether a fetch that met an `auth-required:` wall waits or gives up.
 *
 * The property under test throughout: **the wait ends when the AUTH ends**, and the
 * grace bounds only the question "is anyone even answering?", never the answering
 * itself. A signer holding a prompt in front of a user is doing real work and must not
 * be cut off; a relay nobody is answering for must not cost an idle window.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthOutcomeTest {
    private val relay = NormalizedRelayUrl("wss://gated.example/")

    /** A responder whose per-relay state the test drives by hand. */
    private class FakeResponder : IAuthStatus {
        private val state = MutableStateFlow<PersistentMap<NormalizedRelayUrl, RelayAuthSnapshot>>(persistentMapOf())

        override val authStateFlow: StateFlow<PersistentMap<NormalizedRelayUrl, RelayAuthSnapshot>> = state

        override fun hasFinishedAuthentication(relay: NormalizedRelayUrl) = true

        fun set(
            relay: NormalizedRelayUrl,
            phase: RelayAuthSnapshot.Phase,
            successCount: Int = 0,
        ) {
            state.value = state.value.put(relay, RelayAuthSnapshot(phase, null, successCount))
        }
    }

    private class ClientWith(
        private vararg val responders: IAuthStatus,
    ) : INostrClient by EmptyNostrClient() {
        override fun authResponders() = responders.toSet()
    }

    private val grace = 1_000L

    @Test
    fun withoutAResponderThereIsNothingToWaitFor() =
        runTest {
            val client = EmptyNostrClient()
            val start = currentTime

            assertEquals(AuthOutcome.NO_RESPONDER, client.awaitAuthOutcome(relay, since = 0, graceMs = grace))
            assertEquals(0L, currentTime - start, "a client that cannot authenticate must not burn the grace")
        }

    /**
     * The AUTH and the refusal crossed on the wire: by the time we ask, the success is
     * already recorded. Our REQ has been re-sent, so there is nothing left to wait for —
     * and this is the common case, since the challenge is answered at connect time while
     * the first REQ is still in flight.
     */
    @Test
    fun anAuthThatAlreadyLandedResolvesInstantly() =
        runTest {
            val responder = FakeResponder()
            responder.set(relay, RelayAuthSnapshot.Phase.AUTHENTICATED, successCount = 1)
            val client = ClientWith(responder)
            val start = currentTime

            assertEquals(AuthOutcome.AUTHENTICATED, client.awaitAuthOutcome(relay, since = 0, graceMs = grace))
            assertEquals(0L, currentTime - start)
        }

    /**
     * The connection was already authenticated when we subscribed and the relay refused
     * us anyway — it is gating this particular query, not our identity. No further AUTH
     * is coming, so the grace is the whole (and correct) cost.
     */
    @Test
    fun alreadyAuthenticatedBeforeWeAskedIsARefusal() =
        runTest {
            val responder = FakeResponder()
            responder.set(relay, RelayAuthSnapshot.Phase.AUTHENTICATED, successCount = 1)
            val client = ClientWith(responder)
            val start = currentTime

            assertEquals(AuthOutcome.REFUSED, client.awaitAuthOutcome(relay, since = 1, graceMs = grace))
            assertEquals(grace, currentTime - start, "no new AUTH is in flight; give up at the grace")
        }

    @Test
    fun nobodyPickingTheChallengeUpIsRefusedAtTheGrace() =
        runTest {
            val client = ClientWith(FakeResponder())
            val start = currentTime

            assertEquals(AuthOutcome.REFUSED, client.awaitAuthOutcome(relay, since = 0, graceMs = grace))
            assertEquals(grace, currentTime - start)
        }

    /** The responder declines this relay: signs nothing and settles straight back to idle. */
    @Test
    fun aDecliningResponderIsRefusedAsSoonAsItSettles() =
        runTest {
            val responder = FakeResponder()
            val client = ClientWith(responder)
            launch {
                delay(100)
                responder.set(relay, RelayAuthSnapshot.Phase.SIGNING)
                delay(100)
                responder.set(relay, RelayAuthSnapshot.Phase.IDLE)
            }
            val start = currentTime

            assertEquals(AuthOutcome.REFUSED, client.awaitAuthOutcome(relay, since = 0, graceMs = grace))
            assertEquals(200L, currentTime - start, "refused the moment the responder gave up, not at the grace")
        }

    @Test
    fun aRelayRejectingOurAuthIsRefusedOnTheOk() =
        runTest {
            val responder = FakeResponder()
            val client = ClientWith(responder)
            launch {
                delay(50)
                responder.set(relay, RelayAuthSnapshot.Phase.AUTHENTICATING)
                delay(150)
                responder.set(relay, RelayAuthSnapshot.Phase.AUTH_FAILED)
            }
            val start = currentTime

            assertEquals(AuthOutcome.REFUSED, client.awaitAuthOutcome(relay, since = 0, graceMs = grace))
            assertEquals(200L, currentTime - start, "an OK false is an answer; end on it")
        }

    /**
     * The one the grace must not break: a NIP-55 / NIP-46 signer with a prompt in front
     * of a human takes far longer than the grace. Once it has *picked the challenge up*
     * the wait is unbounded, so an approval that arrives a minute later still counts.
     */
    @Test
    fun aSlowSignerIsNotCutOffByTheGrace() =
        runTest {
            val responder = FakeResponder()
            val client = ClientWith(responder)
            launch {
                delay(500)
                responder.set(relay, RelayAuthSnapshot.Phase.SIGNING)
                // The user stares at the prompt for a minute — 60x the grace.
                delay(60_000)
                responder.set(relay, RelayAuthSnapshot.Phase.AUTHENTICATING)
                delay(200)
                responder.set(relay, RelayAuthSnapshot.Phase.AUTHENTICATED, successCount = 1)
            }
            val start = currentTime

            assertEquals(AuthOutcome.AUTHENTICATED, client.awaitAuthOutcome(relay, since = 0, graceMs = grace))
            assertEquals(60_700L, currentTime - start, "the grace bounds pick-up, never the signing itself")
        }

    /**
     * ...but the settle stage is still bounded, and the accessories bound it by their own
     * idle window. That is the guarantee that makes the derived default safe to ship: an
     * auth-gated relay costs at most what a silent relay already cost, so a prompt nobody
     * ever answers cannot hold a fetch past the deadline it already had.
     */
    @Test
    fun aPromptNobodyAnswersIsCappedByTheSettleBound() =
        runTest {
            val responder = FakeResponder()
            val client = ClientWith(responder)
            launch {
                delay(100)
                responder.set(relay, RelayAuthSnapshot.Phase.SIGNING)
                // ...and the user never touches it.
            }
            val start = currentTime

            assertEquals(
                AuthOutcome.REFUSED,
                client.awaitAuthOutcome(relay, since = 0, graceMs = grace, settleMs = 5_000),
            )
            assertEquals(5_100L, currentTime - start, "picked up at 100, then capped by the settle bound")
        }

    /**
     * Two responders on one client (an account signer plus a derived stream-key signer):
     * one failing says nothing about the other, and either getting in is enough.
     */
    @Test
    fun oneResponderSucceedingIsEnough() =
        runTest {
            val failing = FakeResponder()
            val working = FakeResponder()
            val client = ClientWith(failing, working)
            launch {
                delay(50)
                failing.set(relay, RelayAuthSnapshot.Phase.AUTHENTICATING)
                working.set(relay, RelayAuthSnapshot.Phase.AUTHENTICATING)
                delay(50)
                // The first one settles as failed while the second is still on the wire.
                failing.set(relay, RelayAuthSnapshot.Phase.AUTH_FAILED)
                delay(100)
                working.set(relay, RelayAuthSnapshot.Phase.AUTHENTICATED, successCount = 1)
            }
            val start = currentTime

            assertEquals(AuthOutcome.AUTHENTICATED, client.awaitAuthOutcome(relay, since = 0, graceMs = grace))
            assertEquals(200L, currentTime - start, "must wait for the second responder, not stop at the first failure")
        }
}
