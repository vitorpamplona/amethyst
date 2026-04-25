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

import com.vitorpamplona.quic.QuicCodecException
import com.vitorpamplona.quic.QuicReader
import com.vitorpamplona.quic.QuicWriter
import com.vitorpamplona.quic.Varint
import com.vitorpamplona.quic.connection.ConnectionId
import com.vitorpamplona.quic.crypto.Aead
import com.vitorpamplona.quic.crypto.HeaderProtection
import com.vitorpamplona.quic.crypto.aeadNonce
import com.vitorpamplona.quic.crypto.applyHeaderProtectionMask

/**
 * Long-header packet per RFC 9000 §17.2. Used for Initial, 0-RTT, Handshake,
 * Retry. (Retry has its own structure — we implement build/parse for the
 * other three.)
 *
 * Wire layout:
 *
 *   first_byte          (1 byte)  — header form, type, packet number length
 *   version             (4 bytes) — 0x00000001 for QUIC v1
 *   dcid                (1 + dcid_len bytes)
 *   scid                (1 + scid_len bytes)
 *   token               (varint length + bytes; only Initial)
 *   length              (varint) — covers PN + payload + AEAD tag
 *   packet_number       (1..4 bytes, length encoded in first_byte low bits)
 *   payload             (encrypted)
 */
data class LongHeaderPlaintextPacket(
    val type: LongHeaderType,
    val version: Int = QuicVersion.V1,
    val dcid: ConnectionId,
    val scid: ConnectionId,
    val token: ByteArray = ByteArray(0),
    val packetNumber: Long,
    val payload: ByteArray,
)

object LongHeaderPacket {
    /**
     * Encode + protect a long-header plaintext packet:
     *   1. Build the unprotected header.
     *   2. Compute the encrypted payload via AEAD with packet_number as nonce.
     *   3. Apply header protection over the first byte + packet number.
     *
     * Returns the on-the-wire bytes.
     */
    fun build(
        plain: LongHeaderPlaintextPacket,
        aead: Aead,
        key: ByteArray,
        iv: ByteArray,
        hp: HeaderProtection,
        hpKey: ByteArray,
        largestAckedInSpace: Long,
    ): ByteArray {
        val pnLen = com.vitorpamplona.quic.connection.PacketNumberSpaceState.encodeLength(
            plain.packetNumber,
            largestAckedInSpace,
        )
        require(pnLen in 1..4)

        // Build the unprotected header
        val w = QuicWriter()
        val firstByteOffset = w.size
        val firstByte = 0xC0 or (plain.type.code shl 4) or (pnLen - 1)
        w.writeByte(firstByte)
        w.writeUint32(plain.version)
        w.writeByte(plain.dcid.length)
        w.writeBytes(plain.dcid.bytes)
        w.writeByte(plain.scid.length)
        w.writeBytes(plain.scid.bytes)
        if (plain.type == LongHeaderType.INITIAL) {
            w.writeVarint(plain.token.size.toLong())
            w.writeBytes(plain.token)
        }
        // Length covers PN bytes + payload + AEAD tag.
        val lengthValue = pnLen + plain.payload.size + aead.tagLength
        w.writeVarint(lengthValue.toLong())
        val pnOffset = w.size
        // Encode the packet number big-endian, low bytes
        for (i in pnLen - 1 downTo 0) {
            w.writeByte(((plain.packetNumber ushr (i * 8)) and 0xFF).toInt())
        }
        val headerBytes = w.toByteArray()

        // Encrypt payload
        val nonce = aeadNonce(iv, plain.packetNumber)
        val ciphertext = aead.seal(key, nonce, headerBytes, plain.payload)

        // Concatenate header + ciphertext
        val packet = ByteArray(headerBytes.size + ciphertext.size)
        headerBytes.copyInto(packet, 0)
        ciphertext.copyInto(packet, headerBytes.size)

        // Apply header protection. Sample is 16 bytes starting 4 bytes after pnOffset.
        val sampleStart = pnOffset + 4
        require(sampleStart + 16 <= packet.size) { "packet too short for HP sample" }
        val sample = packet.copyOfRange(sampleStart, sampleStart + 16)
        val mask = hp.mask(hpKey, sample)
        applyHeaderProtectionMask(packet, firstByteOffset, pnOffset, pnLen, mask)

        return packet
    }

