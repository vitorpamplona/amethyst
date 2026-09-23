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
package com.vitorpamplona.quartz.contextvm.transport

import com.vitorpamplona.quartz.contextvm.cep04Encryption.CvmEncryptionException
import com.vitorpamplona.quartz.contextvm.cep04Encryption.CvmGiftWrap
import com.vitorpamplona.quartz.contextvm.cep04Encryption.EncryptionMode
import com.vitorpamplona.quartz.contextvm.cep04Encryption.GiftWrapMode
import com.vitorpamplona.quartz.contextvm.core.CvmKinds
import com.vitorpamplona.quartz.contextvm.core.CvmMessageEvent
import com.vitorpamplona.quartz.contextvm.core.CvmTags
import com.vitorpamplona.quartz.contextvm.fixture.CvmFixtureServer
import com.vitorpamplona.quartz.contextvm.fixture.CvmRequest
import com.vitorpamplona.quartz.contextvm.fixture.FixtureFaults
import com.vitorpamplona.quartz.contextvm.fixture.InMemoryRelayPool
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcId
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcMessage
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcRequest
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcSuccess
import com.vitorpamplona.quartz.contextvm.mcp.McpParams
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
import kotlin.test.assertFalse
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
        handler: suspend (CvmRequest) -> JsonRpcMessage = {
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
    fun `CVM-CORE-15b ignores a correctly correlated response from the wrong signer`() =
        runTest {
            // The forgery this closes. A client subscribes to everything
            // `p`-tagged to its own key, so anyone on the relay can gift-wrap
            // a well-formed response to it — the wrap's signature proves only
            // that its throwaway key signed it, and the inner signature is
            // verified without knowing who the signer ought to be.
            //
            // The forged event is published straight to the pool rather than
            // through a second fixture. A fixture subscribes for traffic
            // addressed to itself and would never see a request `p`-tagged to
            // the real server, so it would answer nothing and this test would
            // pass by timing out for the wrong reason. (It did, at first.)
            //
            // The impostor does everything right except be the coordinator:
            // matching JSON-RPC id, addressed to the very key this call is
            // listening on, and no `e` tag — which CVM-CORE-15 establishes is
            // accepted on its own. Identity is the coordinator's pubkey and
            // nothing else (spec/00.md §8.5). Without that check a stranger
            // answers `kp_take` with their own KeyPackage and we invite them.
            val impostor = NostrSignerInternal(KeyPair())
            val forged =
                CvmMessageEvent.create(
                    JsonRpcSuccess(JsonRpcId.Num(0), buildJsonObject { put("ok", JsonPrimitive(true)) }),
                    ephemeralSigner.pubKey,
                    impostor,
                )

            val transport = transport()
            val pending = async { transport.request(JsonRpcRequest(JsonRpcId.Num(0), "ping"), timeoutMs = 300) }
            yield()
            relays.publish(CvmGiftWrap().wrap(forged, ephemeralSigner.pubKey))

            assertFailsWith<TimeoutCancellationException> { pending.await() }
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
                    handler = { request -> JsonRpcSuccess(request.id, buildJsonObject {}) },
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
    fun `CVM-4-22 a peer that declares no encryption gets a refusal, not an unreadable wrap`() =
        runTest {
            // The point of EncryptionMode.REQUIRED. Before negotiation was
            // wired up its input was a constant true, so this could never fire:
            // we would have sent a wrap the peer cannot open and the caller
            // would have seen a timeout with no reason.
            val fixture = server(discoveryTags = listOf(arrayOf("name", "Plaintext only")))
            fixture.start()
            val transport = transport()

            // First request establishes the baseline and still goes out under
            // the optimistic assumption — the peer has not spoken yet.
            exchange(fixture, transport, JsonRpcRequest(JsonRpcId.Num(0), "ping"))
            assertFalse(transport.peer!!.supportsEncryption)

            val thrown =
                runCatching {
                    exchange(fixture, transport, JsonRpcRequest(JsonRpcId.Num(1), "ping"))
                }.exceptionOrNull()

            assertTrue(thrown is CvmEncryptionException, "expected a stated refusal, got $thrown")
        }

    @Test
    fun `CVM-4-23 a peer that declares encryption keeps getting wraps`() =
        runTest {
            val fixture =
                server(
                    discoveryTags = listOf(arrayOf("name", "Encrypts"), CvmTags.flag(CvmTags.SUPPORT_ENCRYPTION)),
                )
            fixture.start()
            val transport = transport()

            exchange(fixture, transport, JsonRpcRequest(JsonRpcId.Num(0), "ping"))
            val second =
                exchange(
                    fixture,
                    transport, // id 0 again: the fixture answers every request with id 0 and the
                    // transport correctly ignores a mismatched id (CVM-CORE-13).
                    JsonRpcRequest(JsonRpcId.Num(0), "ping"),
                )

            assertTrue(second is JsonRpcSuccess)
            assertTrue(relays.published.all { CvmKinds.isGiftWrap(it.kind) })
        }

    @Test
    fun `CVM-4-24 a peer that declares nothing at all is not read as declaring no`() =
        runTest {
            // The case that decides the whole design. A live cordn coordinator's
            // kind-25910 responses carry only the routing tags `p` and `e`
            // (observed on the public relays, 2026-09-23). Reading that silence
            // as a full CEP-35 surface would conclude it supports nothing, and
            // REQUIRED would refuse to talk to every deployed coordinator.
            val fixture = server(discoveryTags = emptyList())
            fixture.start()
            val transport = transport()

            exchange(fixture, transport, JsonRpcRequest(JsonRpcId.Num(0), "ping"))
            val second =
                exchange(
                    fixture,
                    transport, // id 0 again: the fixture answers every request with id 0 and the
                    // transport correctly ignores a mismatched id (CVM-CORE-13).
                    JsonRpcRequest(JsonRpcId.Num(0), "ping"),
                )

            assertTrue(second is JsonRpcSuccess, "a silent peer must stay reachable")
            assertTrue(relays.published.all { CvmKinds.isGiftWrap(it.kind) })
        }

    @Test
    fun `CVM-19-05 the wrap kind follows what the peer declared`() =
        runTest {
            // CEP-19: preferring 21059 must never mean refusing a 1059-only
            // server. A declared surface without the ephemeral flag downgrades.
            // The surface must declare encryption, or OPTIONAL would send the
            // second request unwrapped and the wrap kind would never be picked
            // at all - which made an earlier version of this test vacuous.
            assertEquals(
                listOf(CvmKinds.GIFT_WRAP),
                wrapKindsOfSecondRequest(
                    discoveryTags = listOf(arrayOf(CvmTags.SUPPORT_ENCRYPTION, "true"), arrayOf("name", "Persistent only")),
                ),
            )
        }

    @Test
    fun `CVM-19-06 a peer that declares ephemeral support gets the ephemeral wrap`() =
        runTest {
            assertEquals(
                listOf(CvmKinds.EPHEMERAL_GIFT_WRAP),
                wrapKindsOfSecondRequest(
                    discoveryTags =
                        listOf(
                            arrayOf(CvmTags.SUPPORT_ENCRYPTION, "true"),
                            arrayOf(CvmTags.SUPPORT_ENCRYPTION_EPHEMERAL, "true"),
                        ),
                ),
            )
        }

    /**
     * Runs two requests against a server declaring [discoveryTags] and returns
     * the kinds we published on the second one - by then the peer's surface has
     * been learned from its first reply.
     */
    private suspend fun wrapKindsOfSecondRequest(discoveryTags: List<Array<String>>): List<Int> {
        val fixture = server(discoveryTags = discoveryTags)
        fixture.start()
        val transport = transport(CvmGiftWrap(giftWrapMode = GiftWrapMode.EPHEMERAL, encryptionMode = EncryptionMode.OPTIONAL))

        exchange(fixture, transport, JsonRpcRequest(JsonRpcId.Num(0), "ping"))
        relays.published.clear()
        // id 0 again: the fixture answers every request with id 0 and the
        // transport correctly ignores a mismatched id (CVM-CORE-13).
        exchange(fixture, transport, JsonRpcRequest(JsonRpcId.Num(0), "ping"))

        // Only what WE sent: a wrap addressed to the server. The fixture's own
        // replies are wrapped too and would otherwise be counted.
        val ours = relays.published.filter { e -> e.tags.any { it.size >= 2 && it[0] == "p" && it[1] == serverSigner.pubKey } }
        assertTrue(ours.isNotEmpty(), "the second request should have been published")
        return ours.map { it.kind }.distinct()
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
