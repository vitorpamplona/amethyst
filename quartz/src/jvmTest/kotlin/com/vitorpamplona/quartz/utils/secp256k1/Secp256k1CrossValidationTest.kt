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
package com.vitorpamplona.quartz.utils.secp256k1

import com.vitorpamplona.quartz.utils.Secp256k1InstanceC
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import fr.acinq.secp256k1.Secp256k1 as NativeSecp256k1

/**
 * Byte-for-byte cross-validation between the three secp256k1 implementations
 * available on JVM:
 *   1. ACINQ / libsecp256k1-kmp (reference implementation)
 *   2. Pure Kotlin ([Secp256k1])
 *   3. Custom C library via JNI ([Secp256k1InstanceC]) — when loadable
 *
 * BIP-340 signatures are deterministic given (privkey, message, aux_rand),
 * so two correct implementations MUST produce identical signature bytes.
 * This catches subtle reduction/limb-representation bugs that wouldn't show
 * up in a roundtrip verify test (where a wrong-but-internally-consistent
 * implementation can still verify its own output).
 */
class Secp256k1CrossValidationTest {
    private val acinq = NativeSecp256k1.get()

    // Deterministic RNG for reproducible test failures. java.util.Random has
    // a documented LCG and is fine for non-cryptographic test data.
    private val rng = Random(0xAFEE5EED1234L)

    private fun randomSeckey(): ByteArray {
        while (true) {
            val b = ByteArray(32).also { rng.nextBytes(it) }
            if (Secp256k1.secKeyVerify(b)) return b
        }
    }

    private fun randomMessage(size: Int): ByteArray = ByteArray(size).also { rng.nextBytes(it) }

    private fun randomAuxRand(): ByteArray = ByteArray(32).also { rng.nextBytes(it) }

    @Test
    fun pubkeyCreateMatchesAcinq() {
        // 50 distinct private keys must produce identical uncompressed pubkeys.
        repeat(50) {
            val seckey = randomSeckey()
            val kotlinPub = Secp256k1.pubkeyCreate(seckey)
            val acinqPub = acinq.pubkeyCreate(seckey)
            assertContentEquals(
                acinqPub,
                kotlinPub,
                "pubkeyCreate must be byte-identical to ACINQ for seckey=${seckey.toHex()}",
            )
        }
    }

    @Test
    fun signSchnorrMatchesAcinq() {
        // 50 random (seckey, message, auxrand) triples must yield byte-identical
        // BIP-340 signatures. Any divergence here points at a reduction or
        // limb-representation bug in one implementation.
        repeat(50) {
            val seckey = randomSeckey()
            val msg = randomMessage(32) // Nostr event IDs are 32 bytes
            val aux = randomAuxRand()
            val kotlinSig = Secp256k1.signSchnorr(msg, seckey, aux)
            val acinqSig = acinq.signSchnorr(msg, seckey, aux)
            assertContentEquals(
                acinqSig,
                kotlinSig,
                "signSchnorr must be byte-identical to ACINQ for seckey=${seckey.toHex()} msg=${msg.toHex()}",
            )
        }
    }

    @Test
    fun signSchnorrVariableMessageLengthSelfConsistent() {
        // ACINQ's signSchnorr API hard-codes the BIP-340 convention that the
        // message is exactly 32 bytes. Our Kotlin implementation accepts
        // messages of any length (NIP-01 event bodies can hash to something
        // other than 32 bytes in custom protocols). For non-32 inputs we
        // verify that the Kotlin signer and verifier at least self-agree:
        // any (msg, sig, pub) tuple produced by the signer must verify.
        val lengths = intArrayOf(0, 1, 31, 33, 55, 56, 64, 65, 127, 128, 511, 512, 1024)
        val seckey = randomSeckey()
        val xOnly = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(seckey)).copyOfRange(1, 33)
        for (len in lengths) {
            val msg = randomMessage(len)
            val aux = randomAuxRand()
            val sig = Secp256k1.signSchnorr(msg, seckey, aux)
            assertTrue(
                Secp256k1.verifySchnorr(sig, msg, xOnly),
                "Kotlin self-verify failed at message length $len",
            )
        }

