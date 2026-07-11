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
package com.vitorpamplona.amethyst.service.relayClient.authCommand.model

import com.vitorpamplona.amethyst.commons.relayauth.AuthPurposeKind
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Round-trip tests for the DataStore-backed relay-auth permission store. Backed by a real
 * PreferenceDataStore on a per-test temp directory (the store takes a plain filesDir), so it runs
 * on the JVM without Robolectric. A fresh directory per test dodges DataStore's per-file
 * single-instance guard.
 */
class DataStoreRelayAuthPermissionStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun newStore() = DataStoreRelayAuthPermissionStore(tmp.newFolder())

    private val relay = "wss://auth.relay.test"
    private val other = "wss://other.relay.test"
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)
    private val carol = "c".repeat(64)

    @Test
    fun decisionRoundTrips() =
        runBlocking {
            val store = newStore()
            assertNull(store.loadDecision(relay))

            store.storeDecision(relay, RelayAuthDecision.ALLOW)
            assertEquals(RelayAuthDecision.ALLOW, store.loadDecision(relay))

            store.storeDecision(relay, RelayAuthDecision.DENY)
            assertEquals(RelayAuthDecision.DENY, store.loadDecision(relay))
        }

    @Test
    fun allDecisionsReversesTheHashedKeyBackToTheUrl() =
        runBlocking {
            val store = newStore()
            store.storeDecision(relay, RelayAuthDecision.ALLOW)
            store.storeDecision(other, RelayAuthDecision.DENY)

            assertEquals(
                mapOf(relay to RelayAuthDecision.ALLOW, other to RelayAuthDecision.DENY),
                store.allDecisions(),
            )
        }

    @Test
    fun recordUseMergesCounterpartiesAcrossCallsGroupedByKind() =
        runBlocking {
            val store = newStore()
            store.recordUse(relay, mapOf(AuthPurposeKind.SEND_DM to setOf(alice)))
            store.recordUse(relay, mapOf(AuthPurposeKind.SEND_DM to setOf(bob)))
            store.recordUse(relay, mapOf(AuthPurposeKind.NOTIFY_INBOX to setOf(carol)))

            assertEquals(
                mapOf(
                    AuthPurposeKind.SEND_DM to setOf(alice, bob),
                    AuthPurposeKind.NOTIFY_INBOX to setOf(carol),
                ),
                store.loadRationale(relay),
            )
        }

    @Test
    fun recordUseAlwaysStoresANewCounterpartyEvenRightAfterAWrite() =
        runBlocking {
            // The write throttle must never drop a genuinely new grant: a second call with a new
            // counterparty, moments after the first, still has to persist it.
            val store = newStore()
            store.recordUse(relay, mapOf(AuthPurposeKind.SEND_DM to setOf(alice)))
            store.recordUse(relay, mapOf(AuthPurposeKind.SEND_DM to setOf(bob)))

            assertEquals(setOf(alice, bob), store.loadRationale(relay)[AuthPurposeKind.SEND_DM])
        }

    @Test
    fun recordUseIsIdempotentForTheSameCounterparty() =
        runBlocking {
            val store = newStore()
            store.recordUse(relay, mapOf(AuthPurposeKind.SEND_DM to setOf(alice)))
            store.recordUse(relay, mapOf(AuthPurposeKind.SEND_DM to setOf(alice)))

            assertEquals(setOf(alice), store.loadRationale(relay)[AuthPurposeKind.SEND_DM])
        }

    @Test
    fun recordUseCapsTheStoredCounterpartySet() =
        runBlocking {
            val store = newStore()
            val many = (0 until 100).map { "%064x".format(it) }.toSet()
            store.recordUse(relay, mapOf(AuthPurposeKind.READ_OUTBOX to many))

            assertEquals(64, store.loadRationale(relay)[AuthPurposeKind.READ_OUTBOX]?.size)
        }

    @Test
    fun recordUsePopulatesLastUsed() =
        runBlocking {
            val store = newStore()
            store.recordUse(relay, mapOf(AuthPurposeKind.SEND_DM to setOf(alice)))

            val ts = store.allLastUsed()[relay]
            assertTrue("expected a positive last-used timestamp, got $ts", (ts ?: 0L) > 0L)
        }

    @Test
    fun clearRationaleDropsRationaleAndPrunesTheReverseLookupWhenNoDecisionRemains() =
        runBlocking {
            val store = newStore()
            store.recordUse(relay, mapOf(AuthPurposeKind.SEND_DM to setOf(alice)))
            store.clearRationale(relay)

            assertTrue(store.loadRationale(relay).isEmpty())
            // The shared url + last-used keys must be pruned too, or they'd orphan the reverse lookup.
            assertFalse(relay in store.allLastUsed())
            assertFalse(relay in store.allRationales())
        }

    @Test
    fun clearRationaleKeepsTheReverseLookupWhenADecisionStillRemains() =
        runBlocking {
            val store = newStore()
            store.storeDecision(relay, RelayAuthDecision.ALLOW)
            store.recordUse(relay, mapOf(AuthPurposeKind.SEND_DM to setOf(alice)))
            store.clearRationale(relay)

            // The decision must survive, and allDecisions still resolves the url (reverse lookup intact).
            assertEquals(RelayAuthDecision.ALLOW, store.loadDecision(relay))
            assertEquals(mapOf(relay to RelayAuthDecision.ALLOW), store.allDecisions())
        }

    @Test
    fun clearDecisionKeepsRationaleAndItsReverseLookup() =
        runBlocking {
            val store = newStore()
            store.storeDecision(relay, RelayAuthDecision.DENY)
            store.recordUse(relay, mapOf(AuthPurposeKind.SEND_DM to setOf(alice)))
            store.clearDecision(relay)

            assertNull(store.loadDecision(relay))
            // Rationale (and the url it depends on for allRationales) must survive the decision clear.
            assertEquals(
                mapOf(AuthPurposeKind.SEND_DM to setOf(alice)),
                store.allRationales()[relay],
            )
        }
}
