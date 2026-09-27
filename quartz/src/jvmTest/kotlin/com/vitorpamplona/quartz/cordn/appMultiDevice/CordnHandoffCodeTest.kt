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
package com.vitorpamplona.quartz.cordn.appMultiDevice

import com.vitorpamplona.quartz.nip19Bech32.bech32.Bech32
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scannable code that points a new phone at the old phone's tip.
 *
 * This is the one artefact a user physically handles, so the cases worth
 * pinning are about what a photographed or mis-scanned code can do: it must
 * carry no owner key material, it must not silently lose a relay to
 * truncation, and by default it must not grant the ability to move the tip.
 */
class CordnHandoffCodeTest {
    private fun tlv(
        type: Byte,
        value: ByteArray,
    ) = byteArrayOf(type, value.size.toByte()) + value

    private fun bech32(payload: ByteArray) = Bech32.encodeBytes(CordnHandoffCode.HRP, payload, Bech32.Encoding.Bech32)

    private val code =
        CordnHandoffCode(
            ephemeralPubKey = "aa".repeat(32),
            dTag = "opaque-d",
            relays = listOf("wss://one.example", "wss://two.example"),
        )

    @Test
    fun `a code round-trips`() {
        assertEquals(code, CordnHandoffCode.decode(code.encode()))
    }

    @Test
    fun `a code is a cordndev string`() {
        assertTrue(code.encode().startsWith("cordndev1"))
    }

    @Test
    fun `relay order survives`() {
        assertEquals(listOf("wss://one.example", "wss://two.example"), CordnHandoffCode.decode(code.encode()).relays)
    }

    @Test
    fun `a code grants no write by default`() {
        // A photographed QR should be worth nothing. Without the write key the
        // code names an event anyone could already fetch and only the owner can
        // decrypt, so a leak reveals nothing and cannot move the tip.
        assertFalse(code.grantsWrite)
        assertNull(CordnHandoffCode.decode(code.encode()).writeKey)
    }

    @Test
    fun `a write-granting code round-trips and says so`() {
        val writable = code.copy(writeKey = "bb".repeat(32))

        val decoded = CordnHandoffCode.decode(writable.encode())
        assertTrue(decoded.grantsWrite)
        assertEquals("bb".repeat(32), decoded.writeKey)
    }

    @Test
    fun `readOnly strips the write key and keeps everything else`() {
        val writable = code.copy(writeKey = "bb".repeat(32))

        assertEquals(code, writable.readOnly())
        assertFalse(writable.readOnly().grantsWrite)
    }

    @Test
    fun `readOnly on an already read-only code is the same code`() {
        assertEquals(code, code.readOnly())
    }

    @Test
    fun `the kind travels, so changing it later cannot strand old codes`() {
        val odd = code.copy(kind = 31078)

        assertEquals(31078, CordnHandoffCode.decode(odd.encode()).kind)
    }

    @Test
    fun `a code with no relays is refused at construction`() {
        assertThrows(IllegalArgumentException::class.java) { code.copy(relays = emptyList()) }
    }

    @Test
    fun `a truncated trailing relay is refused, not silently dropped`() {
        // The failure this strictness exists to prevent. The payload is hand
        // built so the truncation lands on an OPTIONAL tuple: chopping the
        // encoder's own output instead removes the pubkey, which both a strict
        // and a lenient parser reject, so it discriminates nothing. (The first
        // two versions of this test made exactly that mistake and passed
        // against a deliberately lenient parser.)
        val relay = "wss://two.example".encodeToByteArray()
        val full =
            tlv(CordnHandoffCode.TLV_PUBKEY, ByteArray(32) { 0xAA.toByte() }) +
                tlv(CordnHandoffCode.TLV_D, "opaque-d".encodeToByteArray()) +
                tlv(CordnHandoffCode.TLV_RELAY, "wss://one.example".encodeToByteArray()) +
                tlv(CordnHandoffCode.TLV_RELAY, relay)

        // Sanity: intact, it decodes with both relays.
        assertEquals(2, CordnHandoffCode.decode(bech32(full)).relays.size)

        val truncated = full.copyOfRange(0, full.size - 4)

        assertNull(CordnHandoffCode.decodeOrNull(bech32(truncated)))
    }

    @Test
    fun `a relay whose declared length overruns the payload is refused`() {
        val full =
            tlv(CordnHandoffCode.TLV_PUBKEY, ByteArray(32) { 0xAA.toByte() }) +
                tlv(CordnHandoffCode.TLV_D, "opaque-d".encodeToByteArray()) +
                tlv(CordnHandoffCode.TLV_RELAY, "wss://one.example".encodeToByteArray())
        // Inflate the relay tuple's declared length past the end of the buffer.
        val lying = full.copyOf().also { it[full.size - "wss://one.example".length - 1] = 0xFF.toByte() }

        assertNull(CordnHandoffCode.decodeOrNull(bech32(lying)))
    }

    @Test
    fun `a mixed-case code is refused`() {
        val mixed = code.encode().replaceFirst("cordndev1", "CordnDev1")

        assertThrows(IllegalArgumentException::class.java) { CordnHandoffCode.decode(mixed) }
    }

    @Test
    fun `an uppercase code is accepted, because bech32 allows it`() {
        assertEquals(code, CordnHandoffCode.decode(code.encode().uppercase()))
    }

    @Test
    fun `a cordn group ref is not a handoff code`() {
        assertNull(CordnHandoffCode.decodeOrNull("cordn1qqqqqq"))
    }

    @Test
    fun `a bad pubkey length is refused at construction`() {
        assertThrows(IllegalArgumentException::class.java) { code.copy(ephemeralPubKey = "aa") }
    }

    @Test
    fun `an empty d tag is refused`() {
        assertThrows(IllegalArgumentException::class.java) { code.copy(dTag = "") }
    }

    @Test
    fun `the code carries nothing that could be an owner key`() {
        // §4.3: the documents do not provision identity, and neither does this.
        // The user signs in on the new phone by their usual means first.
        val writable = code.copy(writeKey = "bb".repeat(32))

        val fields = listOf(writable.ephemeralPubKey, writable.dTag, writable.writeKey!!) + writable.relays
        assertEquals(setOf("aa".repeat(32), "opaque-d", "bb".repeat(32), "wss://one.example", "wss://two.example"), fields.toSet())
    }
}
