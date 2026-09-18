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
package com.vitorpamplona.contextvm.transport

import com.vitorpamplona.contextvm.core.CvmKinds
import com.vitorpamplona.contextvm.core.CvmTags
import com.vitorpamplona.contextvm.crypto.CvmGiftWrap
import com.vitorpamplona.contextvm.crypto.EncryptionMode
import com.vitorpamplona.contextvm.fixture.CvmFixtureServer
import com.vitorpamplona.contextvm.fixture.FixtureFaults
import com.vitorpamplona.contextvm.fixture.InMemoryRelayPool
import com.vitorpamplona.contextvm.jsonrpc.JsonRpcId
import com.vitorpamplona.contextvm.jsonrpc.JsonRpcRequest
import com.vitorpamplona.contextvm.jsonrpc.JsonRpcSuccess
import com.vitorpamplona.contextvm.mcp.McpParams
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * `CVM-CORE-*` and `CVM-16-*` driven end to end against the Tier C fixture.
 *
 * The fixture is what makes the negative half testable: no real server produces
 * a mismatched correlation tag or a malformed body on request.
 */
class CvmTransportTest {
    private val relays = InMemoryRelayPool()
    private val serverSigner = NostrSignerInternal(KeyPair())
    private val stableSigner = NostrSignerInternal(KeyPair())
    private val ephemeralSigner = NostrSignerInternal(KeyPair())
    private val signers = DualSigner(stableSigner, ephemeralSigner)

    private fun server(
        faults: FixtureFaults = FixtureFaults(),
        injectClientPubkey: Boolean = false,
        discoveryTags: List<Array<String>> = emptyList(),
        handler: suspend (String, JsonObject?) -> com.vitorpamplona.contextvm.jsonrpc.JsonRpcMessage = { _, _ ->
            JsonRpcSuccess(JsonRpcId.Num(0), buildJsonObject { put("ok", JsonPrimitive(true)) })
        },
    ) = CvmFixtureServer(
        relays = relays,
        signer = serverSigner,
        faults = faults,
        injectClientPubkey = injectClientPubkey,
        discoveryTags = discoveryTags,
        handler = handler,
    )

    private fun transport(crypto: CvmGiftWrap = CvmGiftWrap()) =
        CvmTransport(
            relays = relays,
            signers = signers,
            serverPubKey = serverSigner.pubKey,
            crypto = crypto,
        )

    /** Runs a request while pumping the fixture, so the answer arrives in-flight. */
    private suspend fun exchange(
        fixture: CvmFixtureServer,
        transport: CvmTransport,
        request: JsonRpcRequest,
        identity: DualSigner.Identity = DualSigner.Identity.EPHEMERAL,
        timeoutMs: Long = 5_000,
    ) = coroutineScope {
        val pending = async { transport.request(request, identity, timeoutMs) }
        yield()
        fixture.pump()
        pending.await()
    }

    @Test
    fun `CVM-CORE-10 completes a request against the fixture`() =
        runTest {
            val fixture = server()
            fixture.start()

            val response =
                exchange(fixture, transport(), JsonRpcRequest(JsonRpcId.Num(0), "tools/list"))

            assertIs<JsonRpcSuccess>(response)
        }

    @Test
    fun `CVM-CORE-11 subscribes before publishing so nothing is dropped`() =
        runTest {
            // The property that matters: an ephemeral kind published with nobody
            // listening is gone. If the transport ever published first, the
            // request event would land in `dropped`.
            val fixture = server()
            fixture.start()

            exchange(fixture, transport(), JsonRpcRequest(JsonRpcId.Num(0), "ping"))

            assertTrue(relays.dropped.isEmpty(), "dropped: ${relays.dropped.map { it.kind }}")
        }

    @Test
    fun `CVM-CORE-12 the relay drops an event nobody is subscribed to`() =
        runTest {
            // Guards the guard: confirms the fixture relay really does model
            // ephemeral delivery, so the previous test is meaningful.
            val fixture = server()
            // deliberately not started
            val transport = transport()

            assertFailsWith<TimeoutCancellationException> {
                transport.request(JsonRpcRequest(JsonRpcId.Num(0), "ping"), timeoutMs = 50)
            }
            assertTrue(relays.dropped.isNotEmpty())
        }

    @Test
    fun `CVM-CORE-13 ignores a response whose JSON-RPC id does not match`() =
        runTest {
            val fixture = server(faults = FixtureFaults(mismatchedResponseId = true))
            fixture.start()

            assertFailsWith<TimeoutCancellationException> {
                exchange(fixture, transport(), JsonRpcRequest(JsonRpcId.Num(7), "ping"), timeoutMs = 200)
            }
        }

    @Test
    fun `CVM-CORE-14 ignores a response correlated to a different request event`() =
        runTest {
            val fixture = server(faults = FixtureFaults(wrongCorrelationTag = true))
            fixture.start()

            assertFailsWith<TimeoutCancellationException> {
                exchange(fixture, transport(), JsonRpcRequest(JsonRpcId.Num(0), "ping"), timeoutMs = 200)
            }
        }

    @Test
    fun `CVM-CORE-15 accepts a response that omits the e tag but matches by id`() =
        runTest {
            // The `e` tag is the stronger signal but a peer may omit it; the
            // JSON-RPC id still correlates.
            val fixture = server(faults = FixtureFaults(omitCorrelationTag = true))
            fixture.start()

            val response = exchange(fixture, transport(), JsonRpcRequest(JsonRpcId.Num(0), "ping"))
            assertIs<JsonRpcSuccess>(response)
        }

