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
package com.vitorpamplona.quic.packet

import com.vitorpamplona.quic.QuicWriter
import com.vitorpamplona.quic.connection.ConnectionId
import com.vitorpamplona.quic.connection.PacketNumberSpaceState
import com.vitorpamplona.quic.crypto.Aead
import com.vitorpamplona.quic.crypto.HeaderProtection
import com.vitorpamplona.quic.crypto.aeadNonce
import com.vitorpamplona.quic.crypto.applyHeaderProtectionMask

/**
 * Short-header (1-RTT) QUIC packet per RFC 9000 §17.3.
 *
 * Wire layout:
 *   first_byte    (1 byte)  — bits: 0|1|S|R|R|K|PP (S=spin, K=key-phase, PP=pn length-1)
 *   dest_cid      (variable, length is implicit from connection state)
 *   packet_number (1..4 bytes)
 *   payload       (encrypted)
 */
data class ShortHeaderPlaintextPacket(
    val dcid: ConnectionId,
    val packetNumber: Long,
    val payload: ByteArray,
    val keyPhase: Boolean = false,
)

object ShortHeaderPacket {
    fun build(
        plain: ShortHeaderPlaintextPacket,
        aead: Aead,
        key: ByteArray,
        iv: ByteArray,
        hp: HeaderProtection,
        hpKey: ByteArray,
        largestAckedInSpace: Long,
    ): ByteArray {
        val pnLen = PacketNumberSpaceState.encodeLength(plain.packetNumber, largestAckedInSpace)
        require(pnLen in 1..4)

        val w = QuicWriter()
        val firstByteOffset = w.size
        var firstByte = 0x40 or (pnLen - 1) // 01..0..PP
        if (plain.keyPhase) firstByte = firstByte or 0x04
        w.writeByte(firstByte)
        w.writeBytes(plain.dcid.bytes)
        val pnOffset = w.size
        for (i in pnLen - 1 downTo 0) {
            w.writeByte(((plain.packetNumber ushr (i * 8)) and 0xFF).toInt())
        }
        val headerBytes = w.toByteArray()

        // RFC 9001 §5.4.2: pad the plaintext so that pnLen + payload >= 4. The
        // 16-byte AEAD tag then guarantees the encrypted output has the 20
        // bytes following pnOffset that header-protection sampling needs.
        // Trailing 0x00 bytes decode as PADDING frames (RFC 9000 §19.1).
        val paddedPlaintext = padForHeaderProtectionSample(plain.payload, pnLen)

        val nonce = aeadNonce(iv, plain.packetNumber)
        val ciphertext = aead.seal(key, nonce, headerBytes, paddedPlaintext)

        val packet = ByteArray(headerBytes.size + ciphertext.size)
        headerBytes.copyInto(packet, 0)
        ciphertext.copyInto(packet, headerBytes.size)

        val sampleStart = pnOffset + 4
        require(sampleStart + 16 <= packet.size) { "packet too short for HP sample" }
        val sample = packet.copyOfRange(sampleStart, sampleStart + 16)
        val mask = hp.mask(hpKey, sample)
        applyHeaderProtectionMask(packet, firstByteOffset, pnOffset, pnLen, mask)
        return packet
    }

    /**
     * Header-protection unmask only — peek the first byte's key-phase bit
     * before committing to a particular AEAD key set. Returns null if the
     * datagram is too short for a HP sample.
     *
     * RFC 9001 §6 (key update): the receiver MUST decide which key-phase
     * keys to use BEFORE running AEAD. The key-phase bit is part of the
     * header-protected first byte, so the only honest way to choose the
     * right keys is to unmask the first byte first. This helper does that
     * cheaply (one HP-block call), letting the caller pick current vs
     * next-phase keys before paying for the AEAD.
     *
     * The result also reports the unmasked PN length so the caller can
     * stop early on an obviously bogus header rather than allocating
     * buffers for the doomed AEAD attempt.
     */
    fun peekKeyPhase(
        bytes: ByteArray,
        offset: Int,
        dcidLen: Int,
        hp: HeaderProtection,
        hpKey: ByteArray,
    ): Peek? {
        if (offset >= bytes.size) return null
        val first = bytes[offset].toInt() and 0xFF
        if ((first and 0x80) != 0) return null
        val pnOffset = offset + 1 + dcidLen
        val sampleStart = pnOffset + 4
        if (sampleStart + 16 > bytes.size) return null
        val sample = bytes.copyOfRange(sampleStart, sampleStart + 16)
        val mask = hp.mask(hpKey, sample)
        val unprotectedFirst = first xor (mask[0].toInt() and 0x1F)
        return Peek(
            keyPhase = (unprotectedFirst and 0x04) != 0,
            pnLen = (unprotectedFirst and 0x03) + 1,
        )
    }

