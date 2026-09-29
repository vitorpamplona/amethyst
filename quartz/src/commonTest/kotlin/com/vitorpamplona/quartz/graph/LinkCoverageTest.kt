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
package com.vitorpamplona.quartz.graph

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Rule 5 of the link vocabulary: every class [EventFactory] builds says what its references
 * mean ([LinkProvider]) or that it has none ([LinkFree]). There is no fallback, so a new kind
 * cannot land without that decision. The failure lists the undecided classes by package.
 */
class LinkCoverageTest {
    @Test
    fun everyEventClassDecidesItsLinks() {
        // The factory picks a class by kind, and for kind 20001 also by the presence of an `h`
        // (NIP-29) or `g` (geohash) tag, so probe each kind with each of those shapes too.
        val probes = listOf(emptyArray(), arrayOf(arrayOf("h", "group")), arrayOf(arrayOf("g", "u4pr")))
        val undecided = sortedSetOf<String>()
        var classes = 0
        val seen = HashSet<String>()
        for (kind in 0..65535) {
            for (tags in probes) {
                val event = EventFactory.create<Event>("", "", 0L, kind, tags, "", "")
                if (event::class == Event::class) continue
                val name = event::class.qualifiedName ?: continue
                if (!seen.add(name)) continue
                classes++
                if (event !is LinkProvider) undecided += "${name.removePrefix("com.vitorpamplona.quartz.")} ($kind)"
            }
        }
        assertTrue(classes > 400, "the probe found only $classes classes")
        assertTrue(undecided.isEmpty(), "${undecided.size} of $classes classes do not decide their links:\n" + undecided.joinToString("\n"))
    }
}
