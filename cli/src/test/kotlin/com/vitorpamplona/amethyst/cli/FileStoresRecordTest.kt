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
}
