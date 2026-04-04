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
package com.vitorpamplona.quartz.marmot.mls.interop

import com.vitorpamplona.quartz.TestResourceLoader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.framing.MlsMessage
import com.vitorpamplona.quartz.marmot.mls.framing.WireFormat
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Interop tests for MLS Welcome message processing (RFC 9420 Section 12.4.3.1)
 * against IETF test vectors from github.com/mlswg/mls-implementations (welcome.json).
 *
 * Verifies that Welcome messages and KeyPackages produced by OpenMLS and mls-rs
 * can be correctly deserialized by Quartz.
 */
class WelcomeInteropTest {
    private val allVectors: List<WelcomeVector> =
        JsonMapper.jsonInstance.decodeFromString<List<WelcomeVector>>(
            TestResourceLoader().loadString("mls/welcome.json"),
        )

    private val vectors: List<WelcomeVector> =
        allVectors.filter { it.cipherSuite == 1 }

    @Test
    fun testKeyPackageDeserialization() {
        assertTrue(vectors.isNotEmpty(), "No cipher_suite==1 welcome vectors found")

        for ((idx, v) in vectors.withIndex()) {
            val kpBytes = v.keyPackage.hexToByteArray()
            // KeyPackage is wrapped in MlsMessage
            val mlsMsg = MlsMessage.decodeTls(TlsReader(kpBytes))
            assertEquals(
                WireFormat.KEY_PACKAGE,
                mlsMsg.wireFormat,
                "KeyPackage wire format mismatch at vector $idx",
            )

            // Verify round-trip
            assertContentEquals(
                kpBytes,
                mlsMsg.toTlsBytes(),
                "KeyPackage round-trip mismatch at vector $idx",
            )
        }
    }

    @Test
    fun testWelcomeDeserialization() {
        assertTrue(vectors.isNotEmpty(), "No cipher_suite==1 welcome vectors found")

        for ((idx, v) in vectors.withIndex()) {
            val welcomeBytes = v.welcome.hexToByteArray()
            val mlsMsg = MlsMessage.decodeTls(TlsReader(welcomeBytes))
            assertEquals(
                WireFormat.WELCOME,
                mlsMsg.wireFormat,
                "Welcome wire format mismatch at vector $idx",
            )

            // Verify round-trip
            assertContentEquals(
                welcomeBytes,
                mlsMsg.toTlsBytes(),
                "Welcome round-trip mismatch at vector $idx",
            )
        }
    }
}