    /**
     * Strip header protection + decrypt a long-header packet, returning the
     * parsed packet and the number of bytes consumed (so the caller can
     * advance through coalesced datagrams).
     *
     * Returns null if the packet failed authentication — caller should drop
     * silently per RFC 9001 §5.5.
     */
    fun parseAndDecrypt(
        bytes: ByteArray,
        offset: Int,
        aead: Aead,
        key: ByteArray,
        iv: ByteArray,
        hp: HeaderProtection,
        hpKey: ByteArray,
        largestReceivedInSpace: Long,
    ): ParseResult? {
        val packetStart = offset
        val r = QuicReader(bytes, offset)
        val first = r.readByte()
        require((first and 0x80) != 0) { "not a long-header packet" }
        val typeBits = (first ushr 4) and 0x03
        val type = LongHeaderType.fromTypeBits(typeBits)
        val version = r.readUint32().toInt()
        val dcidLen = r.readByte()
        val dcidBytes = r.readBytes(dcidLen)
        val scidLen = r.readByte()
        val scidBytes = r.readBytes(scidLen)
        val token =
            if (type == LongHeaderType.INITIAL) {
                val tokenLen = r.readVarint().toInt()
                r.readBytes(tokenLen)
            } else {
                ByteArray(0)
            }
        val length = r.readVarint().toInt()
        val pnOffset = r.position
        if (pnOffset + length > offset + bytes.size - packetStart + bytes.size /* room check */) {
            // length sanity
        }
        if (pnOffset + length > bytes.size) return null
        // Sample for HP starts at pnOffset + 4.
        val sampleStart = pnOffset + 4
        if (sampleStart + 16 > bytes.size) return null
        val sample = bytes.copyOfRange(sampleStart, sampleStart + 16)
        val mask = hp.mask(hpKey, sample)

        // Make a private copy of the packet so we can mutate the header in place.
        val packetEnd = pnOffset + length
        val packet = bytes.copyOfRange(packetStart, packetEnd)
        val localPnOffset = pnOffset - packetStart

        // Step 1: unmask the first byte so we can read pnLen.
        val firstByteMask = if ((first and 0x80) != 0) 0x0F else 0x1F
        packet[0] = (first xor (mask[0].toInt() and firstByteMask)).toByte()
        val pnLen = ((packet[0].toInt() and 0xFF) and 0x03) + 1

        // Step 2: unmask exactly `pnLen` packet-number bytes.
        for (i in 0 until pnLen) {
            packet[localPnOffset + i] = (packet[localPnOffset + i].toInt() xor mask[1 + i].toInt()).toByte()
        }

        // Now parse the unprotected packet number (big-endian).
        var truncatedPn = 0L
        for (i in 0 until pnLen) {
            truncatedPn = (truncatedPn shl 8) or (packet[localPnOffset + i].toInt() and 0xFF).toLong()
        }
        val fullPn = com.vitorpamplona.quic.connection.PacketNumberSpaceState.decodePacketNumber(
            largestReceived = largestReceivedInSpace,
            truncatedPn = truncatedPn,
            pnLen = pnLen,
        )

        val aadEnd = localPnOffset + pnLen
        val aad = packet.copyOfRange(0, aadEnd)
        val ciphertext = packet.copyOfRange(aadEnd, packet.size)
        val nonce = aeadNonce(iv, fullPn)
        val plaintext = aead.open(key, nonce, aad, ciphertext) ?: return null

        return ParseResult(
            packet =
                LongHeaderPlaintextPacket(
                    type = type,
                    version = version,
                    dcid = ConnectionId(dcidBytes),
                    scid = ConnectionId(scidBytes),
                    token = token,
                    packetNumber = fullPn,
                    payload = plaintext,
                ),
            consumed = packetEnd - packetStart,
        )
    }

    /**
     * Peek the destination CID, source CID, and total length of a long-header
     * packet without decrypting. Useful for routing inbound coalesced
     * datagrams to the right key set.
     */
    fun peekHeader(
        bytes: ByteArray,
        offset: Int = 0,
    ): PeekedHeader? {
        try {
            val r = QuicReader(bytes, offset)
            val first = r.readByte()
            if ((first and 0x80) == 0) return null
            val typeBits = (first ushr 4) and 0x03
            val type = LongHeaderType.fromTypeBits(typeBits)
            val version = r.readUint32().toInt()
            val dcidLen = r.readByte()
            val dcid = r.readBytes(dcidLen)
            val scidLen = r.readByte()
            val scid = r.readBytes(scidLen)
            val tokenLen =
                if (type == LongHeaderType.INITIAL) {
                    r.readVarint().toInt()
                } else {
                    0
                }
            if (type == LongHeaderType.INITIAL) r.skip(tokenLen)
            val length = r.readVarint().toInt()
            val total = r.position - offset + length
            return PeekedHeader(type, version, ConnectionId(dcid), ConnectionId(scid), total)
        } catch (_: QuicCodecException) {
            return null
        }
    }

    data class PeekedHeader(
        val type: LongHeaderType,
        val version: Int,
        val dcid: ConnectionId,
        val scid: ConnectionId,
        val totalLength: Int,
    )

    data class ParseResult(
        val packet: LongHeaderPlaintextPacket,
        val consumed: Int,
    )
}
