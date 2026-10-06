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
package com.vitorpamplona.quartz.buzz.managedAgents

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManagedAgentHintProviderTest {
    private val author = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val agent = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val friend = "7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377"
    private val id = "00".repeat(32)
    private val sig = "00".repeat(64)

    @Test
    fun agentAllowlistAndPersonaAreLinked() {
        val content =
            ManagedAgentContent(
                name = "Scout",
                personaId = "reviewer",
                parallelism = 1,
                respondTo = RespondTo.ALLOWLIST,
                respondToAllowlist = listOf(friend, "junk"),
            )
        val template = ManagedAgentEvent.build(content, agent)
        val event = ManagedAgentEvent(id, author, template.createdAt, template.tags, template.content, sig)

        assertEquals(listOf(agent, friend), event.linkedPubKeys())
        assertTrue(event.pubKeyHints().isEmpty())
        assertEquals(listOf("30175:$author:reviewer"), event.linkedAddressIds())
        assertTrue(event.addressHints().isEmpty())
    }

    @Test
    fun malformedContentYieldsOnlyTheDTagAgent() {
        val event = ManagedAgentEvent(id, author, 1, arrayOf(arrayOf("d", agent)), "{oops", sig)
        assertEquals(listOf(agent), event.linkedPubKeys())
        assertTrue(event.linkedAddressIds().isEmpty())
    }
}
