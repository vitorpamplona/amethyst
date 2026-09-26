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
package com.vitorpamplona.quartz.contextvm.mcp

import com.vitorpamplona.quartz.contextvm.cep04Encryption.CvmGiftWrap
import com.vitorpamplona.quartz.contextvm.cep04Encryption.EncryptionMode
import com.vitorpamplona.quartz.contextvm.cep22OversizedTransfer.OversizedTransferSender
import com.vitorpamplona.quartz.contextvm.cep41OpenStreams.OpenStreamFrame
import com.vitorpamplona.quartz.contextvm.fixture.CvmFixtureServer
import com.vitorpamplona.quartz.contextvm.fixture.CvmRequest
import com.vitorpamplona.quartz.contextvm.fixture.InMemoryRelayPool
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcCodec
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcError
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcFailure
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcId
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcMessage
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcSuccess
import com.vitorpamplona.quartz.contextvm.transfer.ProgressToken
import com.vitorpamplona.quartz.contextvm.transport.CvmTransport
import com.vitorpamplona.quartz.contextvm.transport.DualSigner
import com.vitorpamplona.quartz.contextvm.transport.TimeoutMode
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The MCP client end to end, including both transfer profiles. */
class CvmMcpClientTest {
    private val relays = InMemoryRelayPool()
    private val serverSigner = NostrSignerInternal(KeyPair())
    private val clientSigner = NostrSignerInternal(KeyPair())
    private val plaintext = CvmGiftWrap(encryptionMode = EncryptionMode.DISABLED)

    private fun client() =
        CvmMcpClient(
            CvmTransport(
                relays = relays,
                signers = DualSigner(clientSigner, clientSigner),
                serverPubKey = serverSigner.pubKey,
                crypto = plaintext,
            ),
        )

    private fun fixture(handler: suspend (CvmRequest) -> JsonRpcMessage) =
        CvmFixtureServer(
            relays = relays,
            signer = serverSigner,
            crypto = plaintext,
            handler = handler,
        )

    /** The progressToken the client derives for the first call it makes. */
    private val firstCallToken = ProgressToken.Text("call-0")

    @Test
    fun `a second call on one client still correlates`() =
        runTest {
            // Every test here made exactly one call, so a server that answered
            // with a constant JSON-RPC id passed all of them -- and our own
            // fixture did precisely that until a two-call cordn test hung on it.
            // Ids advance per call and the client refuses a stale one, which is
            // correct and invisible until something makes the second call.
            val client = client()
            val fixture = fixture { request -> JsonRpcSuccess(request.id, buildJsonObject { put("ok", JsonPrimitive(true)) }) }
            fixture.start()

            val results =
                coroutineScope {
                    val pending =
                        async {
                            listOf(
                                client.callTool("first", timeoutMs = 5_000),
                                client.callTool("second", timeoutMs = 5_000),
                            )
                        }
                    while (!pending.isCompleted) {
                        yield()
                        fixture.pump()
                        yield()
                    }
                    pending.await()
                }

            assertEquals(2, results.size)
            results.forEach { assertFalse(it.isError) }
        }

    @Test
    fun `a stale response id is ignored rather than answering the wrong call`() =
        runTest {
            // The guard the test above depends on. A server pinned to id 0
            // answers the first call and nothing after it -- the request must
            // time out rather than accept an answer to a question we already
            // asked.
            val client = client()
            val fixture = fixture { JsonRpcSuccess(JsonRpcId.Num(0), buildJsonObject {}) }
            fixture.start()

            coroutineScope {
                val first = async { client.callTool("first", timeoutMs = 5_000) }
                yield()
                fixture.pump()
                first.await()
            }

            val second =
                coroutineScope {
                    val pending = async { runCatching { client.callTool("second", timeoutMs = 200) } }
                    repeat(20) {
                        yield()
                        fixture.pump()
                        yield()
                    }
                    pending
                }
            assertTrue(second.await().isFailure, "a response carrying a stale id must not resolve the new call")
        }

    @Test
    fun `a tool call returns the server result`() =
        runTest {
            val fixture =
                fixture {
                    JsonRpcSuccess(JsonRpcId.Num(0), buildJsonObject { put("cursor", JsonPrimitive(7)) })
                }
            fixture.start()

            val result =
                coroutineScope {
                    val pending = async { client().callTool("msg_post", timeoutMs = 5_000) }
                    yield()
                    fixture.pump()
                    pending.await()
                }

            assertFalse(result.isError)
            assertEquals(
                7,
                result.result!!
                    .jsonObject["cursor"]!!
                    .jsonPrimitive.content
                    .toInt(),
            )
        }

    @Test
    fun `a tool call always carries a progressToken`() =
        runTest {
            // Without one a server MUST NOT start either transfer profile, so
            // omitting it would silently cap every response at one relay event.
            val fixture = fixture { JsonRpcSuccess(JsonRpcId.Num(0), buildJsonObject {}) }
            fixture.start()

            coroutineScope {
                val pending = async { client().callTool("x", timeoutMs = 5_000) }
                yield()
                fixture.pump()
                pending.await()
            }

            val meta = fixture.handledParams.first()!![McpParams.META]!!.jsonObject
            assertEquals("call-0", meta[McpParams.PROGRESS_TOKEN]!!.jsonPrimitive.content)
        }

