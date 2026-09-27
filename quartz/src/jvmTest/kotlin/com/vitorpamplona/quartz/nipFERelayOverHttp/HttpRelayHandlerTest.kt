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
package com.vitorpamplona.quartz.nipFERelayOverHttp

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.server.RelayServerBase
import com.vitorpamplona.quartz.nip01Core.relay.server.RelayServerListener
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.RequestContext
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.SessionBackend
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.FullAuthPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.IRelayPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.LimitsPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PassThroughPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PolicyResult
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.RelayLimits
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.VerifyPolicy
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nip77Negentropy.NegentropySettings
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** NIP-FE's handler over a relay engine and an in-memory backend: status, lines and auth. */
class HttpRelayHandlerTest {
    /** Everything the backend holds; a filter naming [STALLED_KIND] never answers, one naming [TRICKLE_KIND] never ends. */
    private class MemoryBackend : SessionBackend {
        val events = CopyOnWriteArrayList<Event>()

        override suspend fun query(
            ctx: RequestContext,
            filters: List<Filter>,
            onEach: (Event) -> Unit,
            onEose: () -> Unit,
        ) {
            if (filters.any { it.kinds?.contains(STALLED_KIND) == true }) awaitCancellation()
            events.filter { e -> filters.any { it.match(e) } }.forEach(onEach)
            if (filters.any { it.kinds?.contains(TRICKLE_KIND) == true }) awaitCancellation()
            onEose()
            awaitCancellation()
        }

        override suspend fun count(
            ctx: RequestContext,
            filters: List<Filter>,
        ) = events.count { e -> filters.any { it.match(e) } }

        override suspend fun submit(
            event: Event,
            onComplete: (IEventStore.InsertOutcome) -> Unit,
        ) {
            if (events.none { it.id == event.id }) events += event
            onComplete(IEventStore.InsertOutcome.Accepted)
        }
    }

    /** Refuses every read from a connection nobody signed in on, as an AUTH-gated relay does. */
    private class SignedInOnly : PassThroughPolicy() {
        @Volatile private var scope: RequestContext? = null

        override fun onConnect(
            scope: RequestContext,
            send: (Message) -> Unit,
        ) {
            this.scope = scope
        }

        override fun accept(cmd: ReqCmd): PolicyResult<ReqCmd> =
            if (scope?.authenticatedUsers?.isNotEmpty() == true) {
                PolicyResult.Accepted(cmd)
            } else {
                PolicyResult.Rejected(MachineReadablePrefix.AUTH_REQUIRED.format("sign in"))
            }
    }

    private class MemoryRelay(
        override val backend: SessionBackend,
        policies: () -> IRelayPolicy,
        limits: RelayLimits? = RelayLimits(maxMessageLength = 4096),
    ) : RelayServerBase(
            policyBuilder = policies,
            parentContext = SupervisorJob(),
            negentropySettings = NegentropySettings.Default,
            listener = RelayServerListener.None,
            limits = limits,
        ) {
        constructor(backend: SessionBackend, signedInOnly: Boolean) :
            this(backend, { if (signedInOnly) VerifyPolicy + SignedInOnly() else VerifyPolicy })
    }

    /** One answer as the host would see it. */
    private class Recorded : HttpRelayResponse {
        var status = 0
        val lines = mutableListOf<String>()
        var streamed = false

        override suspend fun single(
            status: Int,
            frame: String,
        ) {
            this.status = status
            lines += frame
        }

        override suspend fun stream(lines: suspend HttpRelayLines.() -> Unit) {
            status = 200
            streamed = true
            val out = this.lines
            object : HttpRelayLines {
                override suspend fun line(frame: String) {
                    out += frame
                }

                override suspend fun flush() {}
            }.lines()
        }
    }

    private val origin = "https://relay.example"
    private val alice = NostrSignerSync()
    private val backend = MemoryBackend()

    private fun handler(
        signedInOnly: Boolean = false,
        deadline: Duration = 5.seconds,
    ) = HttpRelayHandler(MemoryRelay(backend, signedInOnly), origins = { listOf(origin) }, deadline = deadline)

    private fun HttpRelayHandler.ask(
        frame: String,
        authorization: String? = null,
    ) = Recorded().also { runBlocking { handle(HttpRelayRequest(authorization, frame.encodeToByteArray()), it) } }

    private fun req(filter: String) = """["REQ","q",$filter]"""

    private fun count(filter: String) = """["COUNT","c",$filter]"""

    private fun publish(event: Event) = """["EVENT",${event.toJson()}]"""

