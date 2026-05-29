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
package com.vitorpamplona.quartz.nip60Cashu.p2pk

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class P2PKTest {
    private val pubHex = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798"

    @Test
    fun lockedSecretShape() {
        val secret = P2PK.lockedSecret(pubHex)
        val parsed = Json.parseToJsonElement(secret) as JsonArray
        assertEquals("P2PK", (parsed[0] as JsonPrimitive).content)
        val body = parsed[1] as JsonObject
        assertEquals(pubHex, (body["data"] as JsonPrimitive).content)
        assertTrue((body["nonce"] as JsonPrimitive).content.length == 32, "nonce should be 16 bytes hex")
    }

    @Test
    fun roundTripSecretParse() {
        val secret = P2PK.lockedSecret(pubHex)
        val parsed = P2PK.parseSecret(secret)
        assertEquals(pubHex, parsed?.pubKeyHex)
        assertTrue(parsed?.nonceHex?.length == 32)
    }

    @Test
    fun parseRejectsNonP2pk() {
        assertNull(P2PK.parseSecret("not json"))
        assertNull(P2PK.parseSecret("[\"HTLC\", {}]"))
        assertNull(P2PK.parseSecret("[\"P2PK\"]"))
    }

    @Test
    fun witnessSignatureVerifies() {
        // Use a known private key and recover its pubkey for BIP-340 verify.
        val priv = "1".padStart(64, '0')
        val privBytes = priv.hexToByteArray()
        val compressedPub = Secp256k1Instance.compressedPubKeyFor(privBytes)
        // x-only pubkey = bytes 1..33 of compressed
        val xOnlyHex = compressedPub.copyOfRange(1, 33).toHexKey()

        val secret = P2PK.lockedSecret(xOnlyHex)
        val witnessJson = P2PK.signWitness(secret, priv)

        // Extract the signature from the JSON.
        val obj = Json.parseToJsonElement(witnessJson) as JsonObject
        val sigs = obj["signatures"] as JsonArray
        val sigHex = (sigs[0] as JsonPrimitive).content

        val ok =
            Secp256k1Instance.verifySchnorr(
                signature = sigHex.hexToByteArray(),
                hash = sha256(secret.encodeToByteArray()),
                pubKey = xOnlyHex.hexToByteArray(),
            )
        assertTrue(ok, "Witness signature must verify under the locked pubkey")
    }

    @Test
    fun rejectsWrongPubkeyLength() {
        try {
            P2PK.lockedSecret("0102")
            error("should have thrown")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun acceptsCompressedPubkey() {
        // 33-byte compressed pubkey form
        val compressed = "02$pubHex"
        val secret = P2PK.lockedSecret(compressed)
        val parsed = P2PK.parseSecret(secret)
        assertEquals(compressed, parsed?.pubKeyHex)
    }
}
