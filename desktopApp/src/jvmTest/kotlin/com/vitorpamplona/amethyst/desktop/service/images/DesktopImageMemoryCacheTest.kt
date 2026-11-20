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
package com.vitorpamplona.amethyst.desktop.service.images

import coil3.asImage
import coil3.memory.MemoryCache
import org.jetbrains.skia.Bitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The desktop's decoded-image cache must keep an image nothing on screen holds any more: that is
 * the image a feed scrolled past, or the one its prefetch decoded ahead.
 */
class DesktopImageMemoryCacheTest {
    private val heap = 1024L * 1024 * 1024

    @Test
    fun keepsAnImageNothingElseHoldsAcrossGarbageCollections() {
        val cache = DesktopImageLoaderSetup.newMemoryCache(heap)
        val key = MemoryCache.Key("https://example.com/a.jpg")
        putImage(cache, key, 200, 200)

        repeat(3) {
            System.gc()
            Thread.sleep(50)
        }

        assertNotNull(cache[key], "the image was dropped once nothing else held it")
    }

    @Test
    fun staysWithinItsBudget() {
        val cache = DesktopImageLoaderSetup.newMemoryCache(heap)
        val budget = DesktopImageLoaderSetup.memoryCacheBytes(heap)
        // 1024 x 1024 x 4 bytes = 4 MiB each.
        val count = (budget / (4L * 1024 * 1024)).toInt() + 8
        repeat(count) { putImage(cache, MemoryCache.Key("https://example.com/$it.jpg"), 1024, 1024) }

        assertEquals(budget, cache.maxSize)
        assertTrue(cache.size <= budget, "${cache.size} bytes held, over the $budget budget")
        assertNotNull(cache[MemoryCache.Key("https://example.com/${count - 1}.jpg")])
    }

    @Test
    fun budgetIsFifteenPercentOfTheHeapUpTo256MiB() {
        // A 1 GiB heap: 15% of it, about 153 MiB.
        assertEquals(161_061_273L, DesktopImageLoaderSetup.memoryCacheBytes(1024L * 1024 * 1024))
        // The packaged app's 2 GiB heap and anything bigger: the 256 MiB ceiling.
        assertEquals(256L * 1024 * 1024, DesktopImageLoaderSetup.memoryCacheBytes(2L * 1024 * 1024 * 1024))
        assertEquals(256L * 1024 * 1024, DesktopImageLoaderSetup.memoryCacheBytes(16L * 1024 * 1024 * 1024))
    }

    // Its own function, so no local of the test keeps the image reachable.
    private fun putImage(
        cache: MemoryCache,
        key: MemoryCache.Key,
        width: Int,
        height: Int,
    ) {
        val bitmap = Bitmap().apply { allocN32Pixels(width, height) }
        cache[key] = MemoryCache.Value(bitmap.asImage())
    }
}
