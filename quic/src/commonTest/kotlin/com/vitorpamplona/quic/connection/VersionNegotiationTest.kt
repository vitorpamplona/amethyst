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

import com.vitorpamplona.quic.packet.QuicVersion
import com.vitorpamplona.quic.tls.PermissiveCertificateValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Version Negotiation flow per RFC 9000 §6.
 *
 * The runner's `versionnegotiation` testcase has the server reply to the
 * client's first Initial with a VN packet whose supported_versions list
 * contains v1 (and not the version we offered). The client must:
 *
 *   1. Validate the VN packet (DCID echoed = our SCID, list does NOT
 *      include the version we offered).
 *   2. Pick a version it can speak from the list (we only support v1).
 *   3. Generate a fresh DCID, re-derive Initial keys, reset Initial PN
 *      space, re-emit the cached ClientHello, and switch the writer's
 *      stamped version to v1.
 *   4. Latch `vnConsumed` so a second VN is dropped.
 *
 * Failure modes also covered:
 *
 *   - Downgrade defense: VN that lists the version we offered is dropped
 *     (RFC 9000 §6.2 — anti-replay).
 *   - Unsupported list: VN whose supported_versions doesn't include any
 *     version we can speak fails the handshake with
 *     [QuicVersionNegotiationException].
 *   - Second-VN: after one consumed VN, any subsequent VN is dropped
 *     even if otherwise valid.
 */
class VersionNegotiationTest {
    /**
     * Synthesize a VN packet on the wire (RFC 9000 §17.2.1):
     *   first byte : 0x80 | <random low bits, set to 0 here>
     *   version    : 0x00000000
     *   dcid_len + dcid (echoes the client's source CID per §6.1)
     *   scid_len + scid (server picks)
     *   supported_versions: 32-bit big-endian numbers, one per offered version
     */
    private fun encodeVnPacket(
        echoedDcid: ConnectionId,
        serverScid: ConnectionId,
        supportedVersions: List<Int>,
    ): ByteArray {
        val out = ArrayList<Byte>()
        out += 0x80.toByte() // form bit set; remaining bits unused / random
        // version=0
        out += 0x00.toByte()
        out += 0x00.toByte()
        out += 0x00.toByte()
        out += 0x00.toByte()
        out += echoedDcid.length.toByte()
        for (b in echoedDcid.bytes) out += b
        out += serverScid.length.toByte()
        for (b in serverScid.bytes) out += b
        for (v in supportedVersions) {
            out += ((v ushr 24) and 0xFF).toByte()
            out += ((v ushr 16) and 0xFF).toByte()
            out += ((v ushr 8) and 0xFF).toByte()
            out += (v and 0xFF).toByte()
        }
        return out.toByteArray()
    }

    private fun newClient(initialVersion: Int) =
        QuicConnection(
            serverName = "example.test",
            config = QuicConnectionConfig(),
            tlsCertificateValidator = PermissiveCertificateValidator(),
            initialVersion = initialVersion,
        )

    @Test
    fun happy_path_vn_switches_to_v1_and_resets_dcid_pn_keys() {
        val client = newClient(QuicVersion.FORCE_VERSION_NEGOTIATION)
        client.start()
        // The first Initial would be stamped with the forced version.
        assertEquals(QuicVersion.FORCE_VERSION_NEGOTIATION, client.currentVersion)
        val originalDcid = client.destinationConnectionId

        val serverScid = ConnectionId.random(8)
        val vn =
            encodeVnPacket(
                echoedDcid = client.sourceConnectionId,
                serverScid = serverScid,
                supportedVersions = listOf(QuicVersion.V1),
            )

        feedDatagram(client, vn, nowMillis = 0L)

        assertTrue(client.vnConsumed, "valid VN must latch vnConsumed")
        assertEquals(QuicVersion.V1, client.currentVersion, "currentVersion must switch to v1")
        assertNotEquals(
            originalDcid,
            client.destinationConnectionId,
            "DCID must be regenerated (RFC 9000 §6 fresh handshake)",
        )
        // Initial PN space is fresh — next outbound allocate returns 0.
        assertEquals(
            0L,
            client.initial.pnSpace.nextPacketNumber,
            "Initial PN space must reset to 0 after VN",
        )
        // The cached ClientHello must be re-queued so the next drain emits a v1
        // Initial with the same handshake bytes.
        val drained = drainOutbound(client, nowMillis = 1L)
        assertTrue(drained != null && drained.isNotEmpty(), "post-VN drain must emit a fresh Initial")
        // Wire-level check: bytes 1..4 of the long-header packet are the version.
        val versionOnWire =
            ((drained[1].toInt() and 0xFF) shl 24) or
                ((drained[2].toInt() and 0xFF) shl 16) or
                ((drained[3].toInt() and 0xFF) shl 8) or
                (drained[4].toInt() and 0xFF)
        assertEquals(
            QuicVersion.V1,
            versionOnWire,
            "post-VN Initial datagram must carry v1 in the long-header version field",
        )
    }

