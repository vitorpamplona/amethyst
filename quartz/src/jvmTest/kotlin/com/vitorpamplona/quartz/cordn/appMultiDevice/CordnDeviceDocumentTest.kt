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

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The §4/§5/§6/§7 document layer, on its own.
 *
 * These codecs decide what survives a phone swap, so the cases worth pinning
 * are the ones that fail quietly: a field dropped in the round trip is data the
 * user loses, and an address check that passes when it should not is a blob
 * nobody authorised being handed to the MLS engine.
 */
class CordnDeviceDocumentTest {
    private val group =
        CordnGroupDocument(
            gid = "gid-1",
            coordinator = "aa".repeat(32),
            clientState = "c3RhdGU=",
            cursor = 42,
            issuedAt = 1_700_000_000_000,
            roomState = "cm9vbQ==",
            echoState = "ZWNobw==",
            joinedViaRequest = true,
        )

    @Test
    fun `a group document round-trips every field`() {
        val decoded = CordnDeviceDocument.decode(CordnDeviceDocument.encode(group)) as CordnGroupDocument

        assertEquals(group, decoded)
    }

    @Test
    fun `a meta document round-trips every field`() {
        val meta =
            CordnMetaDocument(
                removed = listOf(CordnTombstone("gone", 7)),
                lastResortKeyPackage = CordnLastResortKeyPackage("a2s=", "cHJpdg=="),
                issuedAt = 99,
            )

        assertEquals(meta, CordnDeviceDocument.decode(CordnDeviceDocument.encode(meta)))
    }

    @Test
    fun `an empty meta document round-trips`() {
        // The normal migration case for an account with no tombstones and no
        // published last-resort key package.
        assertEquals(CordnMetaDocument(), CordnDeviceDocument.decode(CordnDeviceDocument.encode(CordnMetaDocument())))
    }

    @Test
    fun `the draft, the read position and the via-request flag survive`() {
        // CordnBackup's Archive carries none of these three, so a restore there
        // silently returns every room unread with the half-typed message gone.
        // Migration must not repeat that, which is why they are on the document.
        val decoded = CordnDeviceDocument.decode(CordnDeviceDocument.encode(group)) as CordnGroupDocument

        assertEquals("cm9vbQ==", decoded.roomState)
        assertEquals("ZWNobw==", decoded.echoState)
        assertTrue(decoded.joinedViaRequest)
    }

    @Test
    fun `an unknown schema version is rejected`() {
        val bumped = CordnDeviceDocument.encode(group).replace("\"schemaVersion\":1", "\"schemaVersion\":2")

        val thrown = assertThrows(CordnDocumentException::class.java) { CordnDeviceDocument.decode(bumped) }
        assertTrue(thrown.message!!.contains("schemaVersion"))
    }

    @Test
    fun `an unknown document type is rejected`() {
        val retyped = CordnDeviceDocument.encode(group).replace("\"type\":\"group\"", "\"type\":\"ledger\"")

        assertThrows(CordnDocumentException::class.java) { CordnDeviceDocument.decode(retyped) }
    }

    @Test
    fun `a half lastResortKeyPackage is rejected rather than half-loaded`() {
        // Both halves are needed at join time. Keeping the public half alone
        // would resolve an incoming Welcome and then fail to open it.
        val half = """{"schemaVersion":1,"type":"meta","issuedAt":0,"lastResortKeyPackage":{"keyPackage":"a2s="}}"""

        assertThrows(CordnDocumentException::class.java) { CordnDeviceDocument.decode(half) }
    }

    @Test
    fun `unknown fields are ignored, so a newer writer does not break this reader`() {
        val extended = CordnDeviceDocument.encode(group).dropLast(1) + ""","somethingNewer":{"a":1}}"""

        assertEquals(group, CordnDeviceDocument.decode(extended))
    }

    @Test
    fun `a foreign clientState is flagged rather than handed to the engine`() {
        // The spec leaves clientState library-private on purpose, so a ts-mls
        // document is unreadable here. Without the marker that surfaces as a
        // crash inside MLS; with it, a caller can refuse with a reason.
        val theirs = CordnDeviceDocument.encode(group).replace(CordnDeviceDocument.CLIENT_STATE_FORMAT, "ts-mls")

        val decoded = CordnDeviceDocument.decode(theirs) as CordnGroupDocument
        assertFalse(decoded.isReadableHere)
        assertTrue((CordnDeviceDocument.decode(CordnDeviceDocument.encode(group)) as CordnGroupDocument).isReadableHere)
    }

    @Test
    fun `a document that predates the marker is not assumed to be ours`() {
        val unmarked = CordnDeviceDocument.encode(group.copy(clientStateFormat = null))

        val decoded = CordnDeviceDocument.decode(unmarked) as CordnGroupDocument
        assertNull(decoded.clientStateFormat)
        assertFalse(decoded.isReadableHere)
    }

    @Test
    fun `a sealed document round-trips and the address is over the ciphertext`() {
        val dek = KeyPair()

        val blob = CordnDocumentSeal.seal(group, dek)

        assertEquals(group, CordnDocumentSeal.open(blob, dek))
        assertTrue(CordnDocumentSeal.verifyAddress(blob, CordnDocumentSeal.address(blob)))
    }

    @Test
    fun `the same document seals to a different address every time`() {
        // NIP-44 salts randomly, so the address cannot be used for dedup — §5
        // says so, and a caller that assumed otherwise would skip republishing
        // a document whose content had genuinely changed.
        val dek = KeyPair()

        val first = CordnDocumentSeal.seal(group, dek)
        val second = CordnDocumentSeal.seal(group, dek)

        assertNotEquals(CordnDocumentSeal.address(first), CordnDocumentSeal.address(second))
        assertEquals(CordnDocumentSeal.open(first, dek), CordnDocumentSeal.open(second, dek))
    }

    @Test
    fun `another DEK does not open the document`() {
        val blob = CordnDocumentSeal.seal(group, KeyPair())

        assertThrows(CordnDocumentException::class.java) { CordnDocumentSeal.open(blob, KeyPair()) }
    }

    @Test
    fun `a blob that does not match its advertised address is refused`() {
        // The §6 check. It is what stops a content store from substituting a
        // blob the tip never named.
        val dek = KeyPair()
        val blob = CordnDocumentSeal.seal(group, dek)
        val other = CordnDocumentSeal.seal(group.copy(gid = "gid-2"), dek)

        assertFalse(CordnDocumentSeal.verifyAddress(other, CordnDocumentSeal.address(blob)))
    }

    @Test
    fun `the plaintext never contains the state in the clear`() {
        val dek = KeyPair()

        val blob = CordnDocumentSeal.seal(group, dek).decodeToString()

        assertFalse(blob.contains("c3RhdGU="))
        assertFalse(blob.contains("gid-1"))
    }
}
