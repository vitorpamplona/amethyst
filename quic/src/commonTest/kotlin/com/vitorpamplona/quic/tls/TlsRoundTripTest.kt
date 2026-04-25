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
package com.vitorpamplona.quic.tls

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TlsRoundTripTest {
    /**
     * End-to-end TLS 1.3 handshake driven entirely on Quartz primitives.
     *
     * Asserts that:
     *   - both sides reach handshake-complete
     *   - the negotiated handshake & application traffic secrets match
     *     bit-for-bit on both sides
     *   - the client decodes the server's ALPN + transport parameters
     */
    @Test
    fun handshake_completes_and_secrets_match() {
        val capturedSecrets = CapturedSecrets()
        val tps = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        val server =
            InProcessTlsServer(
                transportParameters = tps,
            )
        val client =
            TlsClient(
                serverName = "example.test",
                transportParameters = ByteArray(0),
                secretsListener = capturedSecrets,
            )
        client.start()

        // 1) Drain ClientHello → server
        val ch = client.pollOutbound(TlsClient.Level.INITIAL)
        assertNotNull(ch, "client should produce ClientHello at Initial level")
        server.receiveClientHello(ch)

        // 2) Drain ServerHello (Initial level) → client
        val sh = server.pollOutboundInitial()
        assertNotNull(sh, "server should produce ServerHello at Initial level")
        client.pushHandshakeBytes(TlsClient.Level.INITIAL, sh)

        // 3) Drain EncryptedExtensions + Finished (Handshake level) → client
        val ee = server.pollOutboundHandshake()
        assertNotNull(ee, "server should produce EncryptedExtensions")
        client.pushHandshakeBytes(TlsClient.Level.HANDSHAKE, ee)

        val sf = server.pollOutboundHandshake()
        assertNotNull(sf, "server should produce server Finished")
        client.pushHandshakeBytes(TlsClient.Level.HANDSHAKE, sf)

        // 4) Drain client Finished → server
        val cf = client.pollOutbound(TlsClient.Level.HANDSHAKE)
        assertNotNull(cf, "client should produce Finished")
        server.receiveClientFinished(cf)

        // 5) Both sides should agree on traffic secrets
        assertContentEquals(server.clientHandshakeSecret, capturedSecrets.handshakeClient, "client handshake secret matches")
        assertContentEquals(server.serverHandshakeSecret, capturedSecrets.handshakeServer, "server handshake secret matches")
        assertContentEquals(server.clientApplicationSecret, capturedSecrets.applicationClient, "client app secret matches")
        assertContentEquals(server.serverApplicationSecret, capturedSecrets.applicationServer, "server app secret matches")

        assertTrue(capturedSecrets.handshakeComplete, "handshake-complete callback fired")
        assertEquals(TlsClient.State.SENT_CLIENT_FINISHED, client.state)

        // 6) Client should have surfaced ALPN and peer transport parameters
        assertContentEquals(TlsConstants.ALPN_H3, client.negotiatedAlpn)
        assertContentEquals(tps, client.peerTransportParameters)
    }

    private class CapturedSecrets : TlsSecretsListener {
        var handshakeClient: ByteArray? = null
        var handshakeServer: ByteArray? = null
        var applicationClient: ByteArray? = null
        var applicationServer: ByteArray? = null
        var handshakeComplete = false

        override fun onHandshakeKeysReady(
            cipherSuite: Int,
            clientSecret: ByteArray,
            serverSecret: ByteArray,
        ) {
            handshakeClient = clientSecret
            handshakeServer = serverSecret
        }

        override fun onApplicationKeysReady(
            cipherSuite: Int,
            clientSecret: ByteArray,
            serverSecret: ByteArray,
        ) {
            applicationClient = clientSecret
            applicationServer = serverSecret
        }

        override fun onHandshakeComplete() {
            handshakeComplete = true
        }
    }
}
