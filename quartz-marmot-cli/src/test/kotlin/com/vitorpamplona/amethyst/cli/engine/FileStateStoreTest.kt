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
package com.vitorpamplona.amethyst.cli.engine

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for MLS group state persistence.
 * Ensures state save/load/delete and retained epoch management work correctly.
 */
class FileStateStoreTest {
    private lateinit var dataDir: File
    private lateinit var store: FileStateStore

    @BeforeTest
    fun setup() {
        dataDir = File(System.getProperty("java.io.tmpdir"), "marmot-store-test-${System.nanoTime()}")
        dataDir.mkdirs()
        store = FileStateStore(dataDir)
    }

    @AfterTest
    fun cleanup() {
        dataDir.deleteRecursively()
    }

    @Test
    fun saveAndLoadState() =
        runTest {
            val groupId = "abc123"
            val state = byteArrayOf(1, 2, 3, 4, 5)

            store.save(groupId, state)
            val loaded = store.load(groupId)

            assertContentEquals(state, loaded)
        }

    @Test
    fun loadNonexistentReturnsNull() =
        runTest {
            val loaded = store.load("nonexistent")
            assertNull(loaded)
        }

    @Test
    fun deleteRemovesState() =
        runTest {
            val groupId = "to-delete"
            store.save(groupId, byteArrayOf(1, 2, 3))

            store.delete(groupId)
            val loaded = store.load(groupId)
            assertNull(loaded)
        }

    @Test
    fun listGroupsShowsSavedGroups() =
        runTest {
            store.save("group1", byteArrayOf(1))
            store.save("group2", byteArrayOf(2))
            store.save("group3", byteArrayOf(3))

            val groups = store.listGroups()
            assertEquals(3, groups.size)
            assertTrue(groups.contains("group1"))
            assertTrue(groups.contains("group2"))
            assertTrue(groups.contains("group3"))
        }

    @Test
    fun listGroupsEmptyWhenNoGroups() =
        runTest {
            val groups = store.listGroups()
            assertTrue(groups.isEmpty())
        }

    @Test
    fun saveAndLoadRetainedEpochs() =
        runTest {
            val groupId = "epoch-test"
            val epochs =
                listOf(
                    byteArrayOf(10, 20, 30),
                    byteArrayOf(40, 50, 60),
                )

            store.saveRetainedEpochs(groupId, epochs)
            val loaded = store.loadRetainedEpochs(groupId)

            assertEquals(2, loaded.size)
            assertContentEquals(epochs[0], loaded[0])
            assertContentEquals(epochs[1], loaded[1])
        }

    @Test
    fun loadRetainedEpochsNonexistentReturnsEmpty() =
        runTest {
            val loaded = store.loadRetainedEpochs("nonexistent")
            assertTrue(loaded.isEmpty())
        }

    @Test
    fun deleteAlsoRemovesRetainedEpochs() =
        runTest {
            val groupId = "epoch-delete"
            store.save(groupId, byteArrayOf(1))
            store.saveRetainedEpochs(groupId, listOf(byteArrayOf(10)))

            store.delete(groupId)

            assertNull(store.load(groupId))
            assertTrue(store.loadRetainedEpochs(groupId).isEmpty())
        }

    @Test
    fun overwriteExistingState() =
        runTest {
            val groupId = "overwrite"
            store.save(groupId, byteArrayOf(1, 2, 3))
            store.save(groupId, byteArrayOf(4, 5, 6))

            val loaded = store.load(groupId)
            assertContentEquals(byteArrayOf(4, 5, 6), loaded)
        }
}
