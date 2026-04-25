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
package com.vitorpamplona.quic.connection

import com.vitorpamplona.quic.crypto.Aes128Gcm
import com.vitorpamplona.quic.crypto.AesEcbHeaderProtection
import com.vitorpamplona.quic.crypto.InitialSecrets
import com.vitorpamplona.quic.crypto.PlatformAesOneBlock
import com.vitorpamplona.quic.frame.AckFrame
import com.vitorpamplona.quic.frame.ConnectionCloseFrame
import com.vitorpamplona.quic.frame.CryptoFrame
import com.vitorpamplona.quic.frame.DatagramFrame
import com.vitorpamplona.quic.frame.HandshakeDoneFrame
import com.vitorpamplona.quic.frame.MaxDataFrame
import com.vitorpamplona.quic.frame.MaxStreamDataFrame
import com.vitorpamplona.quic.frame.PingFrame
import com.vitorpamplona.quic.frame.StreamFrame
import com.vitorpamplona.quic.frame.decodeFrames
import com.vitorpamplona.quic.frame.encodeFrames
import com.vitorpamplona.quic.packet.LongHeaderPacket
import com.vitorpamplona.quic.packet.LongHeaderPlaintextPacket
import com.vitorpamplona.quic.packet.LongHeaderType
import com.vitorpamplona.quic.packet.QuicVersion
import com.vitorpamplona.quic.packet.ShortHeaderPacket
import com.vitorpamplona.quic.tls.InProcessTlsServer
import com.vitorpamplona.quic.tls.TlsConstants

/**
 * In-memory client ↔ server QUIC connection bridge, following Cloudflare
 * quiche's `Pipe` pattern (`quiche/src/test_utils.rs`). The "server" here
 * is a minimal harness that wraps [InProcessTlsServer] in QUIC packet
 * protection and frame routing — it's enough to exercise the client's
 * full receive path without writing a complete server-side
 * [QuicConnection].
 *
 * Use [drive] to run the handshake until both sides have application
 * keys, then send + receive arbitrary frames via [clientToServer] and
 * [serverToClient].
 */
