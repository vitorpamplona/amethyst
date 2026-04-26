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

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * RFC 9001 Appendix A.4 — Retry packet recognition + integrity-tag verification.
 *
 *   Original DCID = 8394c8f03e515708 (the client's first-Initial DCID)
 *   Retry packet  = ff000000010008f067a5502a4262b5746f6b656e
 *                   04a265ba2eff4d829058fb3f0f2496ba    (16-byte integrity tag)
 *   Retry token   = "token" (5 bytes: 746f6b656e)
 */
class Rfc9001RetryInteropTest {
    @Test
    fun rfc9001_a4_retry_parses() {
        val packet = rfc9001A4Retry.hexToByteArray()
        val retry = RetryPacket.parse(packet)
        assertNotNull(retry, "must recognize §A.4 packet as Retry")
        assertEquals(0x00000001, retry.version)
        assertEquals(0, retry.dcid.length, "Retry DCID is empty (echoes client's empty SCID)")
        assertEquals("f067a5502a4262b5", retry.scid.toHex())
        assertContentEquals("token".encodeToByteArray(), retry.retryToken)
        assertEquals("04a265ba2eff4d829058fb3f0f2496ba", retry.retryIntegrityTag.toHexLocal())
    }

    private fun ByteArray.toHexLocal(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    @Test
    fun rfc9001_a4_integrity_tag_verifies_against_original_dcid() {
        val packet = rfc9001A4Retry.hexToByteArray()
        val retry = RetryPacket.parse(packet)!!
        val originalDcid = "8394c8f03e515708".hexToByteArray()
        assertTrue(retry.verifyIntegrityTag(packet, originalDcid), "RFC §A.4 integrity tag must verify")
    }

    @Test
    fun rfc9001_a4_integrity_tag_rejects_wrong_original_dcid() {
        val packet = rfc9001A4Retry.hexToByteArray()
        val retry = RetryPacket.parse(packet)!!
        val wrongDcid = "0000000000000000".hexToByteArray()
        assertFalse(retry.verifyIntegrityTag(packet, wrongDcid), "tampered DCID must invalidate the integrity tag")
    }

    @Test
    fun retry_is_not_misparsed_as_initial() {
        // A Retry packet's high bits look like a long header but the type
        // field is RETRY (0x03), not INITIAL (0x00). Calling LongHeaderPacket
        // codepaths on a Retry would mis-parse — confirm the type bits.
        val packet = rfc9001A4Retry.hexToByteArray()
        val first = packet[0].toInt() and 0xFF
        val typeBits = (first ushr 4) and 0x03
        assertEquals(LongHeaderType.RETRY.code, typeBits)
    }

    /** RFC 9001 §A.4 Retry packet — 36 bytes. */
    private val rfc9001A4Retry: String =
        "ff000000010008f067a5502a4262b5746f6b656e04a265ba2eff4d829058fb3f0f2496ba"
}