    @Test
    fun downgrade_defense_vn_listing_offered_version_is_dropped() {
        val client = newClient(QuicVersion.FORCE_VERSION_NEGOTIATION)
        client.start()
        val originalDcid = client.destinationConnectionId
        val originalVersion = client.currentVersion

        val vn =
            encodeVnPacket(
                echoedDcid = client.sourceConnectionId,
                serverScid = ConnectionId.random(8),
                // The list MUST NOT contain the version we offered. If it does,
                // it's a probable replay/spoof — RFC 9000 §6.2 says drop.
                supportedVersions = listOf(QuicVersion.V1, QuicVersion.FORCE_VERSION_NEGOTIATION),
            )

        feedDatagram(client, vn, nowMillis = 0L)

        assertFalse(client.vnConsumed, "anti-replay: VN containing offered version must be dropped")
        assertEquals(originalVersion, client.currentVersion, "currentVersion unchanged")
        assertEquals(originalDcid, client.destinationConnectionId, "DCID unchanged")
    }

    @Test
    fun unsupported_list_fails_handshake() {
        val client = newClient(QuicVersion.FORCE_VERSION_NEGOTIATION)
        client.start()

        // quic-go's force-VN test version. We don't support it, so the
        // handshake must fail.
        val vn =
            encodeVnPacket(
                echoedDcid = client.sourceConnectionId,
                serverScid = ConnectionId.random(8),
                supportedVersions = listOf(0x6b3343cf),
            )

        feedDatagram(client, vn, nowMillis = 0L)

        // Unsupported list ⇒ handshake fails BEFORE the latch is set.
        // vnConsumed therefore stays false; the connection is forced
        // closed via signalHandshakeFailed → markClosedExternally.
        assertFalse(client.vnConsumed, "vnConsumed only latches on successful version pick")
        assertEquals(
            QuicConnection.Status.CLOSED,
            client.status,
            "no mutually-supported version must close the connection",
        )
    }

    @Test
    fun second_vn_is_ignored_after_first() {
        val client = newClient(QuicVersion.FORCE_VERSION_NEGOTIATION)
        client.start()

        // First VN: valid, switches to v1.
        val serverScid1 = ConnectionId.random(8)
        feedDatagram(
            client,
            encodeVnPacket(
                echoedDcid = client.sourceConnectionId,
                serverScid = serverScid1,
                supportedVersions = listOf(QuicVersion.V1),
            ),
            nowMillis = 0L,
        )
        assertTrue(client.vnConsumed)
        assertEquals(QuicVersion.V1, client.currentVersion)
        val dcidAfterFirstVn = client.destinationConnectionId

        // Second VN: even if structurally fine, must be dropped. We craft
        // one whose supported list does NOT include the version we
        // ORIGINALLY offered — so it would otherwise look valid.
        feedDatagram(
            client,
            encodeVnPacket(
                echoedDcid = client.sourceConnectionId,
                serverScid = ConnectionId.random(8),
                supportedVersions = listOf(QuicVersion.V1),
            ),
            nowMillis = 1L,
        )

        assertEquals(
            dcidAfterFirstVn,
            client.destinationConnectionId,
            "second VN must NOT regenerate the DCID",
        )
        assertEquals(QuicVersion.V1, client.currentVersion, "current version stays at v1")
    }

    @Test
    fun vn_with_dcid_mismatch_is_dropped() {
        // Defensive: a VN whose echoed DCID doesn't equal our SCID is
        // probably an off-path attacker's spoof — drop without state change.
        val client = newClient(QuicVersion.FORCE_VERSION_NEGOTIATION)
        client.start()

        val vn =
            encodeVnPacket(
                echoedDcid = ConnectionId.random(8), // wrong
                serverScid = ConnectionId.random(8),
                supportedVersions = listOf(QuicVersion.V1),
            )

        feedDatagram(client, vn, nowMillis = 0L)

        assertFalse(client.vnConsumed, "DCID mismatch ⇒ no state change")
        assertEquals(QuicVersion.FORCE_VERSION_NEGOTIATION, client.currentVersion)
    }

    @Test
    fun default_initial_version_is_v1_for_existing_callers() {
        // Backward-compat: not passing initialVersion must keep the writer
        // emitting v1 (no behavior change for existing tests).
        val client =
            QuicConnection(
                serverName = "example.test",
                config = QuicConnectionConfig(),
                tlsCertificateValidator = PermissiveCertificateValidator(),
            )
        assertEquals(QuicVersion.V1, client.currentVersion)
        assertFalse(client.vnConsumed)
        assertNull(client.peerTransportParameters)
    }
}