    data class Peek(
        val keyPhase: Boolean,
        val pnLen: Int,
    )

    /** Strip HP + decrypt a short-header packet. The DCID length must be known from connection state. */
    fun parseAndDecrypt(
        bytes: ByteArray,
        offset: Int,
        dcidLen: Int,
        aead: Aead,
        key: ByteArray,
        iv: ByteArray,
        hp: HeaderProtection,
        hpKey: ByteArray,
        largestReceivedInSpace: Long,
    ): ParseResult? {
        if (offset >= bytes.size) return null
        val first = bytes[offset].toInt() and 0xFF
        if ((first and 0x80) != 0) return null
        val pnOffset = offset + 1 + dcidLen
        val sampleStart = pnOffset + 4
        if (sampleStart + 16 > bytes.size) return null
        val sample = bytes.copyOfRange(sampleStart, sampleStart + 16)
        val mask = hp.mask(hpKey, sample)
        val packetEnd = bytes.size
        val packet = bytes.copyOfRange(offset, packetEnd)
        val localPnOffset = pnOffset - offset
        val firstByteMask = 0x1F
        packet[0] = (first xor (mask[0].toInt() and firstByteMask)).toByte()
        val unmaskedFirst = packet[0].toInt() and 0xFF
        // RFC 9000 §17.3.1: short header layout is `0|1|S|R|R|K|P|P` —
        // the two reserved bits at 0x18 MUST be zero after HP unmasking.
        // A peer that sets either bit is in protocol violation. Pre-fix
        // we silently accepted the packet and let the AEAD pass; an
        // off-spec server (or a fuzzer) could then push us to interop
        // bugs that don't reproduce against well-behaved peers.
        if ((unmaskedFirst and 0x18) != 0) {
            throw com.vitorpamplona.quic.QuicProtocolViolationException(
                "PROTOCOL_VIOLATION: short-header reserved bits set " +
                    "(0x${unmaskedFirst.toString(16)})",
            )
        }
        val pnLen = (unmaskedFirst and 0x03) + 1
        for (i in 0 until pnLen) {
            packet[localPnOffset + i] = (packet[localPnOffset + i].toInt() xor mask[1 + i].toInt()).toByte()
        }

        var truncatedPn = 0L
        for (i in 0 until pnLen) {
            truncatedPn = (truncatedPn shl 8) or (packet[localPnOffset + i].toInt() and 0xFF).toLong()
        }
        val fullPn = PacketNumberSpaceState.decodePacketNumber(largestReceivedInSpace, truncatedPn, pnLen)
        val aadEnd = localPnOffset + pnLen
        val nonce = aeadNonce(iv, fullPn)
        // Range-based open: aad = packet[0..aadEnd), ciphertext = packet[aadEnd..size).
        // Saves the two ByteArray slice allocations that the
        // whole-array form (`aad = copyOfRange(0, aadEnd)` etc.)
        // would do — ~2 KB per inbound packet on the hot path.
        val plaintext =
            aead.openRange(
                key = key,
                nonce = nonce,
                aad = packet,
                aadOffset = 0,
                aadLength = aadEnd,
                ciphertext = packet,
                ciphertextOffset = aadEnd,
                ciphertextLength = packet.size - aadEnd,
            ) ?: return null
        return ParseResult(
            packet =
                ShortHeaderPlaintextPacket(
                    dcid =
                        com.vitorpamplona.quic.connection
                            .ConnectionId(bytes.copyOfRange(offset + 1, offset + 1 + dcidLen)),
                    packetNumber = fullPn,
                    payload = plaintext,
                    keyPhase = (packet[0].toInt() and 0x04) != 0,
                ),
            consumed = packetEnd - offset,
        )
    }

    data class ParseResult(
        val packet: ShortHeaderPlaintextPacket,
        val consumed: Int,
    )
}

/**
 * Return [payload] padded with trailing zero bytes so that
 * `pnLen + paddedSize >= 4`. After AEAD seal adds the 16-byte tag, the
 * resulting ciphertext satisfies the RFC 9001 §5.4.2 requirement that 20
 * bytes follow the start of the packet number for the HP sample.
 */
internal fun padForHeaderProtectionSample(
    payload: ByteArray,
    pnLen: Int,
): ByteArray {
    val minSize = (4 - pnLen).coerceAtLeast(0)
    if (payload.size >= minSize) return payload
    val padded = ByteArray(minSize)
    payload.copyInto(padded, 0)
    return padded
}
