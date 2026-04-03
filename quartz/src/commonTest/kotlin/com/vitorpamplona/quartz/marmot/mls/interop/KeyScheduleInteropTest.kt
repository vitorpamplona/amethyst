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
import com.vitorpamplona.quartz.marmot.mls.schedule.KeySchedule
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Interop tests for MLS Key Schedule (RFC 9420 Section 8) against IETF test vectors
 * from github.com/mlswg/mls-implementations (key-schedule.json).
 *
 * Tests the full epoch key derivation chain across multiple epochs, verifying all
 * 12 derived secrets match the reference implementation outputs from OpenMLS and mls-rs.
 */
class KeyScheduleInteropTest {
    private val allVectors: List<KeyScheduleVector> =
        JsonMapper.jsonInstance.decodeFromString<List<KeyScheduleVector>>(
            TestResourceLoader().loadString("mls/key-schedule.json"),
        )

    private val vectors: List<KeyScheduleVector> =
        allVectors.filter { it.cipherSuite == 1 }

    @Test
    fun testKeyScheduleEpochs() {
        assertTrue(vectors.isNotEmpty(), "No cipher_suite==1 key-schedule vectors found")

        for (v in vectors) {
            // Use the initial_init_secret from the test vector
            var initSecret = v.initialInitSecret.hexToByteArray()

            for ((epochIdx, epoch) in v.epochs.withIndex()) {
                val groupContext = epoch.groupContext.hexToByteArray()
                val commitSecret = epoch.commitSecret.hexToByteArray()
                val pskSecret = epoch.pskSecret.hexToByteArray()

                val ks = KeySchedule(groupContext)
                val secrets = ks.deriveEpochSecrets(commitSecret, initSecret, pskSecret)

                assertEquals(
                    epoch.joinerSecret,
                    secrets.joinerSecret.toHexKey(),
                    "joiner_secret mismatch at epoch $epochIdx",
                )
                assertEquals(
                    epoch.welcomeSecret,
                    secrets.welcomeSecret.toHexKey(),
                    "welcome_secret mismatch at epoch $epochIdx",
                )
                assertEquals(
                    epoch.senderDataSecret,
                    secrets.senderDataSecret.toHexKey(),
                    "sender_data_secret mismatch at epoch $epochIdx",
                )
                assertEquals(
                    epoch.encryptionSecret,
                    secrets.encryptionSecret.toHexKey(),
                    "encryption_secret mismatch at epoch $epochIdx",
                )
                assertEquals(
                    epoch.exporterSecret,
                    secrets.exporterSecret.toHexKey(),
                    "exporter_secret mismatch at epoch $epochIdx",
                )
                assertEquals(
                    epoch.epochAuthenticator,
                    secrets.epochAuthenticator.toHexKey(),
                    "epoch_authenticator mismatch at epoch $epochIdx",
                )
                assertEquals(
                    epoch.externalSecret,
                    secrets.externalSecret.toHexKey(),
                    "external_secret mismatch at epoch $epochIdx",
                )
                assertEquals(
                    epoch.confirmationKey,
                    secrets.confirmationKey.toHexKey(),
                    "confirmation_key mismatch at epoch $epochIdx",
                )
                assertEquals(
                    epoch.membershipKey,
                    secrets.membershipKey.toHexKey(),
                    "membership_key mismatch at epoch $epochIdx",
                )
                assertEquals(
                    epoch.resumptionPsk,
                    secrets.resumptionPsk.toHexKey(),
                    "resumption_psk mismatch at epoch $epochIdx",
                )
                assertEquals(
                    epoch.initSecret,
                    secrets.initSecret.toHexKey(),
                    "init_secret mismatch at epoch $epochIdx",
                )

                // Use this epoch's derived init_secret for the next epoch
                initSecret = secrets.initSecret
            }
        }
    }

    @Test
    fun testMlsExporter() {
        assertTrue(vectors.isNotEmpty(), "No cipher_suite==1 key-schedule vectors found")

        for (v in vectors) {
            var initSecret = v.initialInitSecret.hexToByteArray()

            for ((epochIdx, epoch) in v.epochs.withIndex()) {
                val groupContext = epoch.groupContext.hexToByteArray()
                val commitSecret = epoch.commitSecret.hexToByteArray()
                val pskSecret = epoch.pskSecret.hexToByteArray()

                val ks = KeySchedule(groupContext)
                val secrets = ks.deriveEpochSecrets(commitSecret, initSecret, pskSecret)

                // Test MLS-Exporter
                // The exporter label and context in the test vectors are hex-encoded byte arrays.
                // The mlsExporter API takes a String label, so decode hex to bytes then to String.
                val exporterLabel = String(epoch.exporter.label.hexToByteArray())
                val exporterContext = epoch.exporter.context.hexToByteArray()

                val exported =
                    KeySchedule.mlsExporter(
                        secrets.exporterSecret,
                        exporterLabel,
                        exporterContext,
                        epoch.exporter.length,
                    )

                assertEquals(
                    epoch.exporter.secret,
                    exported.toHexKey(),
                    "MLS-Exporter mismatch at epoch $epochIdx",
                )

                initSecret = secrets.initSecret
            }
        }
    }
}
