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
package com.vitorpamplona.quartz.nip05.namecoin.wallet

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NmcKeyManagerTest {
    @Test
    fun `WIF encode and decode roundtrip`() {
        val privKey = ByteArray(32) { (it + 1).toByte() }
        val wif = NmcKeyManager.privateKeyToWif(privKey)
        assertTrue("WIF should start with T or similar", wif.isNotEmpty())
        val decoded = NmcKeyManager.wifToPrivateKey(wif)
        assertNotNull(decoded)
        assertArrayEquals(privKey, decoded)
    }

    @Test
    fun `address generation produces N prefix`() {
        val privKey = ByteArray(32) { (it + 1).toByte() }
        val address = NmcKeyManager.addressFromPrivKey(privKey)
        assertTrue("NMC address should start with N or M", address[0] == 'N' || address[0] == 'M')
    }

    @Test
    fun `address validation`() {
        val privKey = ByteArray(32) { (it + 1).toByte() }
        val address = NmcKeyManager.addressFromPrivKey(privKey)
        assertTrue(NmcKeyManager.isValidAddress(address))
        assertFalse(NmcKeyManager.isValidAddress("invalid"))
        assertFalse(NmcKeyManager.isValidAddress("1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2")) // Bitcoin addr
    }

    @Test
    fun `hash160 produces 20 bytes`() {
        val data = "test".toByteArray()
        val hash = NmcKeyManager.hash160(data)
        assertEquals(20, hash.size)
    }

    @Test
    fun `addressToHash160 roundtrip`() {
        val privKey = ByteArray(32) { (it + 1).toByte() }
        val pubKey = NmcKeyManager.compressedPubKey(privKey)
        val hash160 = NmcKeyManager.hash160(pubKey)
        val address = NmcKeyManager.addressFromPubKey(pubKey)
        val extracted = NmcKeyManager.addressToHash160(address)
        assertNotNull(extracted)
        assertArrayEquals(hash160, extracted)
    }

    @Test
    fun `base58Check encode and decode roundtrip`() {
        val payload = byteArrayOf(52) + ByteArray(20) { it.toByte() }
        val encoded = NmcKeyManager.base58CheckEncode(payload)
        val decoded = NmcKeyManager.base58CheckDecode(encoded)
        assertNotNull(decoded)
        assertArrayEquals(payload, decoded)
    }

    @Test
    fun `base58Check rejects corrupted data`() {
        val payload = byteArrayOf(52) + ByteArray(20) { it.toByte() }
        val encoded = NmcKeyManager.base58CheckEncode(payload)
        // Corrupt last character
        val corrupted = encoded.dropLast(1) + if (encoded.last() == 'a') 'b' else 'a'
        assertNull(NmcKeyManager.base58CheckDecode(corrupted))
    }

    @Test
    fun `doubleSha256 produces 32 bytes`() {
        val result = NmcKeyManager.doubleSha256("test".toByteArray())
        assertEquals(32, result.size)
    }
}

class NmcNameScriptsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `valid name formats`() {
        assertTrue(NmcNameScripts.isValidName("d/example"))
        assertTrue(NmcNameScripts.isValidName("d/my-domain"))
        assertTrue(NmcNameScripts.isValidName("id/alice"))
        assertTrue(NmcNameScripts.isValidName("id/bob123"))
        assertFalse(NmcNameScripts.isValidName("d/"))
        assertFalse(NmcNameScripts.isValidName("d/UPPER"))
        assertFalse(NmcNameScripts.isValidName("bad/name"))
        assertFalse(NmcNameScripts.isValidName("nope"))
    }

    @Test
    fun `prepareNameNew generates unique salts`() {
        val d1 = NmcNameScripts.prepareNameNew("d/test")
        val d2 = NmcNameScripts.prepareNameNew("d/test")
        assertEquals(20, d1.salt.size)
        assertEquals(20, d1.commitment.size)
        assertFalse(d1.salt.contentEquals(d2.salt))
    }

    @Test
    fun `buildNameNewScript starts with OP_NAME_NEW`() {
        val commitment = ByteArray(20)
        val hash160 = ByteArray(20)
        val script = NmcNameScripts.buildNameNewScript(commitment, hash160)
        assertEquals(0x51, script[0].toInt() and 0xff) // OP_NAME_NEW
    }

    @Test
    fun `buildNameUpdateScript starts with OP_NAME_UPDATE`() {
        val hash160 = ByteArray(20)
        val script = NmcNameScripts.buildNameUpdateScript("d/test", "{}", hash160)
        assertEquals(0x53, script[0].toInt() and 0xff) // OP_NAME_UPDATE
    }

    @Test
    fun `buildDomainValue produces NIP-05 structure`() {
        val pubkey = "a".repeat(64)
        val value = NmcNameScripts.buildDomainValue(pubkey, listOf("wss://r.example.com"))
        val parsed = json.parseToJsonElement(value).jsonObject
        val nostr = parsed["nostr"]?.jsonObject
        assertNotNull(nostr)
        val names = nostr!!["names"]?.jsonObject
        assertNotNull(names)
        assertEquals(pubkey, names!!["_"].toString().trim('"'))
    }

    @Test
    fun `buildIdentityValue produces pubkey structure`() {
        val pubkey = "b".repeat(64)
        val value = NmcNameScripts.buildIdentityValue(pubkey, displayName = "Bob")
        val parsed = json.parseToJsonElement(value).jsonObject
        val nostr = parsed["nostr"]?.jsonObject
        assertNotNull(nostr)
        assertEquals(pubkey, nostr!!["pubkey"].toString().trim('"'))
        assertEquals("Bob", parsed["name"].toString().trim('"'))
    }

    @Test
    fun `updateNostrInValue preserves existing fields`() {
        val existing = """{"email":"a@b.com","bitcoin":"1X"}"""
        val updated = NmcNameScripts.updateNostrInValue(existing, "c".repeat(64))
        val parsed = json.parseToJsonElement(updated).jsonObject
        assertEquals("a@b.com", parsed["email"].toString().trim('"'))
        assertNotNull(parsed["nostr"])
    }

    @Test
    fun `value size check`() {
        assertTrue(NmcNameScripts.isValueWithinLimit("{}"))
        assertTrue(NmcNameScripts.isValueWithinLimit("x".repeat(520)))
        assertFalse(NmcNameScripts.isValueWithinLimit("x".repeat(521)))
    }
}

class NmcTransactionBuilderTest {
    @Test
    fun `buildP2PKHScript has correct structure`() {
        val hash160 = ByteArray(20) { it.toByte() }
        val script = NmcTransactionBuilder.buildP2PKHScript(hash160)
        assertEquals(0x76, script[0].toInt() and 0xff) // OP_DUP
        assertEquals(0xa9, script[1].toInt() and 0xff) // OP_HASH160
        assertEquals(0x14, script[2].toInt() and 0xff) // push 20 bytes
        assertEquals(0x88, script[23].toInt() and 0xff) // OP_EQUALVERIFY
        assertEquals(0xac, script[24].toInt() and 0xff) // OP_CHECKSIG
        assertEquals(25, script.size)
    }

    @Test
    fun `pushData encodes small data correctly`() {
        val data = byteArrayOf(1, 2, 3)
        val pushed = NmcTransactionBuilder.pushData(data)
        assertEquals(4, pushed.size)
        assertEquals(3, pushed[0].toInt())
    }

    @Test
    fun `estimateTxSize produces reasonable sizes`() {
        val size1in1out = NmcTransactionBuilder.estimateTxSize(1, 1)
        assertTrue(size1in1out in 150..250)
        val size2in2out = NmcTransactionBuilder.estimateTxSize(2, 2)
        assertTrue(size2in2out > size1in1out)
    }
}

class NmcWalletTypesTest {
    @Test
    fun `NmcBalance calculations`() {
        val b = NmcBalance(100_000_000, 50_000_000)
        assertEquals(1.0, b.confirmedNmc, 0.001)
        assertEquals(1.5, b.totalNmc, 0.001)
        assertFalse(b.isEmpty)
    }

    @Test
    fun `OwnedName properties`() {
        val n = OwnedName("d/example", "{}", "abc", 100, 14400)
        assertEquals("example.bit", n.displayDomain)
        assertEquals(100, n.daysRemaining)
        assertFalse(n.isExpiringSoon)
    }

    @Test
    fun `PendingNameRegistration readiness`() {
        val p = PendingNameRegistration("d/test", "aa", "txid", 100, "{}", 0)
        assertFalse(p.isReadyForFirstUpdate(111)) // 11 blocks
        assertTrue(p.isReadyForFirstUpdate(112)) // 12 blocks
        assertTrue(p.isReadyForFirstUpdate(200)) // 100 blocks
    }
}
