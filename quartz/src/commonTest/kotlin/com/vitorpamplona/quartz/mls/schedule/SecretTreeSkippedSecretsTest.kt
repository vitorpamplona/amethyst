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
package com.vitorpamplona.quartz.mls.schedule

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SecretTreeSkippedSecretsTest {
    private val encryptionSecret = ByteArray(32) { 3 }

    @Test
    fun skippedSecretsRoundTripIntoAFreshTree() {
        val reference = SecretTree(encryptionSecret, leafCount = 2)
        val tree = SecretTree(encryptionSecret, leafCount = 2)
        tree.applicationKeyNonceForGeneration(leafIndex = 1, generation = 3)
        tree.handshakeKeyNonceForGeneration(leafIndex = 0, generation = 1)

        assertEquals(setOf(1 to 0, 1 to 1, 1 to 2), tree.exportSkippedApplicationSecrets().keys)
        assertEquals(setOf(0 to 0), tree.exportSkippedHandshakeSecrets().keys)

        val restored = SecretTree(encryptionSecret, leafCount = 2)
        restored.importSenderStates(tree.exportSenderStates())
        restored.importSkippedSecrets(tree.exportSkippedApplicationSecrets(), tree.exportSkippedHandshakeSecrets())

        for (generation in 0..2) {
            assertContentEquals(
                reference.applicationKeyNonceForGeneration(1, generation).nonce,
                restored.applicationKeyNonceForGeneration(1, generation).nonce,
            )
        }
        assertContentEquals(
            reference.handshakeKeyNonceForGeneration(0, 0).key,
            restored.handshakeKeyNonceForGeneration(0, 0).key,
        )
        assertTrue(restored.exportSkippedApplicationSecrets().isEmpty())
        assertTrue(restored.exportSkippedHandshakeSecrets().isEmpty())
    }
}