class InMemoryQuicPipe(
    val client: QuicConnection,
    val initialDcid: ByteArray,
) {
    private val tlsServer = InProcessTlsServer()
    private val initial = InitialSecrets.derive(initialDcid)
    private val hp = AesEcbHeaderProtection(PlatformAesOneBlock)

    // Server's per-direction packet protection at each level.
    private var serverHandshakeRx: PacketProtection? = null
    private var serverHandshakeTx: PacketProtection? = null
    private var serverApplicationRx: PacketProtection? = null
    private var serverApplicationTx: PacketProtection? = null

    private val serverScid = ConnectionId.random(8)
    private val initialPnSpace = PacketNumberSpaceState()
    private val handshakePnSpace = PacketNumberSpaceState()
    private val applicationPnSpace = PacketNumberSpaceState()

    /**
     * Run the handshake to completion. Returns when both sides have
     * 1-RTT keys installed (the client status is CONNECTED).
     */
    fun drive(maxRounds: Int = 10) {
        repeat(maxRounds) {
            // Client → server.
            val outClient = drainOutbound(client, nowMillis = 0L) ?: return@repeat
            // The client may emit Initial+Handshake coalesced; demux them.
            processClientDatagram(outClient)
            if (client.status == QuicConnection.Status.CONNECTED) return
            // Server → client.
            val outServer = drainServer() ?: return@repeat
            feedDatagram(client, outServer, nowMillis = 0L)
            if (client.status == QuicConnection.Status.CONNECTED) return
        }
    }

    /**
     * Inject a single datagram from the client to the server, processing all
     * coalesced packets.
     */
    private fun processClientDatagram(datagram: ByteArray) {
        var offset = 0
        while (offset < datagram.size) {
            val first = datagram[offset].toInt() and 0xFF
            if ((first and 0x80) == 0) {
                // Short header — application level. Decrypt and route to the
                // server-side TLS server only if it carries CRYPTO frames
                // (post-handshake messages we ignore).
                val proto = serverApplicationRx ?: return
                val parsed =
                    ShortHeaderPacket.parseAndDecrypt(
                        bytes = datagram,
                        offset = offset,
                        dcidLen = serverScid.length,
                        aead = proto.aead,
                        key = proto.key,
                        iv = proto.iv,
                        hp = proto.hp,
                        hpKey = proto.hpKey,
                        largestReceivedInSpace = applicationPnSpace.largestReceived,
                    ) ?: return
                applicationPnSpace.observeInbound(parsed.packet.packetNumber, 0L)
                processServerInbound(parsed.packet.payload)
                return
            }
            val peeked = LongHeaderPacket.peekHeader(datagram, offset) ?: return
            val proto =
                when (peeked.type) {
                    LongHeaderType.INITIAL -> PacketProtection(Aes128Gcm, initial.clientKey, initial.clientIv, hp, initial.clientHp)
                    LongHeaderType.HANDSHAKE -> serverHandshakeRx ?: return
                    else -> return
                }
            val space =
                when (peeked.type) {
                    LongHeaderType.INITIAL -> initialPnSpace
                    LongHeaderType.HANDSHAKE -> handshakePnSpace
                    else -> return
                }
            val parsed =
                LongHeaderPacket.parseAndDecrypt(
                    bytes = datagram,
                    offset = offset,
                    aead = proto.aead,
                    key = proto.key,
                    iv = proto.iv,
                    hp = proto.hp,
                    hpKey = proto.hpKey,
                    largestReceivedInSpace = space.largestReceived,
                ) ?: return
            space.observeInbound(parsed.packet.packetNumber, 0L)
            processServerInbound(parsed.packet.payload)
            offset += parsed.consumed
        }
    }

    private fun processServerInbound(payload: ByteArray) {
        val frames = decodeFrames(payload)
        val cryptoBytes = ArrayList<ByteArray>()
        for (frame in frames) {
            when (frame) {
                is CryptoFrame -> cryptoBytes += frame.data

                is AckFrame, is PingFrame, is StreamFrame, is DatagramFrame,
                is MaxDataFrame, is MaxStreamDataFrame, is HandshakeDoneFrame,
                is ConnectionCloseFrame,
                -> Unit

                else -> Unit
            }
        }
        if (cryptoBytes.isEmpty()) return
        val joined =
            ByteArray(cryptoBytes.sumOf { it.size }).also { dst ->
                var p = 0
                for (b in cryptoBytes) {
                    b.copyInto(dst, p)
                    p += b.size
                }
            }
        // Heuristic: route to the right TLS-server entry by inspecting the
        // first byte of the joined CRYPTO bytes (TLS handshake type).
        if (joined.isEmpty()) return
        when (joined[0].toInt() and 0xFF) {
            TlsConstants.HS_CLIENT_HELLO -> {
                tlsServer.receiveClientHello(joined)
                installServerSecretsAfterHandshakeBegin()
            }

            TlsConstants.HS_FINISHED -> {
                tlsServer.receiveClientFinished(joined)
            }
        }
    }

    private fun installServerSecretsAfterHandshakeBegin() {
        // Build the server's handshake / app protection.
        val cipher = tlsServer.negotiatedCipherSuite
        check(cipher == TlsConstants.CIPHER_TLS_AES_128_GCM_SHA256) {
            "InMemoryQuicPipe currently only supports AES-128-GCM"
        }
        serverHandshakeRx = packetProtectionFromSecret(cipher, tlsServer.clientHandshakeSecret!!)
        serverHandshakeTx = packetProtectionFromSecret(cipher, tlsServer.serverHandshakeSecret!!)
        serverApplicationRx = packetProtectionFromSecret(cipher, tlsServer.clientApplicationSecret!!)
        serverApplicationTx = packetProtectionFromSecret(cipher, tlsServer.serverApplicationSecret!!)
    }

    /** Build a single datagram from the server containing whatever it owes the client. */
    private fun drainServer(): ByteArray? {
        val parts = mutableListOf<ByteArray>()
        // Initial-level: ServerHello.
        val initialSh = tlsServer.pollOutboundInitial()
        if (initialSh != null) {
            parts += buildServerInitialPacket(initialSh)
        }
        // Handshake-level: EE then server Finished.
        while (true) {
            val hs = tlsServer.pollOutboundHandshake() ?: break
            val proto = serverHandshakeTx ?: continue
            val pn = handshakePnSpace.allocateOutbound()
            val payload = encodeFrames(listOf(CryptoFrame(handshakeCryptoOffset.also { handshakeCryptoOffset += hs.size.toLong() }, hs)))
            parts +=
                LongHeaderPacket.build(
                    LongHeaderPlaintextPacket(
                        type = LongHeaderType.HANDSHAKE,
                        version = QuicVersion.V1,
                        dcid = client.sourceConnectionId,
                        scid = serverScid,
                        packetNumber = pn,
                        payload = payload,
                    ),
                    proto.aead,
                    proto.key,
                    proto.iv,
                    proto.hp,
                    proto.hpKey,
                    largestAckedInSpace = -1L,
                )
        }
        if (parts.isEmpty()) return null
        var total = 0
        for (p in parts) total += p.size
        val out = ByteArray(total)
        var pos = 0
        for (p in parts) {
            p.copyInto(out, pos)
            pos += p.size
        }
        return out
    }

    private var initialCryptoOffset: Long = 0L
    private var handshakeCryptoOffset: Long = 0L

    private fun buildServerInitialPacket(crypto: ByteArray): ByteArray {
        val proto =
            PacketProtection(
                aead = Aes128Gcm,
                key = initial.serverKey,
                iv = initial.serverIv,
                hp = hp,
                hpKey = initial.serverHp,
            )
        val pn = initialPnSpace.allocateOutbound()
        val frames =
            mutableListOf<com.vitorpamplona.quic.frame.Frame>(
                CryptoFrame(initialCryptoOffset, crypto),
            )
        initialCryptoOffset += crypto.size.toLong()
        // Also ACK the client's Initial PN 0 if we've seen it.
        if (initialPnSpace.largestReceived >= 0L) {
            frames.add(
                0,
                AckFrame(
                    largestAcknowledged = initialPnSpace.largestReceived,
                    ackDelay = 0L,
                    firstAckRange = 0L,
                ),
            )
        }
        val payload = encodeFrames(frames)
        return LongHeaderPacket.build(
            LongHeaderPlaintextPacket(
                type = LongHeaderType.INITIAL,
                version = QuicVersion.V1,
                dcid = client.sourceConnectionId,
                scid = serverScid,
                packetNumber = pn,
                payload = payload,
            ),
            proto.aead,
            proto.key,
            proto.iv,
            proto.hp,
            proto.hpKey,
            largestAckedInSpace = -1L,
        )
    }
}
