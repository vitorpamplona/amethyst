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

import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import java.math.BigInteger
import java.security.MessageDigest
import java.util.Locale
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Head-to-head Nostr signature-verification benchmark:
 *
 *  1. WhiteNoise `BIP340` — the BigInteger verifier vendored below verbatim from
 *     whitenoise-android (`app/src/main/java/dev/ipf/whitenoise/android/core/nostr/BIP340.kt`,
 *     master @ 67d33de). It is the verifier behind `NostrEventVerifier.verifies`,
 *     which gates their Zapstore release/self-update path.
 *  2. Quartz pure-Kotlin `Secp256k1.verifySchnorr` — commonMain, no JNI. Same
 *     language, same runtime as (1): isolates algorithm quality from FFI.
 *  3. Quartz pure-Kotlin `verifySchnorrFast` — the Nostr-only variant that skips
 *     the R.y-parity inversion.
 *  4. Quartz production path on JVM/Android: `Event.verifySignature()` semantics —
 *     `Hex.decode` x3 + libsecp256k1 through `Secp256k1Instance`.
 *
 * Run with: ./gradlew :quartz:jvmTest --tests "*.WhiteNoiseBip340Benchmark"
 */
class WhiteNoiseBip340Benchmark {
    private data class Vector(
        val pubKey: ByteArray,
        val msg: ByteArray,
        val sig: ByteArray,
        val pubKeyHex: String,
        val msgHex: String,
        val sigHex: String,
    )

    private val rnd = Random(20260910)

    private val corpus: List<Vector> =
        List(CORPUS) {
            var priv = ByteArray(32)
            do {
                priv = rnd.nextBytes(32)
            } while (!Secp256k1Instance.isPrivateKeyValid(priv))
            val pub = Secp256k1Instance.compressedPubKeyFor(priv).copyOfRange(1, 33)
            val msg = MessageDigest.getInstance("SHA-256").digest(rnd.nextBytes(64))
            val sig = Secp256k1Instance.signSchnorr(msg, priv, rnd.nextBytes(32))
            Vector(pub, msg, sig, Hex.encode(pub), Hex.encode(msg), Hex.encode(sig))
        }

    private inline fun bench(
        warmup: Int,
        iterations: Int,
        op: (Vector) -> Boolean,
    ): Double {
        var sink = 0
        repeat(warmup) { if (op(corpus[it % CORPUS])) sink++ }
        val start = System.nanoTime()
        repeat(iterations) { if (op(corpus[it % CORPUS])) sink++ }
        val elapsed = System.nanoTime() - start
        check(sink > 0)
        return elapsed.toDouble() / iterations
    }

    @Test
    fun agreementGate() {
        // Both implementations must accept every valid signature...
        corpus.forEach {
            assertTrue(WhiteNoiseBip340.verify(it.pubKeyHex, it.msgHex, it.sigHex), "WhiteNoise rejected a valid sig")
            assertTrue(Secp256k1.verifySchnorr(it.sig, it.msg, it.pubKey), "Quartz Kotlin rejected a valid sig")
            assertTrue(Secp256k1Instance.verifySchnorr(it.sig, it.msg, it.pubKey), "Quartz native rejected a valid sig")
        }
        // ...and reject a tampered one identically.
        corpus.take(16).forEach {
            val bad = it.sig.copyOf().also { s -> s[63] = (s[63].toInt() xor 0x01).toByte() }
            val badHex = Hex.encode(bad)
            assertEquals(false, WhiteNoiseBip340.verify(it.pubKeyHex, it.msgHex, badHex))
            assertEquals(false, Secp256k1.verifySchnorr(bad, it.msg, it.pubKey))
            assertEquals(false, Secp256k1Instance.verifySchnorr(bad, it.msg, it.pubKey))
        }
    }

    @Test
    fun benchmarkVerify() {
        val wn = bench(WN_WARMUP, WN_ITERS) { WhiteNoiseBip340.verify(it.pubKeyHex, it.msgHex, it.sigHex) }
        val qk = bench(QK_WARMUP, QK_ITERS) { Secp256k1.verifySchnorr(it.sig, it.msg, it.pubKey) }
        val qkFast = bench(QK_WARMUP, QK_ITERS) { Secp256k1.verifySchnorrFast(it.sig, it.msg, it.pubKey) }
        val qn = bench(QN_WARMUP, QN_ITERS) { Secp256k1Instance.verifySchnorr(it.sig, it.msg, it.pubKey) }
        // Quartz's real Event.verifySignature() path: hex decode x3 + native verify.
        val qnHex =
            bench(QN_WARMUP, QN_ITERS) {
                Secp256k1Instance.verifySchnorr(Hex.decode(it.sigHex), Hex.decode(it.msgHex), Hex.decode(it.pubKeyHex))
            }

        fun row(
            name: String,
            nsPerOp: Double,
        ) = String.format(
            Locale.US,
            "%-52s %12.1f us/op %12.0f ops/s %10.1fx",
            name,
            nsPerOp / 1000.0,
            1_000_000_000.0 / nsPerOp,
            nsPerOp / qnHex,
        )

        println()
        println("Schnorr (BIP-340) signature verification — $CORPUS distinct vectors, JVM ${System.getProperty("java.version")}")
        println("=".repeat(100))
        println(row("WhiteNoise BIP340.verify (BigInteger, hex in)", wn))
        println(row("Quartz Secp256k1.verifySchnorr (pure Kotlin)", qk))
        println(row("Quartz Secp256k1.verifySchnorrFast (pure Kotlin)", qkFast))
        println(row("Quartz Secp256k1Instance.verifySchnorr (libsecp256k1)", qn))
        println(row("Quartz Event.verifySignature() path (hex x3 + native)", qnHex))
        println("=".repeat(100))
        println(
            String.format(
                Locale.US,
                "WhiteNoise is %.0fx slower than Quartz's production path, %.0fx slower than Quartz's pure-Kotlin verifier.",
                wn / qnHex,
                wn / qk,
            ),
        )
        println(
            String.format(
                Locale.US,
                "Verifying 1,000 events: WhiteNoise %.2f s   Quartz %.3f s",
                wn * 1000 / 1e9,
                qnHex * 1000 / 1e9,
            ),
        )
        println()
    }