    @Test
    fun `CVM-CORE-16 keeps waiting through a malformed payload`() =
        runTest {
            // A peer sending garbage must not fail an unrelated in-flight call.
            val fixture = server(faults = FixtureFaults(malformedResponse = true))
            fixture.start()

            assertFailsWith<TimeoutCancellationException> {
                exchange(fixture, transport(), JsonRpcRequest(JsonRpcId.Num(0), "ping"), timeoutMs = 200)
            }
        }

    @Test
    fun `CVM-CORE-17 routes notifications without resolving the request`() =
        runTest {
            val fixture = server(faults = FixtureFaults(noisePrefix = 3))
            fixture.start()

            val seen = mutableListOf<String>()
            val transport = transport()

            val response =
                coroutineScope {
                    val pending =
                        async {
                            transport.request(JsonRpcRequest(JsonRpcId.Num(0), "ping"), timeoutMs = 5_000) {
                                seen += it.method
                            }
                        }
                    yield()
                    fixture.pump()
                    pending.await()
                }

            assertEquals(3, seen.size, "every notification is delivered")
            assertIs<JsonRpcSuccess>(response, "and none of them completed the request")
        }

    @Test
    fun `CVM-4-20 the request goes out encrypted by default`() =
        runTest {
            val fixture = server()
            fixture.start()

            exchange(fixture, transport(), JsonRpcRequest(JsonRpcId.Num(0), "ping"))

            val first = relays.published.first()
            assertTrue(CvmKinds.isGiftWrap(first.kind), "kind ${first.kind} is not a wrap")
            assertNotEquals(ephemeralSigner.pubKey, first.pubKey, "the wrap hides the sender")
        }

    @Test
    fun `CVM-4-21 a disabled-encryption client publishes the bare message kind`() =
        runTest {
            val fixture =
                CvmFixtureServer(
                    relays = relays,
                    signer = serverSigner,
                    crypto = CvmGiftWrap(encryptionMode = EncryptionMode.DISABLED),
                    handler = { _, _ -> JsonRpcSuccess(JsonRpcId.Num(0), buildJsonObject {}) },
                )
            fixture.start()

            exchange(
                fixture,
                transport(CvmGiftWrap(encryptionMode = EncryptionMode.DISABLED)),
                JsonRpcRequest(JsonRpcId.Num(0), "ping"),
            )

            assertEquals(CvmKinds.MESSAGE, relays.published.first().kind)
        }

    @Test
    fun `CVM-16-01 the server derives clientPubkey rather than trusting the client`() =
        runTest {
            val fixture = server(injectClientPubkey = true)
            fixture.start()

            exchange(
                fixture,
                transport(CvmGiftWrap(encryptionMode = EncryptionMode.DISABLED)),
                JsonRpcRequest(JsonRpcId.Num(0), "tools/call", buildJsonObject { put("name", JsonPrimitive("x")) }),
            )

            val meta = fixture.handledParams.first()?.get(McpParams.META) as JsonObject
            assertEquals(
                ephemeralSigner.pubKey,
                (meta[McpParams.CLIENT_PUBKEY] as JsonPrimitive).content,
                "the injected identity is the event signer, not anything the client claimed",
            )
        }

    @Test
    fun `CVM-16-02 injection is off unless the server opts in`() =
        runTest {
            val fixture = server(injectClientPubkey = false)
            fixture.start()

            exchange(
                fixture,
                transport(CvmGiftWrap(encryptionMode = EncryptionMode.DISABLED)),
                JsonRpcRequest(JsonRpcId.Num(0), "ping"),
            )

            assertTrue(fixture.handledParams.first()?.containsKey(McpParams.META) != true)
        }

    @Test
    fun `CVM-35-10 learns the server baseline from its first direct message`() =
        runTest {
            val fixture =
                server(
                    discoveryTags =
                        listOf(
                            arrayOf("name", "Fixture"),
                            CvmTags.flag(CvmTags.SUPPORT_OPEN_STREAM),
                            arrayOf("unknown_future", "keep-me"),
                        ),
                )
            fixture.start()

            val transport = transport()
            exchange(fixture, transport, JsonRpcRequest(JsonRpcId.Num(0), "ping"))

            val peer = transport.peer!!
            assertEquals("Fixture", peer.name)
            assertTrue(peer.supportsOpenStream)
            assertTrue(
                peer.unknownTags.any { it[0] == "unknown_future" },
                "CEP-35 requires unknown discovery tags to survive",
            )
        }

    @Test
    fun `CVM-35-11 the stable identity is used only when asked for`() =
        runTest {
            val fixture = server(injectClientPubkey = true)
            fixture.start()

            exchange(
                fixture,
                transport(CvmGiftWrap(encryptionMode = EncryptionMode.DISABLED)),
                JsonRpcRequest(JsonRpcId.Num(0), "kp_publish"),
                identity = DualSigner.Identity.STABLE,
            )

            val meta = fixture.handledParams.first()?.get(McpParams.META) as JsonObject
            assertEquals(stableSigner.pubKey, (meta[McpParams.CLIENT_PUBKEY] as JsonPrimitive).content)
        }
}
