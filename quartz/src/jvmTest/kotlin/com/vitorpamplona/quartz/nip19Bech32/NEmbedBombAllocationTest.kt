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
package com.vitorpamplona.quartz.nip19Bech32

import com.sun.management.ThreadMXBean
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.utils.GZip
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The bound has to stop the inflater, not just reject what it produced: a size check after
 * `readText()` would still allocate the whole bomb. Measures this thread's allocation around the
 * parse to prove the inflated output is never materialised.
 */
class NEmbedBombAllocationTest {
    @Test
    fun bomb_does_not_allocate_its_inflated_size() {
        val compressed = GZip.compress(" ".repeat(50 * 1024 * 1024) + "{}")

        val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean
        val id = Thread.currentThread().id
        val before = threads.getThreadAllocatedBytes(id)
        val parsed = runCatching { NEmbed.parse(compressed) }.getOrNull()
        val allocated = threads.getThreadAllocatedBytes(id) - before

        assertNull(parsed)
        assertTrue(allocated < 8L * 1024 * 1024, "parse allocated $allocated bytes for a ${compressed.size}-byte payload")
    }
}
