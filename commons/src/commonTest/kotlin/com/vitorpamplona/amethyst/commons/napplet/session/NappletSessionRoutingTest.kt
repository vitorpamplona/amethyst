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

import com.vitorpamplona.amethyst.commons.napplet.NappletAttendance
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletConsentPrompt
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentityWatch
import com.vitorpamplona.amethyst.commons.napplet.NappletIncBus
import com.vitorpamplona.amethyst.commons.napplet.NappletLiveSubscriptions
import com.vitorpamplona.amethyst.commons.napplet.NappletRelayGateway
import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import com.vitorpamplona.amethyst.commons.napplet.permissions.InMemoryNappletPermissionStore
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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

/** [NappletSessionRouting] as Android's broker service runs it: ids from the IPC, a broker that can go, waits and watches. */
class NappletSessionRoutingTest {
    private val author = "aa".repeat(32)
    private val account = "bb".repeat(32)
    private val signer = NostrSignerInternal(KeyPair("00".repeat(31).plus("07").hexToByteArray()))

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val attendance = NappletAttendance<String>()
    private val services = NappletSessionServices(attendance, NappletLiveSubscriptions(scope, attendance), NappletIncBus<String>()) { null }

    @AfterTest
    fun tearDown() = scope.cancel()

    private class Prompt(
        val gate: CompletableDeferred<Unit>? = null,
    ) : NappletConsentPrompt {
        override suspend fun request(
            identity: NappletIdentity,
            capability: NappletCapability,
            request: NappletRequest,
        ): GrantState {
            gate?.await()
            return GrantState.ALLOW_ONCE
        }
    }

    private class Relay : NappletRelayGateway {
        override suspend fun publish(event: Event): List<String> = emptyList()

        override suspend fun query(filters: List<Filter>): List<Event> = emptyList()
    }

    private fun broker(prompt: Prompt = Prompt()) = NappletBroker(signer, NappletPermissionLedger(InMemoryNappletPermissionStore()), prompt, relay = Relay())

    private class Wire {
        private val lock = KmpLock()
        val replies = ArrayList<Pair<String?, String>>()
        val pushes = ArrayList<String>()

        fun reply(
            id: String?,
            payload: String,
        ) = lock.withLock { replies += id to payload }

        fun push(payload: String) = lock.withLock { pushes += payload }

        fun replyTo(id: String) = lock.withLock { replies.firstOrNull { it.first == id }?.second }
    }

    private fun routing(
        wire: Wire,
        brokerOf: () -> NappletBroker? = { broker() },
        identityWatch: NappletIdentityWatch? = null,
        readsWait: Boolean = false,
    ) = NappletSessionRouting(
        owner = "surface",
        launch = TestLaunches.napplet(author),
        accountPubKey = account,
        account = { null },
        broker = brokerOf,
        services = services,
        fsSession = null,
        scope = scope,
        reply = wire::reply,
        push = wire::push,
        identityWatch = identityWatch,
        identityWatchId = "token",
        readsWaitForAttendance = readsWait,
    )

    // runTest's own delay is virtual: the routing runs on real threads.
    private suspend fun pause(ms: Long) = withContext(Dispatchers.Default) { delay(ms) }

    private fun ok(payload: String) =
        Json
            .parseToJsonElement(payload)
            .jsonObject["ok"]
            ?.jsonPrimitive
            ?.content

    @Test
    fun aReplyAnswersTheIdTheHostGaveNotThePayloads() =
        runTest {
            val wire = Wire()
            routing(wire).handle("""{"type":"identity.getPublicKey","id":"page-1"}""", "fire-7")
            eventually { wire.replyTo("fire-7") != null }
            assertEquals("true", ok(wire.replyTo("fire-7")!!))
        }

    @Test
    fun aGoneAccountFailsClosed() =
        runTest {
            val wire = Wire()
            routing(wire, brokerOf = { null }).handle("""{"type":"identity.getPublicKey","id":"a"}""", "a")
            eventually { wire.replyTo("a") != null }
            assertContains(wire.replyTo("a")!!, NappletSessionRouting.ACCOUNT_GONE)
        }

    @Test
    fun aDecryptingReadWaitsForTheUser() =
        runTest {
            val wire = Wire()
            val routing = routing(wire, readsWait = true)
            routing.handle("""{"type":"relay.query","id":"q1","filters":[{"kinds":[1]}]}""", "q1")
            pause(200)
            assertTrue(wire.replyTo("q1") == null, "answered while nobody looked")
            routing.setAttended(true)
            eventually { wire.replyTo("q1") != null }
        }

    @Test
    fun aCancelledResourceRequestNeverAnswers() =
        runTest {
            val wire = Wire()
            val gate = CompletableDeferred<Unit>()
            val routing = routing(wire, brokerOf = { broker(Prompt(gate)) })
            routing.handle("""{"type":"resource.bytes","id":"r1","url":"https://example.com/a.png"}""", "r1")
            pause(100)
            routing.handle("""{"type":"resource.cancel","id":"r1"}""", "r1")
            gate.complete(Unit)
            pause(300)
            assertTrue(wire.replyTo("r1") == null, "a cancelled request answered: ${wire.replies}")
        }

    @Test
    fun theIdentityStreamStartsAfterTheSnapshotAndEndsWithTheLaunch() =
        runTest {
            val key = MutableStateFlow(account)
            val watch = NappletIdentityWatch(scope) { key }
            val wire = Wire()
            val routing = routing(wire, identityWatch = watch)
            routing.handle("""{"type":"identity.getPublicKey","id":"k"}""", "k")
            eventually { wire.replyTo("k") != null }
            pause(50)
            key.value = ""
            eventually { wire.pushes.any { "identity.changed" in it } }

            routing.close()
            val before = wire.pushes.size
            key.value = account
            pause(100)
            assertEquals(before, wire.pushes.size)
        }
}
