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
package com.vitorpamplona.quartz.nipBCOnchainZaps.taproot

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * BIP-341 key-path-only taproot derivation tests.
 *
 * Vectors come from the BIP-341 wallet-test-vectors (`keyPathSpending` cases
 * with `scriptTree=null`).
 */
class TaprootAddressTest {
    // ============================================================
    // BIP-341 key-path-only (no script tree) vector
    // ============================================================

    private val internalKey = "d6889cb081036e0faefa3a35157ad71086b123b2b144b649798b494c300a961d"
    private val expectedTweak = "b86e7be8f39bab32a6f2c0443abbc210f0edac0e2c53d501b36b64437d9c6c70"
    private val expectedOutputKey = "53a1f6e454df1aa2776a2814a721372d6258050de330b3c6d10ee8f4e0dda343"
    private val expectedScriptPubKey =
        "512053a1f6e454df1aa2776a2814a721372d6258050de330b3c6d10ee8f4e0dda343"

    @Test
    fun computesTaggedHash() {
        val tweak = TaprootAddress.tapTweakHash(internalKey.hexToByteArray())
        assertEquals(expectedTweak, tweak.toHexKey())
    }

    @Test
    fun tweaksToOutputKey() {
        val outputKey = TaprootAddress.tweakOutputKey(internalKey.hexToByteArray())
        assertEquals(expectedOutputKey, outputKey.toHexKey())
    }

    @Test
    fun computesScriptPubKey() {
        val script = TaprootAddress.scriptPubKeyForRecipient(internalKey)
        assertEquals(expectedScriptPubKey, script.toHexKey())
    }

    // BIP-341 wallet-test-vectors `scriptPubKey` entry 1 (scriptTree = null):
    // internalPubkey d6889cb0… → bip350Address.
    private val expectedAddress = "bc1p2wsldez5mud2yam29q22wgfh9439spgduvct83k3pm50fcxa5dps59h4z5"

    @Test
    fun derivesExactBip350Address() {
        // Full key-path-only derivation pinned to the BIP-341 wallet test vector.
        assertEquals(expectedAddress, TaprootAddress.fromPubKey(internalKey))
        assertEquals(expectedAddress, TaprootAddress.fromPubKey(internalKey.hexToByteArray()))
    }

    @Test
    fun derivedAddressRoundTripsToOutputKey() {
        val address = TaprootAddress.fromPubKey(internalKey)
        val decoded = SegwitAddress.decode(address)
        assertEquals(1, decoded.witnessVersion)
        assertEquals(expectedOutputKey, decoded.program.toHexKey())
    }

    // ============================================================
    // Sanity: address prefix + length
    // ============================================================

    @Test
    fun derivedAddressHasTaprootPrefix() {
        // Use a different x-only key; output should still be a bc1p taproot address.
        val pubKey = "8b34731183a85a4fc1a3ea9e8caa14e72ff31716bf6dd0d2f8c93f5b14e44f5d"
        val address = TaprootAddress.fromPubKey(pubKey)
        assertTrue(address.startsWith("bc1p"), "expected bc1p..., got $address")
        assertEquals(62, address.length, "P2TR address must be 62 chars")
    }
}
