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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.quartz.cordn.sync.GroupCursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The on-disk cordn stores.
 *
 * Driven through a fake cipher rather than the Android KeyStore, which is the
 * point of the [CordnBlobCipher] seam: everything below the cipher — the
 * layout, the key encoding, the atomic write, the account/coordinator scoping
 * — is ordinary code that can fail in ordinary ways, and none of it is
 * testable on a device-only primitive. The fake really transforms the bytes,
 * so "the plaintext never reached the disk" is a claim these tests can make.
 */
class FileCordnStoresTest {
    /** XOR: reversible, and unmistakably not the input. */
    private class XorCipher : CordnBlobCipher {
        var encryptCalls = 0

        override fun encrypt(bytes: ByteArray): ByteArray {
            encryptCalls++
            return ByteArray(bytes.size) { (bytes[it].toInt() xor MASK).toByte() }
        }

        override fun decrypt(bytes: ByteArray) = ByteArray(bytes.size) { (bytes[it].toInt() xor MASK).toByte() }

        companion object {
            const val MASK = 0x5A
        }
    }

    private val root =
        File.createTempFile("cordn-store", "").also {
            it.delete()
            it.mkdirs()
        }
    private val cipher = XorCipher()

    private val account = "a".repeat(64)
    private val coordinator = "b".repeat(64)

    private fun dir() = CordnStorageLayout.directoryFor(root, account, coordinator)

    private fun groups() = FileCordnGroupStore(dir(), cipher)

    private fun keyPackages() = FileCordnKeyPackageStore(dir(), cipher)

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `a group round-trips through a restart`() =
        runTest {
            val state = byteArrayOf(1, 2, 3, 4, 5)
            groups().saveGroup("g1", state)

            // A new instance over the same directory: what a relaunch is.
            assertContentEquals(state, groups().loadGroup("g1"))
            assertEquals(listOf("g1"), groups().listGroups())
        }

    @Test
    fun `the plaintext never reaches the disk`() =
        runTest {
            // The one claim the whole class exists to make. An MlsGroupState is
            // the ratchet tree and every epoch secret; on disk in the clear it
            // is every message the group ever sent.
            val state = "ratchet-tree-and-epoch-secrets".encodeToByteArray()
            groups().saveGroup("g1", state)

            val onDisk = dir().walkTopDown().filter { it.isFile }.toList()
            assertTrue(onDisk.isNotEmpty(), "nothing was written at all")
            onDisk.forEach {
                val raw = it.readBytes()
                assertTrue(!raw.contentEquals(state), "${it.name} holds the plaintext")
                assertTrue(
                    !raw.decodeToString().contains("ratchet-tree"),
                    "${it.name} holds recognisable plaintext",
                )
            }
        }

    @Test
    fun `a gid that is a path traversal is stored, not rejected`() =
        runTest {
            // A cordn gid is caller-chosen and the coordinator never interprets
            // it (spec/00.md §4), so "../../etc/passwd" is a gid the protocol
            // allows. Marmot's store validates hex because a Marmot group id is
            // a hash; doing that here would refuse legitimate groups. Encoding
            // accepts it AND keeps it inside our directory.
            val hostile = "../../../etc/passwd"
            groups().saveGroup(hostile, byteArrayOf(9))

            assertEquals(listOf(hostile), groups().listGroups(), "the gid must come back verbatim")
            assertContentEquals(byteArrayOf(9), groups().loadGroup(hostile))

            val escaped = File(root, "cordn/etc").exists() || File(root.parentFile, "etc").exists()
            assertTrue(!escaped, "the write escaped the store directory")
            assertTrue(dir().walkTopDown().any { it.isFile }, "it was not written anywhere at all")
        }

    @Test
    fun `gids that differ only in case are different groups`() =
        runTest {
            // Base64url is case-sensitive; a case-folding encoding would merge
            // these two and let one group's state answer for the other.
            groups().saveGroup("Group", byteArrayOf(1))
            groups().saveGroup("group", byteArrayOf(2))

            assertContentEquals(byteArrayOf(1), groups().loadGroup("Group"))
            assertContentEquals(byteArrayOf(2), groups().loadGroup("group"))
            assertEquals(2, groups().listGroups().size)
        }

    @Test
    fun `two coordinators serving the same gid do not collide`() =
        runTest {
            // The rule the layout exists for. Two coordinators can both serve
            // gid "shared" as unrelated groups with different ratchet trees.
            val other = CordnStorageLayout.directoryFor(root, account, "c".repeat(64))
            FileCordnGroupStore(dir(), cipher).saveGroup("shared", byteArrayOf(1))
            FileCordnGroupStore(other, cipher).saveGroup("shared", byteArrayOf(2))

            assertContentEquals(byteArrayOf(1), groups().loadGroup("shared"))
            assertContentEquals(byteArrayOf(2), FileCordnGroupStore(other, cipher).loadGroup("shared"))
        }

