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
package com.vitorpamplona.amethyst.commons.mavenReleases

import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.quartz.nipFCMavenRepositories.MavenModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The card's "Version X is out" source follows the cache as releases arrive. */
class MavenLibraryVersionsTest {
    private val module = MavenModule("com.example", "lib")

    @Test
    fun followsNewReleasesAsTheyArrive() =
        runBlocking {
            val cache = EventCache()
            val first = release("1.0.0", id = "11".repeat(32))
            assertTrue(cache.justConsume(first, null, true))

            val flow = MavenLibraryVersions.newest(cache, SIGNER, module)
            withTimeout(5_000) { assertSame(first, flow.first { it != null }) }

            val newer = release("1.2.0", createdAt = 1_700_000_100L, id = "12".repeat(32))
            assertTrue(cache.justConsume(newer, null, true))
            withTimeout(5_000) { assertEquals("1.2.0", flow.first { it?.version() == "1.2.0" }?.version()) }

            // An older version arriving later changes nothing.
            assertTrue(cache.justConsume(release("1.1.0", id = "13".repeat(32)), null, true))
            withTimeout(5_000) { assertSame(newer, flow.first { it != null }) }
        }

    @Test
    fun cardsOfOneLibraryShareOneFlow() {
        val cache = EventCache()
        assertSame(MavenLibraryVersions.newest(cache, SIGNER, module), MavenLibraryVersions.newest(cache, SIGNER, module))
    }
}