    private fun note(
        content: String,
        at: Long = 1_700_000_000L,
    ) = alice.sign<Event>(at, 1, emptyArray(), content)

    private fun token(
        frame: String,
        url: String = origin,
    ) = alice.sign(HTTPAuthorizationEvent.build(url, "POST", frame.encodeToByteArray(), System.currentTimeMillis() / 1000) {}).toAuthToken()

    @Test
    fun aReqStreamsItsEventsAndEndsOnEose() {
        val a = note("a")
        val b = note("b")
        backend.events += listOf(a, b)
        val answer = handler().ask(req("""{"kinds":[1]}"""))
        assertEquals(200, answer.status)
        assertTrue(answer.streamed)
        assertEquals("""["EOSE","q"]""", answer.lines.last())
        assertEquals(
            setOf(a.id, b.id),
            answer.lines
                .dropLast(1)
                .map { l -> listOf(a, b).first { it.id in l }.id }
                .toSet(),
        )
    }

    @Test
    fun framesGoOutAsTheSocketSendsThemWithTheClientsSubscriptionId() {
        val found = note("found")
        backend.events += found
        val answer = handler().ask("""["REQ","mine",{"kinds":[1]}]""")
        assertEquals(listOf("""["EVENT","mine",${found.toJson()}]""", """["EOSE","mine"]"""), answer.lines)
    }

    @Test
    fun anEmptyReqIsOneEoseLine() {
        val answer = handler().ask(req("""{"kinds":[30000]}"""))
        assertEquals(200, answer.status)
        assertEquals(listOf("""["EOSE","q"]"""), answer.lines)
    }

    @Test
    fun anEventIsAnsweredByItsOkAndAForgeryIsRefused() {
        val posted = note("posted")
        val ok = handler().ask(publish(posted))
        assertEquals(200, ok.status)
        assertEquals(listOf("""["OK","${posted.id}",true,""]"""), ok.lines)
        assertTrue(backend.events.any { it.id == posted.id })

        val forged = Event(posted.id, posted.pubKey, posted.createdAt, posted.kind, posted.tags, "tampered", posted.sig)
        val refused = handler().ask(publish(forged))
        assertEquals(400, refused.status, refused.lines.toString())
        assertTrue(refused.lines.single().startsWith("""["OK","${forged.id}",false,"""), refused.lines.toString())
    }

    @Test
    fun aCountIsOneCountLine() {
        backend.events += listOf(note("a"), note("b"), note("c"))
        val answer = handler().ask(count("""{"kinds":[1]}"""))
        assertEquals(200, answer.status)
        assertTrue(answer.lines.single().startsWith("""["COUNT","c",{"count":3"""), answer.lines.toString())
    }

    @Test
    fun aBodyThatIsNotAReqCountOrEventIsA400AndOneOverTheLimitA413() {
        for (body in listOf("[]", """{"kinds":[1]}""", "not json", """["CLOSE","q"]""", """["AUTH",{"id":"x"}]""", """["NEG-CLOSE","n"]""")) {
            val answer = handler().ask(body)
            assertEquals(400, answer.status, body)
            assertTrue(answer.lines.single().startsWith("""["NOTICE","invalid:"""), answer.lines.toString())
        }
        // A REQ the engine refuses as a command: its own NOTICE, the command never ran.
        val empty = handler().ask("""["REQ","",{"kinds":[1]}]""")
        assertEquals(400, empty.status)
        assertTrue(empty.lines.single().startsWith("""["NOTICE","""), empty.lines.toString())
        // Over the relay's message length, in characters, as the socket measures it.
        val big = handler().ask(req("""{"search":"${"x".repeat(4_096)}"}"""))
        assertEquals(413, big.status)
        assertTrue(big.lines.single().startsWith("""["NOTICE","invalid:"""), big.lines.toString())
    }

    @Test
    fun aNip98SignatureSignsTheSessionInAndAnotherSchemeDoesNot() {
        backend.events += note("gated")
        val gated = handler(signedInOnly = true)
        val frame = req("""{"kinds":[1]}""")

        val anonymous = gated.ask(frame)
        assertEquals(401, anonymous.status)
        assertTrue(anonymous.lines.single().startsWith("""["CLOSED","q","auth-required:"""))

        assertEquals(401, gated.ask(frame, "Basic dXNlcjpwYXNz").status, "Basic is not addressed to the relay")

        val signed = gated.ask(frame, token(frame))
        assertEquals(200, signed.status, signed.lines.toString())
        assertEquals("""["EOSE","q"]""", signed.lines.last())
    }

