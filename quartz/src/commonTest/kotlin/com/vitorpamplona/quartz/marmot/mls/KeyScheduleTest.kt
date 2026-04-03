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
package com.vitorpamplona.quartz.marmot.mls

import com.vitorpamplona.quartz.marmot.mls.crypto.MlsCryptoProvider
import com.vitorpamplona.quartz.marmot.mls.messages.GroupContext
import com.vitorpamplona.quartz.marmot.mls.schedule.KeySchedule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Tests for MLS Key Schedule (RFC 9420 Section 8).
 *
 * Verifies the deterministic key derivation chain produces consistent results
 * and that all derived secrets are distinct.
 */
class KeyScheduleTest {
    @Test
    fun testDeriveEpochSecretsProducesAllKeys() {
        val groupContext =
            GroupContext(
                groupId = ByteArray(32) { 0x42 },
                epoch = 0,
                treeHash = ByteArray(32),
                confirmedTranscriptHash = ByteArray(32),
            )

        val ks = KeySchedule(groupContext.toTlsBytes())
        val commitSecret = ByteArray(32)
        val initSecret = ByteArray(32)

        val secrets = ks.deriveEpochSecrets(commitSecret, initSecret)

        // All secrets should be 32 bytes
        assertEquals(32, secrets.joinerSecret.size)
        assertEquals(32, secrets.welcomeSecret.size)
        assertEquals(32, secrets.epochSecret.size)
        assertEquals(32, secrets.senderDataSecret.size)
        assertEquals(32, secrets.encryptionSecret.size)
        assertEquals(32, secrets.exporterSecret.size)
        assertEquals(32, secrets.epochAuthenticator.size)
        assertEquals(32, secrets.externalSecret.size)
        assertEquals(32, secrets.confirmationKey.size)
        assertEquals(32, secrets.membershipKey.size)
        assertEquals(32, secrets.resumptionPsk.size)
        assertEquals(32, secrets.initSecret.size)
    }

    @Test
    fun testDeriveEpochSecretsAreDeterministic() {
        val groupContext =
            GroupContext(
                groupId = ByteArray(32) { 0x01 },
                epoch = 5,
                treeHash = ByteArray(32) { 0x02 },
                confirmedTranscriptHash = ByteArray(32) { 0x03 },
            )

        val ks = KeySchedule(groupContext.toTlsBytes())
        val commitSecret = ByteArray(32) { 0xAA.toByte() }
        val initSecret = ByteArray(32) { 0xBB.toByte() }

        val secrets1 = ks.deriveEpochSecrets(commitSecret, initSecret)
        val secrets2 = ks.deriveEpochSecrets(commitSecret, initSecret)

        assertContentEquals(secrets1.epochSecret, secrets2.epochSecret)
        assertContentEquals(secrets1.encryptionSecret, secrets2.encryptionSecret)
        assertContentEquals(secrets1.exporterSecret, secrets2.exporterSecret)
    }

    @Test
    fun testDerivedSecretsAreDistinct() {
        val groupContext =
            GroupContext(
                groupId = ByteArray(32),
                epoch = 0,
                treeHash = ByteArray(32),
                confirmedTranscriptHash = ByteArray(32),
            )

        val ks = KeySchedule(groupContext.toTlsBytes())
        val secrets = ks.deriveEpochSecrets(ByteArray(32), ByteArray(32))

        // All derived secrets should be different from each other
        val allSecrets =
            listOf(
                secrets.senderDataSecret,
                secrets.encryptionSecret,
                secrets.exporterSecret,
                secrets.confirmationKey,
                secrets.membershipKey,
            )

        for (i in allSecrets.indices) {
            for (j in i + 1 until allSecrets.size) {
                assertFalse(
                    allSecrets[i].contentEquals(allSecrets[j]),
                    "Secrets at index $i and $j should be different",
                )
            }
        }
    }

