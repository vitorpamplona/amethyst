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
package com.vitorpamplona.amethyst.commons.napplet.session

import com.vitorpamplona.amethyst.commons.napplet.NappletHeldRequests
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NappletPageBridgeTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = KmpLock()
    private val forwarded = ArrayList<String>()
    private val engine = FakeNappletWebEngine()

    @AfterTest
    fun tearDown() = scope.cancel()

    private fun bridge(maxHeldAgeMs: Long = NappletHeldRequests.MAX_AGE_MS) = NappletPageBridge(scope, toTrusted = { lock.withLock { forwarded += it } }, evaluateInShell = engine::evaluateInShell, maxHeldAgeMs = maxHeldAgeMs)

    private fun forwarded() = lock.withLock { forwarded.toList() }

    private fun field(
        payload: String,
        name: String,
    ) = Json
        .parseToJsonElement(payload)
        .jsonObject[name]
        ?.jsonPrimitive
        ?.content

    @Test
    fun readsFlowWhileUnattendedAndActsWait() =
        runTest {
            val page = bridge()
            page.receive("""{"type":"relay.query","id":"q1"}""")
            page.receive("""{"type":"inc.emit","topic":"t"}""")
            page.receive("""{"type":"nostr.signEvent","id":"s1"}""")
            assertEquals(listOf("""{"type":"relay.query","id":"q1"}""", """{"type":"inc.emit","topic":"t"}"""), forwarded())
            page.setAttended(true)
            assertEquals("""{"type":"nostr.signEvent","id":"s1"}""", forwarded().last())
            // Attended, nothing waits.
            page.receive("""{"type":"relay.publish","id":"p1"}""")
            assertEquals("""{"type":"relay.publish","id":"p1"}""", forwarded().last())
        }

    @Test
    fun aHeldRequestExpiresWithAFailure() =
        runTest {
            val page = bridge(maxHeldAgeMs = 50)
            page.receive("""{"type":"relay.publish","id":"p1"}""")
            eventually { engine.delivered().isNotEmpty() }
            val reply = engine.delivered().single()
            assertEquals("p1", field(reply, "id"))
            assertContains(reply, NappletHeldRequests.EXPIRED)
            assertTrue(forwarded().isEmpty())
            // Back too late: nothing is sent for it.
            page.setAttended(true)
            assertTrue(forwarded().isEmpty())
        }

    @Test
    fun pastTheCapARequestIsRefusedAtOnce() =
        runTest {
            val page = bridge()
            repeat(NappletHeldRequests.MAX_HELD) { page.receive("""{"type":"relay.publish","id":"p$it"}""") }
            page.receive("""{"type":"relay.publish","id":"over"}""")
            eventually { engine.delivered().isNotEmpty() }
            val reply = engine.delivered().single()
            assertEquals("over", field(reply, "id"))
            assertContains(reply, NappletHeldRequests.TOO_MANY)
            page.setAttended(true)
            assertEquals(NappletHeldRequests.MAX_HELD, forwarded().size)
        }

    @Test
    fun deliveriesKeepTheirOrderAcrossBatches() =
        runTest {
            val page = bridge()
            repeat(500) { page.deliver("""{"n":$it}""") }
            eventually { engine.delivered().size == 500 }
            assertEquals((0 until 500).map { """{"n":$it}""" }, engine.delivered())
            // Bursts go in fewer scripts than payloads.
            assertTrue(engine.scriptCount() < 500)
        }

    @Test
    fun nothingIsDeliveredAfterClose() =
        runTest {
            val page = bridge()
            page.receive("""{"type":"relay.publish","id":"p1"}""")
            page.close()
            page.deliver("""{"late":true}""")
            page.receive("""{"type":"relay.query","id":"q1"}""")
            page.setAttended(true)
            withContext(Dispatchers.Default) { delay(100) }
            assertTrue(engine.delivered().isEmpty())
            assertTrue(forwarded().isEmpty())
        }

    @Test
    fun aReplyIdIsInjectedOnlyWhenThereIsOne() {
        assertEquals("""{"type":"x.result","id":"a"}""", withReplyId("""{"type":"x.result"}""", "a"))
        assertEquals("""{"type":"x.result"}""", withReplyId("""{"type":"x.result"}""", null))
        assertEquals("not json", withReplyId("not json", "a"))
    }
}