    @Test
    fun aTokenSignsOnlyItsBodyAndIsGoodAgainWithinItsWindow() {
        val h = handler()
        val frame = req("""{"kinds":[1]}""")
        val signed = token(frame)
        assertEquals(200, h.ask(frame, signed).status)
        assertEquals(200, h.ask(frame, signed).status, "the same body again only repeats the read")
        val other = h.ask(req("""{"kinds":[0]}"""), signed)
        assertEquals(401, other.status)
        assertTrue("payload" in other.lines.single(), other.lines.toString())
        assertTrue(other.lines.single().startsWith("""["CLOSED","q","auth-required:"""), other.lines.toString())
    }

    @Test
    fun aTokenOutsideItsSixtySecondsIsRefused() {
        val frame = req("""{"kinds":[1]}""")
        val stale = alice.sign(HTTPAuthorizationEvent.build(origin, "POST", frame.encodeToByteArray(), System.currentTimeMillis() / 1000 - 120) {}).toAuthToken()
        assertEquals(401, handler().ask(frame, stale).status)
    }

    @Test
    fun anEventRefusedForItsTokenIsAnOkFalse() {
        val posted = publish(note("unsigned"))
        val answer = handler().ask(posted, token(req("{}")))
        assertEquals(401, answer.status)
        assertTrue(answer.lines.single().startsWith("""["OK","""), answer.lines.toString())
        assertTrue(""",false,"auth-required:""" in answer.lines.single(), answer.lines.toString())
    }

    @Test
    fun noFirstFrameWithinTheDeadlineIsA503() {
        val answer = handler(deadline = 300.milliseconds).ask(req("""{"kinds":[$STALLED_KIND]}"""))
        assertEquals(503, answer.status)
        assertTrue(answer.lines.single().startsWith("""["NOTICE","error: no answer"""))
    }

    @Test
    fun aDeadlineMidAnswerEndsOnAClosedLine() {
        val found = note("found")
        backend.events += found
        val answer = handler(deadline = 300.milliseconds).ask(req("""{"kinds":[1,$TRICKLE_KIND]}"""))
        assertEquals(200, answer.status)
        assertTrue(found.id in answer.lines.first())
        assertTrue(answer.lines.last().startsWith("""["CLOSED","q","error: the answer ran past"""), answer.lines.toString())
    }

    @Test
    fun aReaderThatStopsReadingIsDroppedAtTheHardStop() {
        backend.events += note("found")
        val h = HttpRelayHandler(MemoryRelay(backend, false), origins = { listOf(origin) }, deadline = 100.milliseconds, tailGrace = 100.milliseconds)
        val stalled =
            object : HttpRelayResponse {
                override suspend fun single(
                    status: Int,
                    frame: String,
                ) = error("streams")

                override suspend fun stream(lines: suspend HttpRelayLines.() -> Unit) =
                    object : HttpRelayLines {
                        override suspend fun line(frame: String) = awaitCancellation()

                        override suspend fun flush() {}
                    }.lines()
            }
        assertFailsWith<HttpRelayReaderStalled> {
            runBlocking { h.handle(HttpRelayRequest(null, req("""{"kinds":[1,$TRICKLE_KIND]}""").encodeToByteArray()), stalled) }
        }
    }

    @Test
    fun theStringOnlyConnectStillSendsWireJson() {
        backend.events += note("socket")
        val out = CopyOnWriteArrayList<String>()
        val session = MemoryRelay(backend, false).connect { out += it }
        runBlocking { session.receive("""["REQ","s",{"kinds":[1]}]""") }
        assertTrue(out.any { it.startsWith("""["EVENT","s",""") } && out.contains("""["EOSE","s"]"""), out.toString())
        session.close()
    }

    @Test
    fun aDeeplyNestedBodyIsA400NotAStackOverflow() {
        for (body in listOf(req("""{"a":""".repeat(2_000) + "1" + "}".repeat(2_000)), "[".repeat(20_000) + "]".repeat(20_000))) {
            val answer = HttpRelayHandler(MemoryRelay(backend, { VerifyPolicy }, limits = null), origins = { listOf(origin) }).ask(body)
            assertEquals(400, answer.status, body.take(20))
        }
    }

