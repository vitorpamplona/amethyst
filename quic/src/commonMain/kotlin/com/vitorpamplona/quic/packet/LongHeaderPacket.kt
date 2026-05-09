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
        val pnLen =
            com.vitorpamplona.quic.connection.PacketNumberSpaceState.encodeLength(
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
        // RFC 9001 §5.4.2: pad the plaintext so that pnLen + payload >= 4 — the
        // 16-byte AEAD tag then provides the rest of the 20 bytes the HP sample
        // window needs after pnOffset. Trailing 0x00 bytes decode as PADDING
        // frames (RFC 9000 §19.1). Length covers PN bytes + (padded) payload +
        // AEAD tag, so the padded size must feed into lengthValue.
        val paddedPlaintext = padForHeaderProtectionSample(plain.payload, pnLen)
        val lengthValue = pnLen + paddedPlaintext.size + aead.tagLength
        w.writeVarint(lengthValue.toLong())
        val pnOffset = w.size
        // Encode the packet number big-endian, low bytes
        for (i in pnLen - 1 downTo 0) {
            w.writeByte(((plain.packetNumber ushr (i * 8)) and 0xFF).toInt())
        }
        val headerBytes = w.toByteArray()

        // Pre-allocate the final packet buffer in one shot and have the
        // AEAD seal write ciphertext+tag directly into it. Pre-fix this
        // path allocated `headerBytes`, `paddedPlaintext`, the
        // `aead.seal` return ByteArray, and the final concat buffer —
        // four ByteArrays per outbound packet. The single-buffer +
        // [Aead.sealInto] form below collapses the seal output and
        // concat into the same allocation.
        val nonce = aeadNonce(iv, plain.packetNumber)
        val packet = ByteArray(headerBytes.size + paddedPlaintext.size + aead.tagLength)
        headerBytes.copyInto(packet, 0)
        aead.sealInto(
            key = key,
            nonce = nonce,
            aad = packet,
            aadOffset = 0,
            aadLength = headerBytes.size,
            plaintext = paddedPlaintext,
            plaintextOffset = 0,
            plaintextLength = paddedPlaintext.size,
            output = packet,
            outputOffset = headerBytes.size,
        )

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
        if ((first and 0x80) == 0) return null // not a long header — silently drop
        val typeBits = (first ushr 4) and 0x03
        val type = LongHeaderType.fromTypeBits(typeBits)
        val version = r.readUint32().toInt()
        val dcidLen = r.readByte()
        if (dcidLen !in 0..20) return null // RFC 9000 §17.2 caps CID at 20 bytes
        val dcidBytes = r.readBytes(dcidLen)
        val scidLen = r.readByte()
        if (scidLen !in 0..20) return null
        val scidBytes = r.readBytes(scidLen)
        val token =
            if (type == LongHeaderType.INITIAL) {
                val tokenLenRaw = r.readVarint()
                if (tokenLenRaw < 0L || tokenLenRaw > r.remaining.toLong()) return null
                r.readBytes(tokenLenRaw.toInt())
            } else {
                ByteArray(0)
            }
        val lengthRaw = r.readVarint()
        if (lengthRaw < 0L || lengthRaw > r.remaining.toLong()) return null
        val length = lengthRaw.toInt()
        val pnOffset = r.position
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

        // Step 1: unmask the first byte so we can read pnLen. The
        // long-header form bit (0x80) was already validated above (we
        // wouldn't be in this function otherwise), so the first-byte
        // mask is fixed at 0x0F — pre-fix the conditional `if (form == 1)`
        // path was dead code.
        packet[0] = (first xor (mask[0].toInt() and 0x0F)).toByte()
        val unmaskedFirst = packet[0].toInt() and 0xFF
        // RFC 9000 §17.2: long header layout is `1|1|T|T|R|R|P|P` —
        // the two reserved bits at 0x0C MUST be zero after HP unmasking.
        // A peer that sets either bit is in protocol violation. The
        // form-bit (0x80) and fixed-bit (0x40) are not header-protected,
        // so they're already correct; we skip the AEAD here on
        // reserved-bit set rather than letting a malformed-but-AEAD-OK
        // packet drift our state.
        if ((unmaskedFirst and 0x0C) != 0) {
            throw com.vitorpamplona.quic.QuicProtocolViolationException(
                "PROTOCOL_VIOLATION: long-header reserved bits set " +
                    "(0x${unmaskedFirst.toString(16)})",
            )
        }
        val pnLen = (unmaskedFirst and 0x03) + 1

        // Step 2: unmask exactly `pnLen` packet-number bytes.
        for (i in 0 until pnLen) {
            packet[localPnOffset + i] = (packet[localPnOffset + i].toInt() xor mask[1 + i].toInt()).toByte()
        }

        // Now parse the unprotected packet number (big-endian).
        var truncatedPn = 0L
        for (i in 0 until pnLen) {
            truncatedPn = (truncatedPn shl 8) or (packet[localPnOffset + i].toInt() and 0xFF).toLong()
        }
        val fullPn =
            com.vitorpamplona.quic.connection.PacketNumberSpaceState.decodePacketNumber(
                largestReceived = largestReceivedInSpace,
                truncatedPn = truncatedPn,
                pnLen = pnLen,
            )

        val aadEnd = localPnOffset + pnLen
        val nonce = aeadNonce(iv, fullPn)
        // Range-based open avoids two ByteArray slice allocations per
        // inbound packet — see [ShortHeaderPacket.parseAndDecrypt] for
        // rationale.
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
            if (dcidLen !in 0..20) return null
            val dcid = r.readBytes(dcidLen)
            val scidLen = r.readByte()
            if (scidLen !in 0..20) return null
            val scid = r.readBytes(scidLen)
            // Retry packets have NO token-length, NO length, NO packet-number fields —
            // they consist of (header)(retry_token)(16-byte integrity tag). The caller
            // routes them via [RetryPacket.parse] separately. We surface them here only
            // so the caller knows the total length is the rest of the input buffer.
            if (type == LongHeaderType.RETRY) {
                return PeekedHeader(
                    type = type,
                    version = version,
                    dcid = ConnectionId(dcid),
                    scid = ConnectionId(scid),
                    totalLength = bytes.size - offset,
                )
            }
            if (type == LongHeaderType.INITIAL) {
                val tokenLenRaw = r.readVarint()
                if (tokenLenRaw < 0L || tokenLenRaw > r.remaining.toLong()) return null
                r.skip(tokenLenRaw.toInt())
            }
            val lengthRaw = r.readVarint()
            if (lengthRaw < 0L || lengthRaw > r.remaining.toLong()) return null
            val total = r.position - offset + lengthRaw.toInt()
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
