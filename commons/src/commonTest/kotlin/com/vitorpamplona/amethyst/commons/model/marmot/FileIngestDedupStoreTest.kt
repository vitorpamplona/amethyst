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
package com.vitorpamplona.amethyst.commons.model.marmot

import com.vitorpamplona.amethyst.commons.util.deleteRecursivelyQuietly
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.amethyst.commons.util.platformTempDirectory
import com.vitorpamplona.marmot.MarmotIngestDedupStore
import kotlinx.coroutines.test.runTest
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The echoes of the messages this device sent must survive a restart (our own kind:445 never
 * decrypts here), and, one per message sent, must not push the Welcome and created-locally
 * markers out of the capped marker file.
 */
class FileIngestDedupStoreTest {
    private val root: Path = platformTempDirectory / "FileIngestDedupStoreTest-${Random.nextLong().toULong()}"

    @AfterTest
    fun cleanUp() {
        platformFileSystem.deleteRecursivelyQuietly(root)
    }

    private fun newFolder(name: String): Path = (root / name).also { platformFileSystem.createDirectories(it) }

    private fun id(n: Int) = n.toString(16).padStart(64, '0')

    @Test
    fun ownEchoesSurviveARestartBoundedAndApartFromTheMarkers() =
        runTest {
            val dir = newFolder("account")
            val welcome = "f".repeat(64)
            val store = FileIngestDedupStore(dir, maxEntries = 100)
            store.mark(welcome)
            val sent = 3 * MarmotIngestDedupStore.OWN_ECHO_LIMIT
            for (n in 0 until sent) store.markOwnEcho(id(n))

            val restarted = FileIngestDedupStore(dir, maxEntries = 100)
            val echoes = restarted.loadOwnEchoes()
            assertEquals(MarmotIngestDedupStore.OWN_ECHO_LIMIT, echoes.size)
            assertEquals(id(sent - 1), echoes.last(), "newest last")
            assertEquals(id(sent - MarmotIngestDedupStore.OWN_ECHO_LIMIT), echoes.first(), "the newest ones are kept")
            assertEquals(setOf(welcome), restarted.loadAll(), "the Welcome marker is still there, and no echo is a marker")
        }

    @Test
    fun noEchoesReadAsEmpty() =
        runTest {
            assertTrue(FileIngestDedupStore(newFolder("empty")).loadOwnEchoes().isEmpty())
        }

    /** The file an earlier install wrote, one id per line, is read as it is. */
    @Test
    fun readsTheFileAnEarlierInstallWrote() =
        runTest {
            val dir = newFolder("legacy")
            platformFileSystem.write(dir / "marmot_ingested.ids") { writeUtf8("${id(1)}\n${id(2)}\n\n") }
            assertEquals(setOf(id(1), id(2)), FileIngestDedupStore(dir).loadAll())
        }
}