    @Test
    fun aFullAuthPolicyRefusesTransportSignInUntilItOptsIn() {
        val frame = req("""{"kinds":[1]}""")
        val relayUrl = RelayUrlNormalizer.normalize("wss://relay.example")
        val refusing =
            MemoryRelay(backend, {
                object : FullAuthPolicy(relayUrl) {
                    override suspend fun authorize(event: RelayAuthEvent): Unit = error("backend rejected user")
                }
            })
        val refused = HttpRelayHandler(refusing, origins = { listOf(origin) }).ask(frame, token(frame))
        assertEquals(403, refused.status, refused.lines.toString())
        assertTrue(refused.lines.single().startsWith("""["CLOSED","q","restricted:"""), refused.lines.toString())

        val optingIn =
            MemoryRelay(backend, {
                object : FullAuthPolicy(relayUrl) {
                    override suspend fun authorizeTransport(pubkey: HexKey): String? = null
                }
            })
        val signed = HttpRelayHandler(optingIn, origins = { listOf(origin) }).ask(frame, token(frame))
        assertEquals(200, signed.status, signed.lines.toString())
    }

    @Test
    fun aMessageLimitInThePolicyChainStillRuns() {
        val limited = MemoryRelay(backend, { LimitsPolicy(RelayLimits(maxMessageLength = 4096)) + VerifyPolicy }, limits = null)
        val answer = HttpRelayHandler(limited, origins = { listOf(origin) }).ask(req("""{"search":"${"x".repeat(20_000)}"}"""))
        assertEquals(400, answer.status, answer.lines.toString())
        assertTrue(answer.lines.single().startsWith("""["NOTICE","invalid: message too large"""), answer.lines.toString())
    }

    @Test
    fun aMultiByteEventUnderTheCharacterLimitIsAccepted() {
        // 1,500 CJK characters: about 4,500 UTF-8 bytes, well under 4,096 characters as the engine counts.
        val answer = handler().ask(publish(note("中".repeat(1_500))))
        assertEquals(200, answer.status, answer.lines.toString())
    }

    @Test
    fun aBackendFailureIsA500OkFalse() {
        val failing =
            object : SessionBackend by backend {
                override suspend fun submit(
                    event: Event,
                    onComplete: (IEventStore.InsertOutcome) -> Unit,
                ): Unit = error("db is down")
            }
        val lost = note("lost")
        val answer = HttpRelayHandler(MemoryRelay(failing, { VerifyPolicy }), origins = { listOf(origin) }).ask(publish(lost))
        assertEquals(500, answer.status, answer.lines.toString())
        assertTrue(answer.lines.single().startsWith("""["OK","${lost.id}",false,"error:"""), answer.lines.toString())
    }

    @Test
    fun anInfiniteDeadlineStillStreams() {
        backend.events += note("forever")
        val answer = handler(deadline = Duration.INFINITE).ask(req("""{"kinds":[1]}"""))
        assertEquals(200, answer.status)
        assertEquals("""["EOSE","q"]""", answer.lines.last())
    }

    @Test
    fun aReaderStalledOnASingleAnswerIsDropped() {
        val h = HttpRelayHandler(MemoryRelay(backend, false), origins = { listOf(origin) }, deadline = 100.milliseconds, tailGrace = 100.milliseconds)
        val stalled =
            object : HttpRelayResponse {
                override suspend fun single(
                    status: Int,
                    frame: String,
                ) = awaitCancellation()

                override suspend fun stream(lines: suspend HttpRelayLines.() -> Unit) = error("single")
            }
        assertFailsWith<HttpRelayReaderStalled> {
            runBlocking { h.handle(HttpRelayRequest(null, count("""{"kinds":[1]}""").encodeToByteArray()), stalled) }
        }
    }

    @Test
    fun aBodyCannotCarryASecondCommand() {
        val smuggled = note("smuggled")
        val answer = handler().ask(req("""{"kinds":[1]}""") + publish(smuggled))
        assertTrue(answer.lines.last().let { it == """["EOSE","q"]""" || it.startsWith("""["NOTICE",""") }, answer.lines.toString())
        assertTrue(backend.events.none { it.id == smuggled.id }, "only the REQ ran")
    }

    @Test
    fun aTokenSignedAtAnyOfTheRelaysAddressesVerifies() {
        val onion = "http://relayxyz.onion/"
        val h = HttpRelayHandler(MemoryRelay(backend, true), origins = { listOf(origin, onion) })
        val frame = req("""{"kinds":[1]}""")
        assertEquals(200, h.ask(frame, token(frame, onion)).status)
        assertEquals(200, h.ask(frame, token(frame, "http://relayxyz.onion")).status, "with or without the trailing slash")
        assertEquals(200, h.ask(frame, token(frame, "$origin/")).status)
        assertEquals(401, h.ask(frame, token(frame, "https://elsewhere.example")).status)
    }

    private companion object {
        const val STALLED_KIND = 7
        const val TRICKLE_KIND = 8
    }
}
