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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import com.vitorpamplona.amethyst.commons.napplet.permissions.InMemoryNappletPermissionStore
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The host-agnostic routing brain. Verifies that one raw envelope maps to the right [Outcome] for
 * each host to act on — independent of any transport (Messenger / IPC / in-process).
 */
class NappletRequestRouterTest {
    private val signer = NostrSignerInternal(KeyPair("00".repeat(31).plus("07").hexToByteArray()))
    private val applet = NappletIdentity(authorPubKey = "aa".repeat(32), identifier = "demo")
    private val allDeclared = NappletCapability.entries.toSet()

    private class Prompt(
        private val answer: GrantState,
    ) : NappletConsentPrompt {
        override suspend fun request(
            identity: NappletIdentity,
            capability: NappletCapability,
            request: NappletRequest,
        ): GrantState = answer
    }

    private class FakeRelay : NappletRelayGateway {
        override suspend fun publish(event: Event): List<String> = emptyList()

        override suspend fun query(filters: List<Filter>): List<Event> = emptyList()
    }

    private fun broker(answer: GrantState = GrantState.ALLOW_ONCE) =
        NappletBroker(
            signer,
            NappletPermissionLedger(InMemoryNappletPermissionStore()),
            Prompt(answer),
            relay = FakeRelay(),
        )

    private suspend fun route(
        payload: String,
        answer: GrantState = GrantState.ALLOW_ONCE,
    ) = NappletRequestRouter.route(broker(answer), applet, allDeclared, payload)

    @Test
    fun relayCloseBecomesCloseSubscription() =
        runTest {
            assertEquals(
                NappletRequestRouter.Outcome.CloseSubscription("s1"),
                route("""{"type":"relay.close","subId":"s1"}"""),
            )
            // No subId → nothing to do.
            assertEquals(NappletRequestRouter.Outcome.Ignore, route("""{"type":"relay.close"}"""))
        }

    @Test
    fun resourceCancelRepliesDone() =
        runTest {
            val outcome = route("""{"type":"resource.cancel"}""")
            assertIs<NappletRequestRouter.Outcome.Reply>(outcome)
            assertTrue(outcome.payload.contains("resource.cancel.result"))
        }

    @Test
    fun malformedRequestRepliesFailed() =
        runTest {
            val outcome = route("""{"type":"inc.emit","topic":"t"}""")
            assertIs<NappletRequestRouter.Outcome.Reply>(outcome)
            assertTrue(outcome.payload.contains("failed"))
        }

    @Test
    fun queryRepliesWithItsResult() =
        runTest {
            val outcome = route("""{"type":"relay.query","filters":[{"kinds":[1]}]}""")
            assertIs<NappletRequestRouter.Outcome.Reply>(outcome)
            assertTrue(outcome.payload.contains("relay.query.result"))
        }

    @Test
    fun authorizedSubscribeOpensALiveSubscriptionWithAllFilters() =
        runTest {
            val outcome = route("""{"type":"relay.subscribe","subId":"s1","filters":[{"kinds":[1]},{"authors":["aa"]}]}""")
            assertIs<NappletRequestRouter.Outcome.OpenSubscription>(outcome)
            assertEquals("s1", outcome.subId)
            assertEquals(2, outcome.filters.size)
            assertEquals(listOf(1), outcome.filters[0].kinds)
            assertEquals(listOf("aa"), outcome.filters[1].authors)
        }

    @Test
    fun refusedSubscribePushesAnEoseToCloseIt() =
        runTest {
            val outcome = route("""{"type":"relay.subscribe","subId":"s1","filters":[{"kinds":[1]}]}""", answer = GrantState.DENY)
            assertIs<NappletRequestRouter.Outcome.Push>(outcome)
            assertEquals(1, outcome.payloads.size)
            assertTrue(outcome.payloads.first().contains("relay.eose"))
        }

    @Test
    fun identityWatchWhenDeclaredBecomesWatchIdentity() =
        runTest {
            assertEquals(
                NappletRequestRouter.Outcome.WatchIdentity,
                NappletRequestRouter.route(broker(), applet, allDeclared, """{"type":"identity.watch"}"""),
            )
        }

    @Test
    fun identityWatchWithoutDeclarationIsIgnored() =
        runTest {
            assertEquals(
                NappletRequestRouter.Outcome.Ignore,
                NappletRequestRouter.route(broker(), applet, emptySet(), """{"type":"identity.watch"}"""),
            )
        }

    @Test
    fun identityUnwatchBecomesUnwatchIdentity() =
        runTest {
            assertEquals(
                NappletRequestRouter.Outcome.UnwatchIdentity,
                NappletRequestRouter.route(broker(), applet, emptySet(), """{"type":"identity.unwatch"}"""),
            )
        }
}
