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
package com.vitorpamplona.quartz.nip60Cashu.token

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class V4EncoderTest {
    private val mintUrl = "http://localhost:3338"

    // keyset id "009a1f293253e41e" + two proofs from the NUT-00 v4 example.
    private val proofs =
        listOf(
            CashuProof(
                id = "009a1f293253e41e",
                amount = 1,
                secret = "acc12435e7b8484c3cf1850149218af90f716a52bf4a5ed347e48ecc13f77388",
                c = "0244538319d5485d55bed3b29a642bee587937dab9e7a620e11e48ba4824f1cf3f",
            ),
            CashuProof(
                id = "009a1f293253e41e",
                amount = 2,
                secret = "1323d3d4707a58ad2e23ada4e9f1f49f5a5b4ac7b708eb0d61f738f48307e8ee",
                c = "023456aa110d84b4ac747aebd82c3b005aca50bf457ebd5737a4414fac3ae7d8c5",
            ),
        )

    @Test
    fun encodesCashuBPrefixAndRoundTrips() {
        val encoded = V4Encoder.encode(mintUrl, proofs, unit = "sat", memo = null)
        assertTrue(encoded.startsWith("cashuB"), "must carry the v4 wire prefix")

        val token = decode(encoded)
        assertEquals(mintUrl, token.m)
        assertEquals("sat", token.u)
        assertEquals(null, token.d)

        val groups = token.t!!
        // both proofs share a keyset id, so they collapse into a single group.
        assertEquals(1, groups.size)
        val decodedProofs = groups[0].p
        assertEquals(2, decodedProofs.size)
        assertEquals(setOf(1, 2), decodedProofs.map { it.a }.toSet())
        assertEquals(
            proofs.map { it.secret }.toSet(),
            decodedProofs.map { it.s }.toSet(),
        )
    }

    @Test
    fun carriesMemoWhenProvided() {
        val token = decode(V4Encoder.encode(mintUrl, proofs, memo = "minibits rules!"))
        assertEquals("minibits rules!", token.d)
    }

    @Test
    fun rejectsEmptyProofList() {
        assertFailsWith<IllegalArgumentException> {
            V4Encoder.encode(mintUrl, emptyList())
        }
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalEncodingApi::class)
    private fun decode(cashuB: String): V4Token {
        val raw = cashuB.removePrefix("cashuB")
        val bytes = Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL).decode(raw)
        return Cbor { ignoreUnknownKeys = true }.decodeFromByteArray<V4Token>(bytes)
    }
}