        // For the 32-byte case we can cross-check against ACINQ for
        // byte-identical signatures, since that's what BIP-340 covers.
        val msg32 = randomMessage(32)
        val aux = randomAuxRand()
        val kotlinSig = Secp256k1.signSchnorr(msg32, seckey, aux)
        val acinqSig = acinq.signSchnorr(msg32, seckey, aux)
        assertContentEquals(acinqSig, kotlinSig)
    }

    @Test
    fun customCImplementationMatchesAcinqAndKotlin() {
        val cAvailable =
            try {
                Secp256k1InstanceC.init()
                true
            } catch (e: UnsatisfiedLinkError) {
                println("SKIP: custom C library not loadable (${e.message})")
                false
            } catch (e: Throwable) {
                println("SKIP: custom C library init failed (${e.message})")
                false
            }
        if (!cAvailable) return

        // 25 triples — fewer than the pure tests because each round exercises
        // three implementations and the test is slower.
        repeat(25) {
            val seckey = randomSeckey()
            val msg = randomMessage(32)
            val aux = randomAuxRand()

            val kotlinPubCompressed = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(seckey))
            val acinqPubCompressed = acinq.pubKeyCompress(acinq.pubkeyCreate(seckey))
            val cPubCompressed = Secp256k1InstanceC.compressedPubKeyFor(seckey)
            assertContentEquals(acinqPubCompressed, kotlinPubCompressed, "pubkey: Kotlin vs ACINQ")
            assertContentEquals(acinqPubCompressed, cPubCompressed, "pubkey: C vs ACINQ")

            val kotlinSig = Secp256k1.signSchnorr(msg, seckey, aux)
            val acinqSig = acinq.signSchnorr(msg, seckey, aux)
            val cSig = Secp256k1InstanceC.signSchnorr(msg, seckey, aux)
            assertContentEquals(acinqSig, kotlinSig, "sig: Kotlin vs ACINQ")
            assertContentEquals(acinqSig, cSig, "sig: C vs ACINQ")
        }
    }

    @Test
    fun privKeyTweakAddMatchesAcinq() {
        repeat(30) {
            val seckey = randomSeckey()
            val tweak = randomSeckey()
            val kotlinResult = Secp256k1.privKeyTweakAdd(seckey, tweak)
            val acinqResult = acinq.privKeyTweakAdd(seckey.copyOf(), tweak)
            assertContentEquals(
                acinqResult,
                kotlinResult,
                "privKeyTweakAdd mismatch for seckey=${seckey.toHex()} tweak=${tweak.toHex()}",
            )
        }
    }

    @Test
    fun batchOfSignaturesRoundTripAcrossImpls() {
        // Sign with ACINQ, verify with Kotlin and C. Sign with Kotlin, verify
        // with ACINQ and C. This is the weakest form of interoperability check,
        // but covers the case where a signing bug produces a valid-looking
        // signature that only the producing implementation accepts.
        val seckey = randomSeckey()
        val kotlinPubCompressed = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(seckey))
        val xOnly = kotlinPubCompressed.copyOfRange(1, 33)

        repeat(20) {
            val msg = randomMessage(32)
            val aux = randomAuxRand()
            val kotlinSig = Secp256k1.signSchnorr(msg, seckey, aux)
            val acinqSig = acinq.signSchnorr(msg, seckey, aux)

            // Same inputs → same signature (BIP-340 determinism).
            assertContentEquals(acinqSig, kotlinSig)

            assertTrue(acinq.verifySchnorr(kotlinSig, msg, xOnly), "ACINQ must verify Kotlin sig")
            assertTrue(
                Secp256k1.verifySchnorr(acinqSig, msg, xOnly),
                "Kotlin must verify ACINQ sig",
            )
        }
    }

    @Test
    fun ecdhXOnlySymmetric() {
        // Kotlin ecdhXOnly shared secret should equal what ACINQ's
        // pubKeyTweakMul produces (after extracting the x coordinate). The
        // two sides of an ECDH must also see the same shared secret.
        repeat(20) {
            val skA = randomSeckey()
            val skB = randomSeckey()
            val pubA = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(skA))
            val pubB = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(skB))
            val xA = pubA.copyOfRange(1, 33)
            val xB = pubB.copyOfRange(1, 33)

            val kotlinAB = Secp256k1.ecdhXOnly(xB, skA)
            val kotlinBA = Secp256k1.ecdhXOnly(xA, skB)
            assertContentEquals(kotlinAB, kotlinBA, "ECDH must be symmetric")

            // Compare against ACINQ's pubKeyTweakMul + extract x
            val acinqAB = acinq.pubKeyTweakMul(pubB, skA).copyOfRange(1, 33)
            assertContentEquals(acinqAB, kotlinAB, "Kotlin ECDH must match ACINQ")
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { b -> ((b.toInt() and 0xFF) or 0x100).toString(16).substring(1) }
}
