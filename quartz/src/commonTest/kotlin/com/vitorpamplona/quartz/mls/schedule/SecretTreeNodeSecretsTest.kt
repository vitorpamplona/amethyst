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
import kotlin.test.assertFailsWith

class SecretTreeNodeSecretsTest {
    private val encryptionSecret = ByteArray(32) { 9 }

    private fun key(
        tree: SecretTree,
        leafIndex: Int,
    ) = tree.applicationKeyNonceForGeneration(leafIndex, 0).key

    @Test
    fun derivingALeafKeepsTheSiblingsAndDropsThePath() {
        // 4 leaves: leaf nodes 0, 2, 4, 6; parents 1 and 5; root 3
        val tree = SecretTree(encryptionSecret, leafCount = 4)
        assertEquals(setOf(3), tree.exportNodeSecrets().keys)

        key(tree, 0)
        assertEquals(setOf(2, 5), tree.exportNodeSecrets().keys)

        key(tree, 3)
        assertEquals(setOf(2, 4), tree.exportNodeSecrets().keys)
    }

    @Test
    fun aTreeWithoutItsRootDerivesTheSameKeys() {
        val reference = SecretTree(encryptionSecret, leafCount = 4)
        val source = SecretTree(encryptionSecret, leafCount = 4)
        key(source, 0)

        // what another implementation hands over once leaf 0 has sent: no root
        val imported = SecretTree(ByteArray(0), leafCount = 4)
        imported.importNodeSecrets(source.exportNodeSecrets())

        for (leaf in 1..3) assertContentEquals(key(reference, leaf), key(imported, leaf))
    }

    @Test
    fun nonPowerOfTwoTreesDeriveTheSameKeys() {
        val reference = SecretTree(encryptionSecret, leafCount = 5)
        val source = SecretTree(encryptionSecret, leafCount = 5)
        key(source, 4)

        val imported = SecretTree(ByteArray(0), leafCount = 5)
        imported.importNodeSecrets(source.exportNodeSecrets())
        for (leaf in 0..3) assertContentEquals(key(reference, leaf), key(imported, leaf))
    }

    @Test
    fun aLeafWithNoKnownAncestorFails() {
        val source = SecretTree(encryptionSecret, leafCount = 4)
        key(source, 0)
        val imported = SecretTree(ByteArray(0), leafCount = 4)
        imported.importNodeSecrets(source.exportNodeSecrets().filterKeys { it != 5 })

        assertFailsWith<IllegalArgumentException> { key(imported, 2) }
    }
}