    @Test
    fun `an error response surfaces as an error rather than a throw`() =
        runTest {
            val fixture =
                fixture {
                    JsonRpcFailure(
                        JsonRpcId.Num(0),
                        JsonRpcError(JsonRpcError.PAYMENT_REQUIRED, "Payment Required"),
                    )
                }
            fixture.start()

            val result =
                coroutineScope {
                    val pending = async { client().callTool("priced", timeoutMs = 5_000) }
                    yield()
                    fixture.pump()
                    pending.await()
                }

            assertTrue(result.isError)
            assertEquals(JsonRpcError.PAYMENT_REQUIRED, result.error!!.code)
            assertNull(result.result)
        }

    @Test
    fun `a CEP-22 transfer reassembles into the effective response`() =
        runTest {
            val big = buildJsonObject { put("text", JsonPrimitive("x".repeat(400))) }
            val serialized = JsonRpcCodec.encode(JsonRpcSuccess(JsonRpcId.Num(0), big))

            // NO direct response, and that is the whole point: a chunked
            // response REPLACES the direct one. An earlier version of this test
            // had the fixture also answer normally, which meant the call was
            // ended by that answer and the reassembly only had to win a
            // tie-break. Against the reference coordinator, which sends frames
            // and nothing else, the same code hung until its deadline.
            val fixture = fixture { error("a chunked response is the only response") }
            fixture.start()

            val result =
                coroutineScope {
                    val pending = async { client().callTool("big", timeoutMs = 5_000) }
                    yield()

                    OversizedTransferSender(chunkChars = 64).frame(firstCallToken, serialized).forEach { frame ->
                        fixture.reply(frame.envelope.toNotification(), clientSigner.pubKey, "0".repeat(64))
                    }
                    pending.await()
                }

            assertEquals(
                "x".repeat(400),
                result.result!!
                    .jsonObject["text"]!!
                    .jsonPrimitive.content,
                "the reassembled payload is the response",
            )
        }

    @Test
    fun `a CEP-41 subscription ends on its budget instead of throwing`() =
        runTest {
            // The other half of the rule above, and the one that made every
            // live subscription an exception: if `close` does not complete the
            // request, an open-ended subscription has NO response to wait for,
            // so its budget running out is the only way it can end. Under
            // TimeoutMode.TOTAL that is a normal return, not a failure.
            val fixture = fixture { error("a subscription has no response to give") }
            fixture.start()

            val live = mutableListOf<String>()

            val result =
                coroutineScope {
                    val pending =
                        async {
                            client().callTool("sub", timeoutMs = 5_000, timeoutMode = TimeoutMode.TOTAL) { live += it }
                        }
                    yield()

                    listOf(
                        OpenStreamFrame.start(firstCallToken, 1.0),
                        OpenStreamFrame.chunk(firstCallToken, 2.0, 0, "pushed"),
                        OpenStreamFrame.close(firstCallToken, 3.0, lastChunkIndex = 0),
                    ).forEach { frame ->
                        fixture.reply(frame.envelope.toNotification(), clientSigner.pubKey, "0".repeat(64))
                    }

                    // No pump: nothing answers, and the budget is the exit.
                    pending.await()
                }

            assertEquals(listOf("pushed"), live, "fragments arrive as they are pushed")
            assertEquals(listOf("pushed"), result.streamed)
            assertNull(result.result, "there was no response, and that is not an error")
            assertFalse(result.isError)
        }

    @Test
    fun `a CEP-41 stream delivers fragments but close does not complete the call`() =
        runTest {
            // The rule worth pinning end to end: close says no more frames, and
            // the request is still only finished by its own JSON-RPC response.
            val fixture =
                fixture {
                    JsonRpcSuccess(JsonRpcId.Num(0), buildJsonObject { put("done", JsonPrimitive(true)) })
                }
            fixture.start()

            val live = mutableListOf<String>()

            val result =
                coroutineScope {
                    val pending =
                        async {
                            client().callTool("stream", timeoutMs = 5_000) { live += it }
                        }
                    yield()

                    listOf(
                        OpenStreamFrame.start(firstCallToken, 1.0),
                        OpenStreamFrame.chunk(firstCallToken, 2.0, 0, "Hello"),
                        OpenStreamFrame.chunk(firstCallToken, 3.0, 1, " world"),
                        OpenStreamFrame.close(firstCallToken, 4.0, lastChunkIndex = 1),
                    ).forEach { frame ->
                        fixture.reply(frame.envelope.toNotification(), clientSigner.pubKey, "0".repeat(64))
                    }

                    // Only now does the request's own response arrive.
                    fixture.pump()
                    pending.await()
                }

            assertEquals(listOf("Hello", " world"), live)
            assertEquals(listOf("Hello", " world"), result.streamed)
            assertEquals(
                true,
                result.result!!
                    .jsonObject["done"]!!
                    .jsonPrimitive.content
                    .toBoolean(),
                "the call is completed by its JSON-RPC response, not by close",
            )
        }

    @Test
    fun `initialize completes the handshake and sends initialized`() =
        runTest {
            val fixture =
                fixture {
                    JsonRpcSuccess(
                        JsonRpcId.Num(0),
                        buildJsonObject { put("protocolVersion", JsonPrimitive(CvmMcpClient.PROTOCOL_VERSION)) },
                    )
                }
            fixture.start()

            coroutineScope {
                val pending = async { client().initialize() }
                yield()
                fixture.pump()
                pending.await()
            }

            // The notification rides after the response, unsubscribed, so it
            // shows up on the wire rather than in a correlation slot.
            val methods =
                relays.published.mapNotNull { event ->
                    runCatching { JsonRpcCodec.decode(event.content) }.getOrNull()
                }
            assertTrue(
                methods.any { it is com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcNotification && it.method == McpMethods.INITIALIZED },
                "the client must tell the server it is ready",
            )
        }
}