    @Test
    fun `two accounts on one device do not see each other`() =
        runTest {
            val theirs = CordnStorageLayout.directoryFor(root, "d".repeat(64), coordinator)
            groups().saveGroup("g1", byteArrayOf(1))

            assertTrue(FileCordnGroupStore(theirs, cipher).listGroups().isEmpty())
            assertNull(FileCordnGroupStore(theirs, cipher).loadGroup("g1"))
        }

    @Test
    fun `a non-hex pubkey never becomes a directory`() {
        assertFailsWith<IllegalArgumentException> { CordnStorageLayout.directoryFor(root, "../escape", coordinator) }
        assertFailsWith<IllegalArgumentException> { CordnStorageLayout.directoryFor(root, account, "../escape") }
    }

    @Test
    fun `deleting a group takes its cursor with it`() =
        runTest {
            // Otherwise a later re-join of the same gid resumes from a cursor
            // belonging to a group it is no longer in, and silently skips
            // everything before it.
            groups().saveGroup("g1", byteArrayOf(1))
            groups().saveCursor("g1", GroupCursor(fetchCursor = 42, lastCursor = 99))

            groups().deleteGroup("g1")

            assertNull(groups().loadGroup("g1"))
            assertNull(groups().loadCursor("g1"), "the cursor outlived the group")
            assertTrue(groups().listGroups().isEmpty())
        }

    @Test
    fun `a cursor round-trips both of its fields`() =
        runTest {
            // They are not the same number and conflating them is invisible
            // until a fetch re-reads or skips a stretch of the stream.
            groups().saveCursor("g1", GroupCursor(fetchCursor = 7, lastCursor = 1234567890123L))

            val restored = assertNotNull(groups().loadCursor("g1"))
            assertEquals(7L, restored.fetchCursor)
            assertEquals(1234567890123L, restored.lastCursor)
        }

    @Test
    fun `a group with no state yet is not listed`() =
        runTest {
            // A cursor alone means a directory exists with nothing restorable
            // in it; reporting it as a group would make restore() build an
            // empty MlsGroup for a gid we do not actually hold.
            groups().saveCursor("ghost", GroupCursor(fetchCursor = 1))

            assertTrue(groups().listGroups().isEmpty())
        }

    @Test
    fun `a key package bundle round-trips and can be withdrawn`() =
        runTest {
            val bundle = byteArrayOf(7, 7, 7)
            keyPackages().save("ref1", bundle)

            assertContentEquals(bundle, keyPackages().load("ref1"))
            assertEquals(listOf("ref1"), keyPackages().list())

            keyPackages().delete("ref1")
            assertNull(keyPackages().load("ref1"))
            assertTrue(keyPackages().list().isEmpty())
        }

    @Test
    fun `a half-written temp file is not served as a bundle`() =
        runTest {
            // atomicWrite leaves one behind if the process dies between write
            // and rename. Listing it would hand joinPendingWelcomes a ref that
            // decodes to nothing and burn the Welcome it was meant to open.
            //
            // What excludes it is the encoding, not a name check: '.' is not in
            // the base64url alphabet. So this test is really a guard on the
            // encoding — swap it for something that passes names through and
            // this fails, which is the regression worth catching.
            keyPackages().save("ref1", byteArrayOf(1))
            File(dir(), "keypackages/${CordnStorageLayout.encodeKey("ref2")}.tmp").writeBytes(byteArrayOf(2))

            assertEquals(listOf("ref1"), keyPackages().list())
        }

    @Test
    fun `a stray file in the directory does not break the listing`() =
        runTest {
            // Ignoring one unreadable name is better than failing the listing
            // and hiding every real group behind it.
            groups().saveGroup("g1", byteArrayOf(1))
            File(dir(), "groups/not-base64-@@@").mkdirs()
            File(dir(), "groups/not-base64-@@@/state").writeBytes(byteArrayOf(0))

            assertEquals(listOf("g1"), groups().listGroups())
        }

    @Test
    fun `an empty store lists nothing rather than failing`() =
        runTest {
            assertTrue(groups().listGroups().isEmpty())
            assertTrue(keyPackages().list().isEmpty())
            assertNull(groups().loadGroup("nope"))
            assertNull(groups().loadCursor("nope"))
            assertNull(keyPackages().load("nope"))
        }

    @Test
    fun `concurrent saves of different groups all survive`() =
        runTest {
            // Dispatchers.IO is a pool, and a sync loop saving several groups
            // at once is the ordinary case.
            val store = groups()
            withContext(Dispatchers.IO) {
                (1..24).map { async { store.saveGroup("g$it", byteArrayOf(it.toByte())) } }.awaitAll()
            }

            assertEquals(24, store.listGroups().size)
            (1..24).forEach { assertContentEquals(byteArrayOf(it.toByte()), store.loadGroup("g$it")) }
        }

    @Test
    fun `a rewritten group leaves no temp file behind`() =
        runTest {
            val store = groups()
            store.saveGroup("g1", byteArrayOf(1))
            store.saveGroup("g1", byteArrayOf(2, 2))

            assertContentEquals(byteArrayOf(2, 2), store.loadGroup("g1"))
            assertTrue(
                dir().walkTopDown().none { it.name.endsWith(".tmp") },
                "a temp file survived the rename",
            )
        }
}
