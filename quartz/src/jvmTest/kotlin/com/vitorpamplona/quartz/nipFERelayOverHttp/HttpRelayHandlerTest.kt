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

import com.vitorpamplona.negentropy.Negentropy
import com.vitorpamplona.negentropy.storage.StorageVector
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
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
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nip77Negentropy.NegentropySettings
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import com.vitorpamplona.quartz.nip98HttpAuth.Nip98AuthVerifier
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

/** NIP-FE's handler over a relay engine and an in-memory backend: status, lines, auth, and negentropy rounds. */
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

        override suspend fun snapshotIdsForNegentropy(
            filters: List<Filter>,
            maxEntries: Int?,
        ) = events.filter { e -> filters.any { it.match(e) } }.map { IdAndTime(it.createdAt, it.id) }
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
        command: HttpRelayCommand,
        body: String,
        authorization: String? = null,
    ) = Recorded().also { runBlocking { handle(HttpRelayRequest(command, authorization, body.encodeToByteArray()), it) } }

    private fun note(
        content: String,
        at: Long = 1_700_000_000L,
    ) = alice.sign<Event>(at, 1, emptyArray(), content)

    private fun token(
        command: HttpRelayCommand,
        body: String,
    ) = alice.sign(HTTPAuthorizationEvent.build(origin + command.path, "POST", body.encodeToByteArray(), System.currentTimeMillis() / 1000) {}).toAuthToken()

    @Test
    fun aReqStreamsItsEventsAndEndsOnEose() {
        val a = note("a")
        val b = note("b")
        backend.events += listOf(a, b)
        val answer = handler().ask(HttpRelayCommand.REQ, """{"kinds":[1]}""")
        assertEquals(200, answer.status)
        assertTrue(answer.streamed)
        assertEquals("""["EOSE"]""", answer.lines.last())
        assertEquals(
            setOf(a.id, b.id),
            answer.lines
                .dropLast(1)
                .map { l -> listOf(a, b).first { it.id in l }.id }
                .toSet(),
        )
    }

    @Test
    fun anEmptyReqIsOneEoseLine() {
        val answer = handler().ask(HttpRelayCommand.REQ, """[{"kinds":[30000]}]""")
        assertEquals(200, answer.status)
        assertEquals(listOf("""["EOSE"]"""), answer.lines)
    }

    @Test
    fun anEventIsAnsweredByItsOkAndAForgeryIsRefused() {
        val posted = note("posted")
        val ok = handler().ask(HttpRelayCommand.EVENT, posted.toJson())
        assertEquals(200, ok.status)
        assertEquals(listOf("""["OK","${posted.id}",true,""]"""), ok.lines)
        assertTrue(backend.events.any { it.id == posted.id })

        val forged = Event(posted.id, posted.pubKey, posted.createdAt, posted.kind, posted.tags, "tampered", posted.sig)
        val refused = handler().ask(HttpRelayCommand.EVENT, forged.toJson())
        assertEquals(400, refused.status, refused.lines.toString())
        assertTrue(refused.lines.single().startsWith("""["OK","${forged.id}",false,"""), refused.lines.toString())
    }

    @Test
    fun aCountIsOneCountLine() {
        backend.events += listOf(note("a"), note("b"), note("c"))
        val answer = handler().ask(HttpRelayCommand.COUNT, """{"kinds":[1]}""")
        assertEquals(200, answer.status)
        assertTrue(answer.lines.single().startsWith("""["COUNT",{"count":3"""), answer.lines.toString())
    }

    @Test
    fun aBodyThatIsNotTheCommandsArgumentsIsA400AndOneOverTheLimitA413() {
        // Not the command's shape: refused before any session opens.
        for ((command, body) in listOf(
            HttpRelayCommand.REQ to "[]",
            HttpRelayCommand.EVENT to """[{"id":"x"}]""",
            HttpRelayCommand.NEG to """[{"kinds":[1]}]""",
            HttpRelayCommand.COUNT to "not json",
        )) {
            val answer = handler().ask(command, body)
            assertEquals(400, answer.status, "$command '$body'")
            assertTrue(answer.lines.single().startsWith("""["CLOSED","invalid:"""), answer.lines.toString())
        }
        // The right shape with an inside the engine cannot read: its own NOTICE, the command never ran.
        val unreadable = handler().ask(HttpRelayCommand.EVENT, """{"id":"not an event"}""")
        assertEquals(400, unreadable.status)
        assertTrue(unreadable.lines.single().startsWith("""["NOTICE","""), unreadable.lines.toString())
        assertEquals(413, handler().ask(HttpRelayCommand.REQ, """{"search":"${"x".repeat(5_000)}"}""").status)
        // Under the byte cap, over it once wrapped in its frame: the engine measures the frame.
        assertEquals(413, handler().ask(HttpRelayCommand.REQ, """{"search":"${"x".repeat(4_096 - 20)}"}""").status)
    }

    @Test
    fun aNip98SignatureSignsTheSessionInAndAnotherSchemeDoesNot() {
        backend.events += note("gated")
        val gated = handler(signedInOnly = true)
        val body = """{"kinds":[1]}"""

        val anonymous = gated.ask(HttpRelayCommand.REQ, body)
        assertEquals(401, anonymous.status)
        assertTrue(anonymous.lines.single().startsWith("""["CLOSED","auth-required:"""))

        assertEquals(401, gated.ask(HttpRelayCommand.REQ, body, "Basic dXNlcjpwYXNz").status, "Basic is not addressed to the relay")

        val signed = gated.ask(HttpRelayCommand.REQ, body, token(HttpRelayCommand.REQ, body))
        assertEquals(200, signed.status, signed.lines.toString())
        assertEquals("""["EOSE"]""", signed.lines.last())
    }

    @Test
    fun aTokenForAnotherBodyOrASecondUseIsRefused() {
        val h = handler()
        val body = """{"kinds":[1]}"""
        val once = token(HttpRelayCommand.REQ, body)
        assertEquals(200, h.ask(HttpRelayCommand.REQ, body, once).status)
        val replayed = h.ask(HttpRelayCommand.REQ, body, once)
        assertEquals(401, replayed.status)
        assertTrue("replay" in replayed.lines.single(), replayed.lines.toString())
        val other = h.ask(HttpRelayCommand.REQ, """{"kinds":[0]}""", token(HttpRelayCommand.REQ, body))
        assertEquals(401, other.status)
        assertTrue("payload" in other.lines.single(), other.lines.toString())
    }

    @Test
    fun negentropyReconcilesInStatelessRounds() {
        val shared = (1..30).map { note("shared $it", 1_700_000_000L + it) }
        val onlyRelay = (1..12).map { note("relay $it", 1_700_001_000L + it) }
        val onlyClient = (1..7).map { note("client $it", 1_700_002_000L + it) }
        backend.events += shared + onlyRelay

        val mine = StorageVector().apply { (shared + onlyClient).forEach { insert(it.createdAt, it.id) } }.also { it.seal() }
        val client = Negentropy(mine, 0)
        var message = client.initiate().toHexKey()
        val have = mutableSetOf<String>()
        val need = mutableSetOf<String>()
        var rounds = 0
        while (true) {
            check(++rounds < 20) { "no convergence" }
            // A fresh handler each round: nothing on the server side carries over.
            val answer = handler().ask(HttpRelayCommand.NEG, """[{"kinds":[1]},"$message"]""")
            assertEquals(200, answer.status, answer.lines.toString())
            val reply =
                answer.lines
                    .single()
                    .substringAfter("""["NEG-MSG","""")
                    .substringBefore('"')
            val result = client.reconcile(reply.hexToByteArray())
            have += result.sendIds.map { it.toHexString() }
            need += result.needIds.map { it.toHexString() }
            message = result.msg?.toHexKey() ?: break
        }
        assertEquals(onlyClient.map { it.id }.toSet(), have)
        assertEquals(onlyRelay.map { it.id }.toSet(), need)
    }

    @Test
    fun aMalformedNegentropyRoundIsRefused() {
        val answer = handler().ask(HttpRelayCommand.NEG, """[{"kinds":[1]},"zz"]""")
        assertEquals(400, answer.status)
        assertTrue(answer.lines.single().startsWith("""["NEG-ERR","""), answer.lines.toString())
    }

    @Test
    fun noFirstFrameWithinTheDeadlineIsA503() {
        val answer = handler(deadline = 300.milliseconds).ask(HttpRelayCommand.REQ, """{"kinds":[$STALLED_KIND]}""")
        assertEquals(503, answer.status)
        assertTrue(answer.lines.single().startsWith("""["CLOSED","error: no answer"""))
    }

    @Test
    fun aDeadlineMidAnswerEndsOnAClosedLine() {
        val found = note("found")
        backend.events += found
        val answer = handler(deadline = 300.milliseconds).ask(HttpRelayCommand.REQ, """{"kinds":[1,$TRICKLE_KIND]}""")
        assertEquals(200, answer.status)
        assertTrue(found.id in answer.lines.first())
        assertTrue(answer.lines.last().startsWith("""["CLOSED","error: the answer ran past"""), answer.lines.toString())
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
            runBlocking { h.handle(HttpRelayRequest(HttpRelayCommand.REQ, null, """{"kinds":[1,$TRICKLE_KIND]}""".encodeToByteArray()), stalled) }
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
    fun framesCarryNoSubscriptionId() {
        val found = note("found")
        backend.events += found
        val answer = handler().ask(HttpRelayCommand.REQ, """{"kinds":[1]}""")
        assertTrue(answer.lines.first().startsWith("""["EVENT",{"""), answer.lines.toString())
        assertEquals("""["EOSE"]""", answer.lines.last())
    }

    @Test
    fun aDeeplyNestedBodyIsA400NotAStackOverflow() {
        for (body in listOf("""{"a":""".repeat(2_000) + "1" + "}".repeat(2_000), "[".repeat(20_000) + "]".repeat(20_000))) {
            val answer = HttpRelayHandler(MemoryRelay(backend, { VerifyPolicy }, limits = null), origins = { listOf(origin) }).ask(HttpRelayCommand.REQ, body)
            assertEquals(400, answer.status, body.take(20))
        }
    }

    @Test
    fun aFullAuthPolicyRefusesTransportSignInUntilItOptsIn() {
        val body = """{"kinds":[1]}"""
        val relayUrl = RelayUrlNormalizer.normalize("wss://relay.example")
        val refusing =
            MemoryRelay(backend, {
                object : FullAuthPolicy(relayUrl) {
                    override suspend fun authorize(event: RelayAuthEvent): Unit = error("backend rejected user")
                }
            })
        val refused = HttpRelayHandler(refusing, origins = { listOf(origin) }).ask(HttpRelayCommand.REQ, body, token(HttpRelayCommand.REQ, body))
        assertEquals(403, refused.status, refused.lines.toString())
        assertTrue(refused.lines.single().startsWith("""["CLOSED","restricted:"""), refused.lines.toString())

        val optingIn =
            MemoryRelay(backend, {
                object : FullAuthPolicy(relayUrl) {
                    override suspend fun authorizeTransport(pubkey: HexKey): String? = null
                }
            })
        val signed = HttpRelayHandler(optingIn, origins = { listOf(origin) }).ask(HttpRelayCommand.REQ, body, token(HttpRelayCommand.REQ, body))
        assertEquals(200, signed.status, signed.lines.toString())
    }

    @Test
    fun aFloodOfFreshTokensCannotFlushAReplay() {
        val h = HttpRelayHandler(MemoryRelay(backend, false), origins = { listOf(origin) }, verifier = Nip98AuthVerifier(maxReplayEntries = 4))

        // A token's id is its hash, so tokens signed the same second over the same body are one token:
        // every one here names its own body.
        fun body(n: Int) = """{"kinds":[$n]}"""
        val victim = token(HttpRelayCommand.REQ, body(0))
        assertEquals(200, h.ask(HttpRelayCommand.REQ, body(0), victim).status)
        for (n in 1..3) assertEquals(200, h.ask(HttpRelayCommand.REQ, body(n), token(HttpRelayCommand.REQ, body(n))).status)
        val flooding = h.ask(HttpRelayCommand.REQ, body(4), token(HttpRelayCommand.REQ, body(4)))
        assertEquals(429, flooding.status, "a full cache refuses the new token: ${flooding.lines}")
        val replayed = h.ask(HttpRelayCommand.REQ, body(0), victim)
        assertEquals(401, replayed.status, "and still remembers the old one: ${replayed.lines}")
    }

    @Test
    fun aMessageLimitInThePolicyChainStillRuns() {
        val limited = MemoryRelay(backend, { LimitsPolicy(RelayLimits(maxMessageLength = 4096)) + VerifyPolicy }, limits = null)
        val answer = HttpRelayHandler(limited, origins = { listOf(origin) }).ask(HttpRelayCommand.REQ, """{"search":"${"x".repeat(20_000)}"}""")
        assertEquals(400, answer.status, answer.lines.toString())
        assertTrue(answer.lines.single().startsWith("""["NOTICE","invalid: message too large"""), answer.lines.toString())
    }

    @Test
    fun aMultiByteEventUnderTheCharacterLimitIsAccepted() {
        // 1,500 CJK characters: about 4,500 UTF-8 bytes, well under 4,096 characters as the engine counts.
        val posted = note("\u4E2D".repeat(1_500))
        val answer = handler().ask(HttpRelayCommand.EVENT, posted.toJson())
        assertEquals(200, answer.status, answer.lines.toString())
    }

    @Test
    fun aBackendFailureIsA500Line() {
        val failing =
            object : SessionBackend by backend {
                override suspend fun sealedNegentropyStorage(
                    filters: List<Filter>,
                    maxEntries: Int,
                ) = error("db is down")
            }
        val message = Negentropy(StorageVector().also { it.seal() }, 0).initiate().toHexKey()
        val answer = HttpRelayHandler(MemoryRelay(failing, { VerifyPolicy }), origins = { listOf(origin) }).ask(HttpRelayCommand.NEG, """[{"kinds":[1]},"$message"]""")
        assertEquals(500, answer.status, answer.lines.toString())
        assertTrue(answer.lines.single().startsWith("""["CLOSED","error:"""), answer.lines.toString())
    }

    @Test
    fun anInfiniteDeadlineStillStreams() {
        backend.events += note("forever")
        val answer = handler(deadline = Duration.INFINITE).ask(HttpRelayCommand.REQ, """{"kinds":[1]}""")
        assertEquals(200, answer.status)
        assertEquals("""["EOSE"]""", answer.lines.last())
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
            runBlocking { h.handle(HttpRelayRequest(HttpRelayCommand.COUNT, null, """{"kinds":[1]}""".encodeToByteArray()), stalled) }
        }
    }

    @Test
    fun theBodyShapeIsReadWithoutATree() {
        assertEquals("""["REQ","http",{"kinds":[1]}]""", HttpRelayCommand.REQ.frameOf(""" {"kinds":[1]} """))
        assertEquals("""["COUNT","http",{"a":"]"},{"b":"\"["}]""", HttpRelayCommand.COUNT.frameOf("""[{"a":"]"},{"b":"\"["}]"""))
        assertEquals("""["NEG-OPEN","http",{"kinds":[1]},"61"]""", HttpRelayCommand.NEG.frameOf("""[{"kinds":[1]},"61"]"""))
        assertEquals("""["EVENT",{"id":"x"}]""", HttpRelayCommand.EVENT.frameOf("""{"id":"x"}"""))
        for (bad in listOf("", "[]", "{", "[{}", "{}}", "{]", "[{}]x", "{} {}", "[{},]", "[,{}]", "[{} {}]", "[1]", """[{},"x"]""", "null", "\"x\"")) {
            assertEquals(null, HttpRelayCommand.REQ.frameOf(bad), "REQ '$bad'")
        }
        for (bad in listOf("""[{"kinds":[1]}]""", """["61",{}]""", """[{},61]""", """[{},"61",1]""", """{"kinds":[1]}""")) {
            assertEquals(null, HttpRelayCommand.NEG.frameOf(bad), "NEG '$bad'")
        }
        for (bad in listOf("""[{"id":"x"}]""", "1")) assertEquals(null, HttpRelayCommand.EVENT.frameOf(bad), "EVENT '$bad'")
    }

    private companion object {
        const val STALLED_KIND = 7
        const val TRICKLE_KIND = 8
    }
}
