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
package com.vitorpamplona.amethyst.commons.video.cache

import com.vitorpamplona.amethyst.commons.util.deleteRecursivelyQuietly
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.amethyst.commons.util.platformTempDirectory
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SparseVideoCacheTest {
    private val dir: Path = platformTempDirectory / "SparseVideoCacheTest-${Random.nextLong().toULong()}"
    private var clock = 1000L

    @AfterTest
    fun cleanUp() {
        platformFileSystem.deleteRecursivelyQuietly(dir)
    }

    private fun cache(maxBytes: Long = 1_000_000) = SparseVideoCache(platformFileSystem, dir, maxBytes, now = { clock++ })

    private fun bytes(
        size: Int,
        seed: Int = 0,
    ) = ByteArray(size) { (it + seed).toByte() }

    private fun SparseVideoCache.store(
        url: String,
        offset: Long,
        data: ByteArray,
    ) {
        acquire(url)
        write(url, offset, data)
        release(url)
    }

    @Test
    fun storesSparseBytesAndReadsOnlyStoredRuns() {
        val cache = cache()
        val url = "https://h/a.mp4"
        val data = bytes(3000)
        cache.acquire(url)
        cache.write(url, 0, data.copyOfRange(0, 1000))
        cache.write(url, 2000, data.copyOfRange(2000, 3000))
        assertEquals(1000, cache.runEnd(url, 0))
        assertEquals(2000L, cache.nextCachedStart(url, 1000))
        assertContentEquals(data.copyOfRange(500, 1000), cache.read(url, 500, 4000))
        assertEquals(0, cache.read(url, 1500, 100).size)
        assertContentEquals(data.copyOfRange(2000, 2100), cache.read(url, 2000, 100))
        cache.release(url)
        assertEquals(2000, cache.totalBytes())
    }

    @Test
    fun survivesARestart() {
        val url = "https://h/a.mp4"
        val data = bytes(2000, seed = 7)
        cache().run {
            acquire(url)
            setInfo(url, 5000, "video/mp4")
            write(url, 0, data.copyOfRange(0, 1000))
            write(url, 4000, data.copyOfRange(1000, 2000))
            release(url)
        }
        val reopened = cache()
        assertEquals(5000L, reopened.length(url))
        assertEquals("video/mp4", reopened.mimeType(url))
        assertTrue(reopened.covers(url, 0, 1000))
        assertTrue(reopened.covers(url, 4000, 5000))
        assertContentEquals(data.copyOfRange(1000, 2000), reopened.read(url, 4000, 1000))
        assertEquals(2000, reopened.totalBytes())
    }

    @Test
    fun bytesPastTheLengthAreNotStored() {
        val cache = cache()
        val url = "https://h/a.mp4"
        cache.acquire(url)
        cache.setInfo(url, 100, null)
        cache.write(url, 50, bytes(100))
        cache.release(url)
        assertEquals(100, cache.runEnd(url, 50))
        assertEquals(50, cache.totalBytes())
    }

    @Test
    fun evictsLeastRecentlyUsedButNeverWhatIsInUse() {
        val cache = cache(maxBytes = 2500)
        cache.store("a", 0, bytes(1000))
        cache.store("b", 0, bytes(1000))
        // "a" is opened again, so "b" is now the oldest.
        cache.acquire("a")
        cache.release("a")
        cache.acquire("c")
        cache.write("c", 0, bytes(1000))
        assertEquals(2000, cache.totalBytes())
        assertTrue(cache.covers("a", 0, 1000))
        assertTrue(!cache.covers("b", 0, 1))
        // "c" is in use: writing past the budget removes "a", never "c".
        cache.write("c", 1000, bytes(1000))
        assertTrue(cache.covers("c", 0, 2000))
        assertTrue(!cache.covers("a", 0, 1))
        cache.release("c")
        // Evicted files are gone from disk too.
        assertNull(cache().length("a"))
        assertEquals(2000, cache().totalBytes())
    }

    @Test
    fun aDataFileShorterThanItsIndexIsTrustedOnlyAsFarAsItGoes() {
        val url = "https://h/a.mp4"
        cache().store(url, 0, bytes(1000))
        val dataFile = platformFileSystem.list(dir).single { it.name.endsWith(".data") }
        platformFileSystem.write(dataFile) { write(bytes(400)) }
        val reopened = cache()
        assertEquals(400, reopened.runEnd(url, 0))
        assertEquals(400, reopened.totalBytes())
    }

    @Test
    fun strayFilesAreCleanedUp() {
        platformFileSystem.createDirectories(dir)
        platformFileSystem.write(dir / "orphan.data") { write(bytes(10)) }
        platformFileSystem.write(dir / "junk.meta") { writeUtf8("not an index") }
        platformFileSystem.write(dir / "x.meta.tmp") { writeUtf8("half") }
        assertEquals(0, cache().totalBytes())
        assertEquals(emptyList<Path>(), platformFileSystem.list(dir))
    }
}
