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

import com.vitorpamplona.amethyst.cli.stores.FileIngestDedupStore
import com.vitorpamplona.marmot.MarmotIngestDedupStore
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals

/**
 * amy runs one command per process, so the echo of a message it sent comes back in the next
 * command: the id has to be on disk, bounded, and kept apart from the Welcome markers.
 */
class FileIngestDedupStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun id(n: Int) = n.toString(16).padStart(64, '0')

    @Test
    fun `own echoes survive the next command bounded and apart from the markers`() =
        runBlocking {
            val file = File(tmp.newFolder("amy"), "ingested.ids")
            val welcome = "f".repeat(64)
            val store = FileIngestDedupStore(file, maxEntries = 100)
            store.mark(welcome)
            val sent = 3 * MarmotIngestDedupStore.OWN_ECHO_LIMIT
            for (n in 0 until sent) store.markOwnEcho(id(n))

            val next = FileIngestDedupStore(file, maxEntries = 100)
            val echoes = next.loadOwnEchoes()
            assertEquals(MarmotIngestDedupStore.OWN_ECHO_LIMIT, echoes.size)
            assertEquals(id(sent - 1), echoes.last())
            assertEquals(setOf(welcome), next.loadAll())
        }
}
