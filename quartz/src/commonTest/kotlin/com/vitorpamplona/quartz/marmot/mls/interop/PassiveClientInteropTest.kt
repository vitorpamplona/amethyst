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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Interop tests for MLS passive client scenarios against IETF test vectors
 * from github.com/mlswg/mls-implementations.
 *
 * These tests verify that a passive client can:
 * 1. Deserialize KeyPackages and Welcome messages from other implementations
 * 2. Parse commit messages from group epochs
 *
 * Full passive client protocol (join via Welcome, process commits, verify
 * epoch_authenticator) requires MlsGroup.processWelcome and processCommit
 * which are platform-specific (jvmAndroidTest). These common tests validate
 * the wire format layer.
 */
class PassiveClientInteropTest {
    private val welcomeVectors: List<PassiveClientVector> =
        JsonMapper.jsonInstance
            .decodeFromString<List<PassiveClientVector>>(
                TestResourceLoader().loadString("mls/passive-client-welcome.json"),
            ).filter { it.cipherSuite == 1 }

    private val commitVectors: List<PassiveClientVector> =
        JsonMapper.jsonInstance
            .decodeFromString<List<PassiveClientVector>>(
                TestResourceLoader().loadString("mls/passive-client-handling-commit.json"),
            ).filter { it.cipherSuite == 1 }

    private val randomVectors: List<PassiveClientVector> =
        JsonMapper.jsonInstance
            .decodeFromString<List<PassiveClientVector>>(
                TestResourceLoader().loadString("mls/passive-client-random.json"),
            ).filter { it.cipherSuite == 1 }

    @Test
    fun testWelcomeVectorDeserialization() {
        assertTrue(welcomeVectors.isNotEmpty(), "No cipher_suite==1 passive-client-welcome vectors")

        for ((idx, v) in welcomeVectors.withIndex()) {
            // Deserialize KeyPackage
            val kpBytes = v.keyPackage.hexToByteArray()
            val kpMsg = MlsMessage.decodeTls(TlsReader(kpBytes))
            assertEquals(
                WireFormat.KEY_PACKAGE,
                kpMsg.wireFormat,
                "KeyPackage format mismatch at welcome vector $idx",
            )

            // Deserialize Welcome
            val welcomeBytes = v.welcome.hexToByteArray()
            val welcomeMsg = MlsMessage.decodeTls(TlsReader(welcomeBytes))
            assertEquals(
                WireFormat.WELCOME,
                welcomeMsg.wireFormat,
                "Welcome format mismatch at welcome vector $idx",
            )

            // Verify epoch_authenticator is valid hex
            val epochAuth = v.initialEpochAuthenticator.hexToByteArray()
            assertEquals(
                32,
                epochAuth.size,
                "initial_epoch_authenticator should be 32 bytes at welcome vector $idx",
            )
        }
    }

    @Test
    fun testCommitVectorDeserialization() {
        assertTrue(commitVectors.isNotEmpty(), "No cipher_suite==1 passive-client-commit vectors")

        for ((idx, v) in commitVectors.withIndex()) {
            // Deserialize Welcome
            val welcomeBytes = v.welcome.hexToByteArray()
            val welcomeMsg = MlsMessage.decodeTls(TlsReader(welcomeBytes))
            assertEquals(WireFormat.WELCOME, welcomeMsg.wireFormat)

            // Verify all epoch commits can be deserialized
            for ((epochIdx, epoch) in v.epochs.withIndex()) {
                val commitBytes = epoch.commit.hexToByteArray()
                val commitMsg = MlsMessage.decodeTls(TlsReader(commitBytes))
                assertTrue(
                    commitMsg.wireFormat == WireFormat.PUBLIC_MESSAGE ||
                        commitMsg.wireFormat == WireFormat.PRIVATE_MESSAGE,
                    "Commit should be PublicMessage or PrivateMessage at commit vector $idx, epoch $epochIdx",
                )

                // Verify each proposal can be deserialized
                for ((propIdx, proposal) in epoch.proposals.withIndex()) {
                    val propBytes = proposal.hexToByteArray()
                    val propMsg = MlsMessage.decodeTls(TlsReader(propBytes))
                    assertTrue(
                        propMsg.wireFormat == WireFormat.PUBLIC_MESSAGE ||
                            propMsg.wireFormat == WireFormat.PRIVATE_MESSAGE,
                        "Proposal should be PublicMessage or PrivateMessage at commit vector $idx, epoch $epochIdx, proposal $propIdx",
                    )
                }
            }
        }
    }

    @Test
    fun testRandomVectorDeserialization() {
        assertTrue(randomVectors.isNotEmpty(), "No cipher_suite==1 passive-client-random vectors")

        for ((idx, v) in randomVectors.withIndex()) {
            // Deserialize Welcome
            val welcomeBytes = v.welcome.hexToByteArray()
            val welcomeMsg = MlsMessage.decodeTls(TlsReader(welcomeBytes))
            assertEquals(WireFormat.WELCOME, welcomeMsg.wireFormat)

            // Verify all epoch commits
            for ((epochIdx, epoch) in v.epochs.withIndex()) {
                val commitBytes = epoch.commit.hexToByteArray()
                val commitMsg = MlsMessage.decodeTls(TlsReader(commitBytes))
                assertTrue(
                    commitMsg.wireFormat == WireFormat.PUBLIC_MESSAGE ||
                        commitMsg.wireFormat == WireFormat.PRIVATE_MESSAGE,
                    "Commit format error at random vector $idx, epoch $epochIdx",
                )
            }
        }
    }

    @Test
    fun testAllVectorsHaveValidEpochAuthenticators() {
        val allVectors = welcomeVectors + commitVectors + randomVectors

        for ((idx, v) in allVectors.withIndex()) {
            val initialAuth = v.initialEpochAuthenticator.hexToByteArray()
            assertEquals(32, initialAuth.size, "Invalid initial_epoch_authenticator at $idx")

            for ((epochIdx, epoch) in v.epochs.withIndex()) {
                val epochAuth = epoch.epochAuthenticator.hexToByteArray()
                assertEquals(
                    32,
                    epochAuth.size,
                    "Invalid epoch_authenticator at vector $idx, epoch $epochIdx",
                )
            }
        }
    }
}