    @Test
    fun testDifferentGroupContextsProduceDifferentSecrets() {
        val ctx1 =
            GroupContext(
                groupId = ByteArray(32) { 0x01 },
                epoch = 0,
                treeHash = ByteArray(32),
                confirmedTranscriptHash = ByteArray(32),
            )

        val ctx2 =
            GroupContext(
                groupId = ByteArray(32) { 0x02 },
                epoch = 0,
                treeHash = ByteArray(32),
                confirmedTranscriptHash = ByteArray(32),
            )

        val commitSecret = ByteArray(32)
        val initSecret = ByteArray(32)

        val secrets1 = KeySchedule(ctx1.toTlsBytes()).deriveEpochSecrets(commitSecret, initSecret)
        val secrets2 = KeySchedule(ctx2.toTlsBytes()).deriveEpochSecrets(commitSecret, initSecret)

        assertFalse(secrets1.epochSecret.contentEquals(secrets2.epochSecret))
    }

    @Test
    fun testMlsExporter() {
        val groupContext =
            GroupContext(
                groupId = ByteArray(32),
                epoch = 0,
                treeHash = ByteArray(32),
                confirmedTranscriptHash = ByteArray(32),
            )

        val ks = KeySchedule(groupContext.toTlsBytes())
        val secrets = ks.deriveEpochSecrets(ByteArray(32), ByteArray(32))

        // Marmot exporter: MLS-Exporter("marmot", "group-event", 32)
        val marmotKey =
            KeySchedule.mlsExporter(
                secrets.exporterSecret,
                "marmot",
                "group-event".encodeToByteArray(),
                32,
            )

        assertEquals(32, marmotKey.size)

        // Same inputs produce same output (deterministic)
        val marmotKey2 =
            KeySchedule.mlsExporter(
                secrets.exporterSecret,
                "marmot",
                "group-event".encodeToByteArray(),
                32,
            )
        assertContentEquals(marmotKey, marmotKey2)

        // Different label produces different key
        val otherKey =
            KeySchedule.mlsExporter(
                secrets.exporterSecret,
                "other",
                "group-event".encodeToByteArray(),
                32,
            )
        assertFalse(marmotKey.contentEquals(otherKey))
    }

    @Test
    fun testExpandWithLabelProducesCorrectLength() {
        val secret = ByteArray(32) { 0x42 }

        val out16 = MlsCryptoProvider.expandWithLabel(secret, "test", ByteArray(0), 16)
        assertEquals(16, out16.size)

        val out32 = MlsCryptoProvider.expandWithLabel(secret, "test", ByteArray(0), 32)
        assertEquals(32, out32.size)

        val out48 = MlsCryptoProvider.expandWithLabel(secret, "test", ByteArray(0), 48)
        assertEquals(48, out48.size)
    }

    @Test
    fun testDeriveSecretMatchesExpandWithLabel() {
        val secret = ByteArray(32) { 0x42 }

        val derived = MlsCryptoProvider.deriveSecret(secret, "test")
        val expanded = MlsCryptoProvider.expandWithLabel(secret, "test", ByteArray(0), 32)

        assertContentEquals(derived, expanded)
    }

    @Test
    fun testHkdfExpandDeterminism() {
        val prk = ByteArray(32) { it.toByte() }
        val info = "test info".encodeToByteArray()

        val out1 = MlsCryptoProvider.hkdfExpand(prk, info, 32)
        val out2 = MlsCryptoProvider.hkdfExpand(prk, info, 32)

        assertContentEquals(out1, out2)
    }

    @Test
    fun testRefHash() {
        val value = byteArrayOf(0x01, 0x02, 0x03)
        val hash1 = MlsCryptoProvider.refHash("MLS 1.0 KeyPackage Reference", value)
        val hash2 = MlsCryptoProvider.refHash("MLS 1.0 KeyPackage Reference", value)

        assertEquals(32, hash1.size)
        assertContentEquals(hash1, hash2)

        // Different label produces different hash
        val hash3 = MlsCryptoProvider.refHash("MLS 1.0 Other Reference", value)
        assertFalse(hash1.contentEquals(hash3))
    }

    private fun assertContentEquals(
        expected: ByteArray,
        actual: ByteArray,
    ) {
        kotlin.test.assertContentEquals(expected, actual)
    }
}