    companion object {
        private const val CORPUS = 64
        private const val WN_WARMUP = 100
        private const val WN_ITERS = 1_000
        private const val QK_WARMUP = 2_000
        private const val QK_ITERS = 20_000
        private const val QN_WARMUP = 5_000
        private const val QN_ITERS = 200_000
    }
}

/**
 * Copied verbatim from whitenoise-android
 * `app/src/main/java/dev/ipf/whitenoise/android/core/nostr/BIP340.kt` (master @ 67d33de),
 * renamed only to avoid a clash. Do not "improve" it — the point is to measure what
 * that app actually ships.
 */
private object WhiteNoiseBip340 {
    private val zero = BigInteger.ZERO
    private val one = BigInteger.ONE
    private val two = BigInteger.valueOf(2)
    private val three = BigInteger.valueOf(3)
    private val seven = BigInteger.valueOf(7)
    private val p = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16)
    private val n = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)
    private val gx = BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16)
    private val gy = BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16)
    private val g = Point(gx, gy)

    fun verify(
        publicKeyHex: String,
        messageHex: String,
        signatureHex: String,
    ): Boolean {
        val publicKey = publicKeyHex.lowercase(Locale.US).hexToBytes() ?: return false
        val message = messageHex.lowercase(Locale.US).hexToBytes() ?: return false
        val signature = signatureHex.lowercase(Locale.US).hexToBytes() ?: return false
        if (publicKey.size != 32 || message.size != 32 || signature.size != 64) return false

        val pubkeyPoint = liftX(unsigned(publicKey)) ?: return false
        val r = unsigned(signature.copyOfRange(0, 32))
        val s = unsigned(signature.copyOfRange(32, 64))
        if (r >= p || s >= n) return false

        val challenge = taggedHash("BIP0340/challenge", signature.copyOfRange(0, 32) + publicKey + message)
        val e = unsigned(challenge).mod(n)
        val rPoint = add(multiply(s, g), multiply(n.subtract(e), pubkeyPoint)) ?: return false
        return !rPoint.y.testBit(0) && rPoint.x == r
    }

    private fun liftX(x: BigInteger): Point? {
        if (x >= p) return null
        val c = mod(x.modPow(three, p).add(seven))
        val y = c.modPow(p.add(one).divide(BigInteger.valueOf(4)), p)
        if (mod(y.multiply(y).subtract(c)) != zero) return null
        return Point(x, if (y.testBit(0)) p.subtract(y) else y)
    }

    private fun taggedHash(
        tag: String,
        message: ByteArray,
    ): ByteArray {
        val tagHash = sha256(tag.toByteArray(Charsets.UTF_8))
        return sha256(tagHash + tagHash + message)
    }

    private fun multiply(
        scalar: BigInteger,
        point: Point,
    ): Point? {
        var result: Point? = null
        var addend: Point? = point
        var k = scalar.mod(n)
        while (k > zero && addend != null) {
            if (k.testBit(0)) result = add(result, addend)
            addend = double(addend)
            k = k.shiftRight(1)
        }
        return result
    }

    private fun add(
        left: Point?,
        right: Point?,
    ): Point? {
        if (left == null) return right
        if (right == null) return left
        if (left.x == right.x) {
            if (mod(left.y.add(right.y)) == zero) return null
            return double(left)
        }
        val lambda = mod(right.y.subtract(left.y).multiply(right.x.subtract(left.x).modInverse(p)))
        val x = mod(lambda.multiply(lambda).subtract(left.x).subtract(right.x))
        val y = mod(lambda.multiply(left.x.subtract(x)).subtract(left.y))
        return Point(x, y)
    }

    private fun double(point: Point): Point? {
        if (point.y == zero) return null
        val lambda = mod(three.multiply(point.x).multiply(point.x).multiply(two.multiply(point.y).modInverse(p)))
        val x = mod(lambda.multiply(lambda).subtract(two.multiply(point.x)))
        val y = mod(lambda.multiply(point.x.subtract(x)).subtract(point.y))
        return Point(x, y)
    }

    private fun mod(value: BigInteger): BigInteger = value.mod(p)

    private fun unsigned(bytes: ByteArray): BigInteger = BigInteger(1, bytes)

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun String.isHex(expectedLength: Int): Boolean = length == expectedLength && all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

    private fun String.hexToBytes(): ByteArray? {
        if (length % 2 != 0 || !isHex(length)) return null
        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private data class Point(
        val x: BigInteger,
        val y: BigInteger,
    )
}
