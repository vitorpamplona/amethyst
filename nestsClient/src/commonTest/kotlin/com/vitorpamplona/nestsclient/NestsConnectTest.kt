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
package com.vitorpamplona.nestsclient

import com.vitorpamplona.nestsclient.moq.ClientSetup
import com.vitorpamplona.nestsclient.moq.MoqCodec
import com.vitorpamplona.nestsclient.moq.MoqVersion
import com.vitorpamplona.nestsclient.moq.ServerSetup
import com.vitorpamplona.nestsclient.transport.FakeWebTransport
import com.vitorpamplona.nestsclient.transport.WebTransportException
import com.vitorpamplona.nestsclient.transport.WebTransportFactory
import com.vitorpamplona.nestsclient.transport.WebTransportSession
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class NestsConnectTest {
    @Test
    fun connect_walks_resolveRoom_then_transport_then_moq_handshake() =
        runTest {
            val (clientSide, serverSide) = FakeWebTransport.pair()
            val httpClient =
                FakeNestsClient(
                    NestsRoomInfo(
                        endpoint = "https://relay.example.com/moq",
                        token = "tok-abc",
                    ),
                )
            val transport = ConstantWebTransportFactory(clientSide)

            // Server-side raw peer answers SETUP.
            val server =
                async {
                    val control = serverSide.peerOpenedBidiStreams().first()
                    val cs = MoqCodec.decode(control.incoming().first())!!.message as ClientSetup
                    control.write(MoqCodec.encode(ServerSetup(cs.supportedVersions.first())))
                }

            val listener =
                connectNestsListener(
                    httpClient = httpClient,
                    transport = transport,
                    scope = this,
                    serviceBase = "https://relay.example.com/api/v1/nests",
                    roomId = "abc",
                    signer = NostrSignerInternal(KeyPair()),
                )
            server.await()

            val connected = assertIs<NestsListenerState.Connected>(listener.state.value)
            assertEquals("https://relay.example.com/moq", connected.roomInfo.endpoint)
            assertEquals(MoqVersion.DRAFT_17, connected.negotiatedMoqVersion)

            assertEquals("relay.example.com", transport.lastConnectedAuthority)
            assertEquals("/moq", transport.lastConnectedPath)
            assertEquals("tok-abc", transport.lastBearer)

            listener.close()
            assertIs<NestsListenerState.Closed>(listener.state.value)
        }

    @Test
    fun resolveRoom_failure_short_circuits_to_Failed() =
        runTest {
            val httpClient =
                ThrowingNestsClient(
                    NestsException("server returned 500", status = 500),
                )
            val transport = NeverConnectFactory()

            val listener =
                connectNestsListener(
                    httpClient = httpClient,
                    transport = transport,
                    scope = this,
                    serviceBase = "https://relay.example.com/api/v1/nests",
                    roomId = "abc",
                    signer = NostrSignerInternal(KeyPair()),
                )

            val failed = assertIs<NestsListenerState.Failed>(listener.state.value)
            assertTrue("Room resolution failed" in failed.reason)
            assertTrue("500" in failed.reason || (failed.cause as? NestsException)?.status == 500)
            assertEquals(0, transport.connectCallCount, "transport must not be reached")
        }

    @Test
    fun transport_handshake_failure_short_circuits_to_Failed() =
        runTest {
            val httpClient =
                FakeNestsClient(NestsRoomInfo(endpoint = "https://relay.example.com/moq"))
            val transport =
                ThrowingTransportFactory(
                    WebTransportException(
                        WebTransportException.Kind.HandshakeFailed,
                        "QUIC handshake refused",
                    ),
                )

            val listener =
                connectNestsListener(
                    httpClient = httpClient,
                    transport = transport,
                    scope = this,
                    serviceBase = "https://relay.example.com/api/v1/nests",
                    roomId = "abc",
                    signer = NostrSignerInternal(KeyPair()),
                )

            val failed = assertIs<NestsListenerState.Failed>(listener.state.value)
            assertTrue("HandshakeFailed" in failed.reason, "got: ${failed.reason}")
        }

    @Test
    fun malformed_endpoint_url_short_circuits_to_Failed() =
        runTest {
            val httpClient =
                FakeNestsClient(NestsRoomInfo(endpoint = "not-a-url"))

            val listener =
                connectNestsListener(
                    httpClient = httpClient,
                    transport = NeverConnectFactory(),
                    scope = this,
                    serviceBase = "https://relay.example.com/api/v1/nests",
                    roomId = "abc",
                    signer = NostrSignerInternal(KeyPair()),
                )

            val failed = assertIs<NestsListenerState.Failed>(listener.state.value)
            assertTrue("Malformed MoQ endpoint" in failed.reason, "got: ${failed.reason}")
        }

    @Test
    fun parseEndpoint_drops_default_port_keeps_explicit_port_and_path() {
        assertEquals(
            "relay.example.com" to "/moq",
            parseEndpoint("https://relay.example.com/moq"),
        )
        assertEquals(
            "relay.example.com:4443" to "/moq",
            parseEndpoint("https://relay.example.com:4443/moq"),
        )
        assertEquals(
            "relay.example.com" to "/",
            parseEndpoint("https://relay.example.com"),
        )
        assertEquals(
            "relay.example.com" to "/api/v1/moq?room=abc",
            parseEndpoint("https://relay.example.com/api/v1/moq?room=abc"),
        )
    }

    // ---------------------------------------------------------- fakes

    private class FakeNestsClient(
        private val info: NestsRoomInfo,
    ) : NestsClient {
        override suspend fun resolveRoom(
            serviceBase: String,
            roomId: String,
            signer: NostrSigner,
        ): NestsRoomInfo = info
    }

    private class ThrowingNestsClient(
        private val toThrow: NestsException,
    ) : NestsClient {
        override suspend fun resolveRoom(
            serviceBase: String,
            roomId: String,
            signer: NostrSigner,
        ): NestsRoomInfo = throw toThrow
    }

    private class ConstantWebTransportFactory(
        private val session: WebTransportSession,
    ) : WebTransportFactory {
        var lastConnectedAuthority: String? = null
            private set
        var lastConnectedPath: String? = null
            private set
        var lastBearer: String? = null
            private set

        override suspend fun connect(
            authority: String,
            path: String,
            bearerToken: String?,
        ): WebTransportSession {
            lastConnectedAuthority = authority
            lastConnectedPath = path
            lastBearer = bearerToken
            return session
        }
    }

    private class ThrowingTransportFactory(
        private val toThrow: WebTransportException,
    ) : WebTransportFactory {
        override suspend fun connect(
            authority: String,
            path: String,
            bearerToken: String?,
        ): WebTransportSession = throw toThrow
    }

    private class NeverConnectFactory : WebTransportFactory {
        var connectCallCount = 0
            private set

        override suspend fun connect(
            authority: String,
            path: String,
            bearerToken: String?,
        ): WebTransportSession {
            connectCallCount++
            fail("transport.connect must not be called")
        }
    }
}
