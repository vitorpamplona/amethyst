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
package com.vitorpamplona.amethyst.cli

import com.vitorpamplona.amethyst.cli.stores.FileMarmotMessageStore
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The epoch-admin, media-secret and verdict records are first write wins, so a record a crash cut
 * short must never read as a whole one: an admin set missing keys would judge that epoch's
 * deletions unauthorized for good.
 */
class FileStoresRecordTest {
    @get:Rule val tmp = TemporaryFolder()

    private val group = "c".repeat(64)
    private val adminA = "a".repeat(64)
    private val adminB = "b".repeat(64)

    @Test
    fun `a record cut short by a crash is not read`() =
        runBlocking {
            val dir = tmp.newFolder("messages")
            FileMarmotMessageStore(dir).recordEpochAdmins(group, 7, setOf(adminA, adminB))
            // The crash: the next record was cut after its first key.
            File(dir, "$group.epoch-admins").appendText("12 $adminA")

            assertEquals(mapOf(7L to setOf(adminA, adminB)), FileMarmotMessageStore(dir).loadEpochAdmins(group))
        }

    @Test
    fun `the next record replaces a cut-short one instead of completing it`() =
        runBlocking {
            val dir = tmp.newFolder("messages")
            File(dir.also { it.mkdirs() }, "$group.epoch-admins").writeText("12 $adminA")

            val store = FileMarmotMessageStore(dir)
            store.recordEpochAdmins(group, 12, setOf(adminA, adminB))

            assertEquals(mapOf(12L to setOf(adminA, adminB)), FileMarmotMessageStore(dir).loadEpochAdmins(group))
        }

    private fun innerEvent(
        id: Char,
        createdAt: Long,
    ) = """{"id":"${id.toString().repeat(64)}","pubkey":"${"e".repeat(64)}","created_at":$createdAt,"kind":9,"tags":[],"content":"m","sig":""}"""

    /** Read by every command for every group: kept as messages are appended, not read off the log. */
    @Test
    fun `the newest message time is kept apart from the log`() =
        runBlocking {
            val dir = tmp.newFolder("messages")
            val store = FileMarmotMessageStore(dir)
            assertNull(store.newestMessageCreatedAt(group))
            store.appendMessage(group, innerEvent('1', 200))
            store.appendMessage(group, innerEvent('2', 500))
            store.appendMessage(group, innerEvent('3', 300))

            assertTrue(File(dir, "$group.messages").delete())
            assertEquals(500L, FileMarmotMessageStore(dir).newestMessageCreatedAt(group))
        }

    @Test
    fun `a log with no newest time is read once and the answer kept`() =
        runBlocking {
            val dir = tmp.newFolder("messages")
            val store = FileMarmotMessageStore(dir)
            store.appendMessage(group, innerEvent('1', 200))
            store.appendMessage(group, innerEvent('2', 700))
            File(dir, "$group.newest").delete()

            assertEquals(700L, FileMarmotMessageStore(dir).newestMessageCreatedAt(group))
            assertTrue(File(dir, "$group.messages").delete())
            assertEquals(700L, FileMarmotMessageStore(dir).newestMessageCreatedAt(group))
        }
}
