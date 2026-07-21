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
package com.vitorpamplona.quartz.buzz.interop

import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.OwnerAttestation
import com.vitorpamplona.quartz.buzz.oaOwnerAttestation.ownerAttestation
import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.CreateGroupEvent
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * LIVE interoperability test against a real running `block/buzz` relay.
 *
 * Skipped unless the `BUZZ_RELAY_WS` env var points at a relay (e.g.
 * `ws://127.0.0.1:3000`) whose membership already includes the test keys:
 *
 * ```
 * BUZZ_RELAY_WS=ws://127.0.0.1:3000 \
 * BUZZ_MEMBER_SK=<64-hex member secret key> \
 * BUZZ_OWNER_SK=<64-hex member secret key for the OA owner test> \
 * ./gradlew :quartz:jvmTest --tests "*BuzzRelayLiveInteropTest"
 * ```
 *
 * Proves, against the actual Rust implementation (not vectors): NIP-42 auth,
 * channel-scoped kind-40002 publishing, REQ round-trip, and NIP-OA/NIP-AA agent
 * authentication (an un-enrolled agent key connecting with an owner-signed `auth`
 * tag on its AUTH event).
 */
class BuzzRelayLiveInteropTest {
    private val relayWs = System.getenv("BUZZ_RELAY_WS")
    private val memberSk = System.getenv("BUZZ_MEMBER_SK")
    private val ownerSk = System.getenv("BUZZ_OWNER_SK")

    private class Frames : WebSocketListener() {
        val incoming = LinkedBlockingQueue<String>()
        val closed = LinkedBlockingQueue<String>()

        override fun onMessage(
            webSocket: WebSocket,
            text: String,
        ) {
            incoming.add(text)
        }

        override fun onFailure(
            webSocket: WebSocket,
            t: Throwable,
            response: Response?,
        ) {
            closed.add("failure: ${t.message}")
        }

        override fun onClosed(
            webSocket: WebSocket,
            code: Int,
            reason: String,
        ) {
            closed.add("closed: $code $reason")
        }

        fun next(timeoutSec: Long = 10): String =
            incoming.poll(timeoutSec, TimeUnit.SECONDS)
                ?: error("timed out waiting for a relay frame; closes=$closed")

        /** Waits for the frame whose label matches [label], skipping others (NOTICE etc). */
        fun nextOf(
            label: String,
            timeoutSec: Long = 10,
        ): String {
            repeat(20) {
                val f = next(timeoutSec)
                if (f.startsWith("[\"$label\"")) return f
            }
            error("no $label frame arrived")
        }
    }

    private fun connect(): Pair<WebSocket, Frames> {
        val frames = Frames()
        val client = OkHttpClient.Builder().pingInterval(15, TimeUnit.SECONDS).build()
        val ws = client.newWebSocket(Request.Builder().url(relayWs!!).build(), frames)
        return ws to frames
    }

    private fun challengeOf(authFrame: String): String {
        // ["AUTH","<challenge>"]
        val start = authFrame.indexOf("\",\"") + 3
        return authFrame.substring(start, authFrame.length - 2)
    }

    /** Performs the NIP-42 handshake and returns the open, authenticated socket. */
    private fun authedSocket(
        signer: NostrSignerInternal,
        attestation: OwnerAttestation? = null,
    ): Pair<WebSocket, Frames> =
        runBlocking {
            val (ws, frames) = connect()
            val challenge = challengeOf(frames.nextOf("AUTH"))
            val relayUrl = RelayUrlNormalizer.normalizeOrNull(relayWs!!)!!
            val auth =
                signer.sign(
                    RelayAuthEvent.build(relayUrl, challenge) {
                        attestation?.let { ownerAttestation(it) }
                    },
                )
            ws.send("""["AUTH",${auth.toJson()}]""")
            val ok = frames.nextOf("OK")
            assertTrue("true" in ok, "NIP-42 auth should be accepted, got: $ok")
            ws to frames
        }

    private fun envReady() = relayWs != null && memberSk != null

    @Test
    fun publishAndReadBackStreamMessage() {
        if (!envReady()) {
            println("SKIP: BUZZ_RELAY_WS / BUZZ_MEMBER_SK not set")
            return
        }
        runBlocking {
            val member = NostrSignerInternal(KeyPair(memberSk!!.hexToByteArray()))
            val (ws, frames) = authedSocket(member)

            // Buzz channels are relay-side rows keyed by UUID `h` values: the relay
            // parses the h tag as a Uuid (extract_channel_id in ingest.rs) and expects
            // a kind:9007 create before posting. Reuses Quartz's existing NIP-29
            // CreateGroupEvent — Buzz channels ARE NIP-29 groups with extra columns.
            val channelId = UUID.randomUUID().toString()
            val create =
                member.sign(
                    CreateGroupEvent.build(channelId) {
                        add(arrayOf("name", "amethyst-interop"))
                    },
                )
            ws.send("""["EVENT",${create.toJson()}]""")
            val createOk = frames.nextOf("OK")
            assertTrue("true" in createOk, "channel create (9007) should be accepted: $createOk")

            val msg =
                member.sign(
                    StreamMessageV2Event.build(channelId, "hello from Quartz over NIP-01"),
                )

            ws.send("""["EVENT",${msg.toJson()}]""")
            val ok = frames.nextOf("OK")
            assertTrue(msg.id in ok, "OK should reference our event id: $ok")
            assertTrue("true" in ok, "relay should accept the stream message: $ok")

            // REQ it back.
            ws.send("""["REQ","iq",{"kinds":[${StreamMessageV2Event.KIND}],"#h":["$channelId"]}]""")
            val evFrame = frames.nextOf("EVENT")
            val echoed = Event.fromJson(evFrame.substringAfter(",\"iq\",").dropLast(1))
            assertTrue(echoed is StreamMessageV2Event, "factory should type the echoed event")
            assertEquals(msg.id, echoed.id, "round-tripped event must be byte-identical (same id)")
            frames.nextOf("EOSE")
            ws.close(1000, "done")
        }
    }

    @Test
    fun agentAuthenticatesViaOwnerAttestation() {
        if (!envReady() || ownerSk == null) {
            println("SKIP: BUZZ_RELAY_WS / BUZZ_MEMBER_SK / BUZZ_OWNER_SK not set")
            return
        }
        // A brand-new agent key that is NOT a member; the owner (a member) attests it.
        val agent = NostrSignerInternal(KeyPair())
        val attestation =
            OwnerAttestation.sign(
                agentPubKey = agent.pubKey,
                conditions = "",
                ownerPrivKey = ownerSk.hexToByteArray(),
            )
        val (ws, _) = authedSocket(agent, attestation)
        ws.close(1000, "done")
    }
}
