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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * One request the way every host runs it, iOS's included: the page's envelope through
 * [NappletRequestRouter] to the broker, the broker's question onto the host's [NappletPromptQueues],
 * the user's answer back, and the reply envelope the page receives. Runs on the JVM and the iOS
 * simulator.
 */
class NappletConsentRoundTripTest {
    private val signer = NostrSignerInternal(KeyPair("00".repeat(31).plus("07").hexToByteArray()))
    private val applet = NappletIdentity(authorPubKey = "aa".repeat(32), identifier = "demo")
    private val queried = mutableListOf<List<Filter>>()

    private val relay =
        object : NappletRelayGateway {
            override suspend fun publish(event: Event): List<String> = emptyList()

            override suspend fun query(filters: List<Filter>): List<Event> = emptyList<Event>().also { queried += filters }
        }

    // The host's queue, worded the way buildAccountNappletBroker words it (minus the account's names).
    private fun broker(prompts: NappletPromptQueues) =
        NappletBroker(
            signer = signer,
            ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()),
            consentPrompt =
                NappletConsentPrompt { app: NappletIdentity, capability: NappletCapability, _: NappletRequest ->
                    prompts.capability(app, NappletConsentInfo(app.identifier, app.coordinate, capability.name, "Read your relays", allowAlways = true))
                },
            relay = relay,
        )

    private val query = """{"type":"relay.query","id":"q1","filters":[{"kinds":[1]}]}"""

    private fun replyType(payload: String) =
        Json
            .parseToJsonElement(payload)
            .jsonObject["type"]
            .toString()
            .trim('"')

    @Test
    fun theUsersAnswerLetsTheRequestThroughAndThePageGetsItsReply() =
        runTest {
            val prompts = NappletPromptQueues()
            val outcome = async { NappletRequestRouter.route(broker(prompts), applet, NappletCapability.entries.toSet(), query) }

            val asked =
                prompts.capabilities.pending
                    .first { it.isNotEmpty() }
                    .single()
            assertEquals(applet.coordinate, asked.app)
            assertEquals(NappletCapability.RELAY.name, asked.info.capabilityLabel)
            assertTrue(queried.isEmpty(), "nothing runs before the user answers")
            asked.answer(GrantState.ALLOW_ONCE)

            val reply = assertIs<NappletRequestRouter.Outcome.Reply>(outcome.await())
            assertEquals("relay.query.result", replyType(reply.payload))
            assertFalse(reply.payload.contains("\"error\""), reply.payload)
            assertEquals(1, queried.size)
            assertTrue(
                prompts.capabilities.pending.value
                    .isEmpty(),
            )
        }

    @Test
    fun aDenialReachesThePageAndNothingRuns() =
        runTest {
            val prompts = NappletPromptQueues()
            val outcome = async { NappletRequestRouter.route(broker(prompts), applet, NappletCapability.entries.toSet(), query) }
            prompts.capabilities.pending
                .first { it.isNotEmpty() }
                .single()
                .answer(GrantState.DENY)

            val reply = assertIs<NappletRequestRouter.Outcome.Reply>(outcome.await())
            assertEquals("relay.query.result", replyType(reply.payload))
            assertTrue(reply.payload.contains("\"error\""), reply.payload)
            assertTrue(queried.isEmpty())
        }

    @Test
    fun closingTheAppDismissesItsQuestionAsARefusal() =
        runTest {
            val prompts = NappletPromptQueues()
            val outcome = async { NappletRequestRouter.route(broker(prompts), applet, NappletCapability.entries.toSet(), query) }
            prompts.capabilities.pending.first { it.isNotEmpty() }
            prompts.dismissApp(applet.coordinate)

            val reply = assertIs<NappletRequestRouter.Outcome.Reply>(outcome.await())
            assertTrue(reply.payload.contains("\"error\""), reply.payload)
            assertTrue(queried.isEmpty())
        }
}
