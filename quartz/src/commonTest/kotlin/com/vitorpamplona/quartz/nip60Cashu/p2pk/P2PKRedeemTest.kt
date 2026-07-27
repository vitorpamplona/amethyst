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
import com.vitorpamplona.quartz.nip60Cashu.token.CashuProof
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [signP2pkWitnesses] — the redeem-side counterpart to spending a pasted
 * cashu token that may (or may not) be NUT-11 P2PK-locked.
 *
 * Reproduces the interop scenario reported against Bey Wallet, which
 * P2PK-locks ecash to the recipient's Nostr identity pubkey: before the fix
 * the redeem path sent the locked proofs to /v1/swap with no witness and the
 * mint rejected them with `witness is missing for p2pk signature`. Here we
 * assert the witness is produced and verifies under the lock pubkey.
 */
class P2PKRedeemTest {
    // Deterministic key (== 1) — same construction as P2PKTest.
    private val priv = "1".padStart(64, '0')
    private val xOnlyPub =
        Secp256k1Instance
            .compressedPubKeyFor(priv.hexToByteArray())
            .copyOfRange(1, 33)
            .toHexKey()

    private fun lockedProof(lockPubKeyHex: String) = CashuProof(id = "keyset1", amount = 4, secret = P2PK.lockedSecret(lockPubKeyHex), c = "c-hex")

    private fun plainProof() = CashuProof(id = "keyset1", amount = 1, secret = "9a1b...plain-secret", c = "c-hex")

    private fun witnessVerifies(
        proof: CashuProof,
        xOnlyHex: String,
    ): Boolean {
        val witness = proof.witness ?: return false
        val sigs = (Json.parseToJsonElement(witness) as JsonObject)["signatures"] as JsonArray
        val sigHex = (sigs[0] as JsonPrimitive).content
        return Secp256k1Instance.verifySchnorr(
            signature = sigHex.hexToByteArray(),
            hash = sha256(proof.secret.encodeToByteArray()),
            pubKey = xOnlyHex.hexToByteArray(),
        )
    }

    @Test
    fun plainProofPassesThroughUnsigned() {
        val proof = plainProof()
        val out = signP2pkWitnesses(listOf(proof)) { error("resolver must not be called for a plain proof") }
        assertEquals(1, out.size)
        assertNull(out[0].witness, "a non-P2PK proof must not gain a witness")
        assertEquals(proof, out[0])
    }

    @Test
    fun lockedProofGetsVerifiableWitness() {
        // Locked to the x-only key (Nostr-identity style, 64 hex).
        val out =
            signP2pkWitnesses(listOf(lockedProof(xOnlyPub))) { lockXOnly ->
                assertEquals(xOnlyPub, lockXOnly, "resolver is queried by the lock's x-only pubkey")
                priv
            }
        assertTrue(witnessVerifies(out[0], xOnlyPub), "witness must verify under the lock pubkey")
    }

    @Test
    fun compressedLockResolvesByXOnly() {
        // Locked to the 33-byte compressed form (02/03 prefix) — the resolver
        // must still be asked by the 32-byte x-only pubkey, and the witness
        // must verify (the mint runs x-only BIP-340).
        val compressed = "02$xOnlyPub"
        val out =
            signP2pkWitnesses(listOf(lockedProof(compressed))) { lockXOnly ->
                assertEquals(xOnlyPub, lockXOnly, "the parity prefix must be stripped before resolving")
                priv
            }
        assertTrue(witnessVerifies(out[0], xOnlyPub))
    }

    @Test
    fun unknownLockThrowsNamingThePubkey() {
        val compressed = "02$xOnlyPub"
        val e =
            assertFailsWith<P2PKUnredeemableException> {
                signP2pkWitnesses(listOf(lockedProof(compressed))) { null }
            }
        // The exception carries the lock exactly as it appears in the secret so
        // callers can compare it against the user's own keys.
        assertEquals(compressed, e.lockPubKeyHex)
    }

    @Test
    fun mixedSetSignsOnlyTheLockedProofs() {
        val plain = plainProof()
        val locked = lockedProof(xOnlyPub)
        val out = signP2pkWitnesses(listOf(plain, locked)) { priv }
        assertNull(out[0].witness, "plain proof stays unsigned")
        assertTrue(witnessVerifies(out[1], xOnlyPub), "locked proof is signed")
    }

    @Test
    fun anyP2pkLockedDetectsLockedProofs() {
        assertFalse(listOf(plainProof()).anyP2pkLocked())
        assertTrue(listOf(plainProof(), lockedProof(xOnlyPub)).anyP2pkLocked())
    }

    @Test
    fun uppercaseLockMatchesLowercaseKeyIndex() {
        // NUT-11 doesn't mandate a hex case for `data`. Our key index is keyed
        // by lowercase x-only (Hex.encode is lowercase), so an uppercase lock we
        // hold the key for must still resolve — not be reported unredeemable.
        val upperLock = "02${xOnlyPub.uppercase()}"
        val index = mapOf(xOnlyPub to priv)
        val out = signP2pkWitnesses(listOf(lockedProof(upperLock))) { index[it] }
        assertTrue(witnessVerifies(out[0], xOnlyPub), "an uppercase lock we hold the key for must sign")
    }

    @Test
    fun firstUnsignableReturnsNullWhenAllSignable() {
        assertNull(firstUnsignableP2pkLock(listOf(plainProof(), lockedProof(xOnlyPub))) { priv })
    }

    @Test
    fun firstUnsignableNamesTheUnredeemableLock() {
        val other = "02${"b".repeat(64)}"
        assertEquals(other, firstUnsignableP2pkLock(listOf(plainProof(), lockedProof(other))) { null })
    }

    @Test
    fun firstUnsignableIsCaseInsensitive() {
        val upperLock = "02${xOnlyPub.uppercase()}"
        val index = mapOf(xOnlyPub to priv)
        assertNull(firstUnsignableP2pkLock(listOf(lockedProof(upperLock))) { index[it] })
    }
}
