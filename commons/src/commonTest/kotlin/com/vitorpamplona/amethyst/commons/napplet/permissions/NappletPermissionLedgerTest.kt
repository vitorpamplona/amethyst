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
package com.vitorpamplona.amethyst.commons.napplet.permissions

import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NappletPermissionLedgerTest {
    private val applet = NappletIdentity(authorPubKey = "aa".repeat(32), identifier = "chess")
    private val other = NappletIdentity(authorPubKey = "bb".repeat(32), identifier = "wallet")

    private fun ledger(store: NappletPermissionStore = InMemoryNappletPermissionStore()) = NappletPermissionLedger(store)

    @Test
    fun unknownGrantDefaultsToAsk() =
        runTest {
            val ledger = ledger()
            assertEquals(PermissionDecision.ASK, ledger.decide(applet, NappletCapability.IDENTITY))
        }

    @Test
    fun allowAlwaysIsPersistedAndAllows() =
        runTest {
            val store = InMemoryNappletPermissionStore()
            ledger(store).record(applet, NappletCapability.IDENTITY, GrantState.ALLOW_ALWAYS)

            // A brand-new ledger over the same store still sees the grant.
            assertEquals(PermissionDecision.ALLOW, ledger(store).decide(applet, NappletCapability.IDENTITY))
        }

    @Test
    fun denyIsPersistedAndBlocks() =
        runTest {
            val store = InMemoryNappletPermissionStore()
            ledger(store).record(applet, NappletCapability.RELAY, GrantState.DENY)
            assertEquals(PermissionDecision.DENY, ledger(store).decide(applet, NappletCapability.RELAY))
        }

    @Test
    fun sessionGrantAllowsButDoesNotSurviveANewLedger() =
        runTest {
            val store = InMemoryNappletPermissionStore()
            val ledger = ledger(store)
            ledger.record(applet, NappletCapability.IDENTITY, GrantState.ALLOW_SESSION)
            assertEquals(PermissionDecision.ALLOW, ledger.decide(applet, NappletCapability.IDENTITY))

            // A fresh ledger over the same store does not see the in-memory session grant.
            assertEquals(PermissionDecision.ASK, ledger(store).decide(applet, NappletCapability.IDENTITY))
        }

    @Test
    fun onceAndAskAreNeverRemembered() =
        runTest {
            val ledger = ledger()
            ledger.record(applet, NappletCapability.IDENTITY, GrantState.ALLOW_ONCE)
            ledger.record(applet, NappletCapability.RELAY, GrantState.ASK)
            assertEquals(PermissionDecision.ASK, ledger.decide(applet, NappletCapability.IDENTITY))
            assertEquals(PermissionDecision.ASK, ledger.decide(applet, NappletCapability.RELAY))
        }

    @Test
    fun persistentDenyWinsOverASessionAllow() =
        runTest {
            val ledger = ledger()
            ledger.record(applet, NappletCapability.IDENTITY, GrantState.ALLOW_SESSION)
            ledger.record(applet, NappletCapability.IDENTITY, GrantState.DENY)
            assertEquals(PermissionDecision.DENY, ledger.decide(applet, NappletCapability.IDENTITY))
        }

    @Test
    fun grantsAreScopedPerAppletCoordinate() =
        runTest {
            val ledger = ledger()
            ledger.record(applet, NappletCapability.IDENTITY, GrantState.ALLOW_ALWAYS)
            assertEquals(PermissionDecision.ALLOW, ledger.decide(applet, NappletCapability.IDENTITY))
            assertEquals(PermissionDecision.ASK, ledger.decide(other, NappletCapability.IDENTITY))
        }

    @Test
    fun revokeAllClearsPersistentAndSessionGrants() =
        runTest {
            val ledger = ledger()
            ledger.record(applet, NappletCapability.IDENTITY, GrantState.ALLOW_ALWAYS)
            ledger.record(applet, NappletCapability.RELAY, GrantState.ALLOW_SESSION)
            ledger.revokeAll(applet)
            assertEquals(PermissionDecision.ASK, ledger.decide(applet, NappletCapability.IDENTITY))
            assertEquals(PermissionDecision.ASK, ledger.decide(applet, NappletCapability.RELAY))
        }

    @Test
    fun allPersistedGrantsListsPersistedDecisionsByCoordinate() =
        runTest {
            val ledger = ledger()
            ledger.record(applet, NappletCapability.IDENTITY, GrantState.ALLOW_ALWAYS)
            ledger.record(applet, NappletCapability.RELAY, GrantState.DENY)
            ledger.record(applet, NappletCapability.STORAGE, GrantState.ALLOW_SESSION) // not persisted
            ledger.record(other, NappletCapability.WALLET, GrantState.DENY)

            val all = ledger.allPersistedGrants()
            assertEquals(
                mapOf(NappletCapability.IDENTITY to GrantState.ALLOW_ALWAYS, NappletCapability.RELAY to GrantState.DENY),
                all[applet.coordinate],
            )
            assertEquals(mapOf(NappletCapability.WALLET to GrantState.DENY), all[other.coordinate])
        }

    @Test
    fun revokeForgetsOnlyThatCapability() =
        runTest {
            val ledger = ledger()
            ledger.record(applet, NappletCapability.IDENTITY, GrantState.ALLOW_ALWAYS)
            ledger.record(applet, NappletCapability.RELAY, GrantState.ALLOW_ALWAYS)

            ledger.revoke(applet, NappletCapability.IDENTITY)

            assertEquals(PermissionDecision.ASK, ledger.decide(applet, NappletCapability.IDENTITY))
            assertEquals(PermissionDecision.ALLOW, ledger.decide(applet, NappletCapability.RELAY))
        }

    @Test
    fun aggregateHashDoesNotAffectTheLedgerKey() =
        runTest {
            val ledger = ledger()
            val v1 = applet.copy(aggregateHash = "11".repeat(32))
            val v2 = applet.copy(aggregateHash = "22".repeat(32))
            ledger.record(v1, NappletCapability.IDENTITY, GrantState.ALLOW_ALWAYS)
            // A code update (new aggregate hash) keeps the grant — same coordinate.
            assertEquals(PermissionDecision.ALLOW, ledger.decide(v2, NappletCapability.IDENTITY))
        }
}
