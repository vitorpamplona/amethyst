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
package com.vitorpamplona.androiddevice

import com.vitorpamplona.androiddevice.emulator.EmulatorJwtAuth
import java.math.BigInteger
import java.nio.file.Files
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmulatorJwtAuthTest {
    private val dir = Files.createTempDirectory("jwks")

    private fun b64(s: String) = Base64.getUrlDecoder().decode(s)

    private fun field(
        json: String,
        name: String,
    ) = Regex("\"$name\":\"([^\"]+)\"").find(json)!!.groupValues[1]

    @Test
    fun theTokenVerifiesWithThePublishedKey() {
        val auth = EmulatorJwtAuth(dir, null, nowSeconds = { 1_000_000 })
        val jwk = auth.jwkSet()
        val token = auth.token(1_000_000)
        val (header, claims, signature) = token.split('.')

        val params = AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }.getParameterSpec(ECParameterSpec::class.java)
        val point = ECPoint(BigInteger(1, b64(field(jwk, "x"))), BigInteger(1, b64(field(jwk, "y"))))
        val key = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, params))
        val verifier = Signature.getInstance("SHA256withECDSAinP1363Format").apply { initVerify(key) }
        verifier.update("$header.$claims".toByteArray())
        assertTrue(verifier.verify(b64(signature)))
        assertEquals(64, b64(signature).size)

        val headerJson = String(b64(header))
        val claimsJson = String(b64(claims))
        assertEquals("ES256", field(headerJson, "alg"))
        assertEquals(auth.keyId, field(headerJson, "kid"))
        assertEquals(auth.keyId, field(jwk, "kid"))
        assertEquals(EmulatorJwtAuth.ISSUER, field(claimsJson, "iss"))
        assertTrue(claimsJson.contains("\"iat\":999995"), "issued a little in the past")
        assertTrue(claimsJson.contains("/android.emulation.control.EmulatorController/streamScreenshot"))
    }

    @Test
    fun registeringDropsAJwkFileAndCloseTakesItBack() {
        val active = dir.resolve("active.jwk")
        val auth = EmulatorJwtAuth(dir, active)
        // The emulator lists accepted keys in its active file; stand in for it.
        active.writeText("{}")
        val waiter =
            Thread {
                Thread.sleep(150)
                active.writeText(auth.jwkSet())
            }.apply { start() }
        val header = auth.authorization()
        waiter.join()
        assertTrue(header.startsWith("Bearer "))
        val file = dir.resolve("${auth.keyId}.jwk")
        assertTrue(file.exists())
        auth.close()
        assertFalse(file.exists())
    }

    @Test
    fun theTokenIsReusedUntilItNearlyExpires() {
        var now = 1_000L
        val auth = EmulatorJwtAuth(dir, null, nowSeconds = { now })
        val first = auth.authorization()
        now += 60
        assertEquals(first, auth.authorization())
        now += 3_600
        assertFalse(first == auth.authorization())
    }

    @Test
    fun coordinatesAreAlways32Bytes() {
        assertContentEquals(ByteArray(31) + byteArrayOf(1), EmulatorJwtAuth.unsigned32(BigInteger.ONE))
        val big = BigInteger(1, ByteArray(32) { 0xff.toByte() })
        assertEquals(32, EmulatorJwtAuth.unsigned32(big).size)
    }
}
