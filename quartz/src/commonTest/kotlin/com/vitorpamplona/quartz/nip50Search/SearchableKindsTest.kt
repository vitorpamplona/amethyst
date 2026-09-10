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
package com.vitorpamplona.quartz.nip50Search

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [SearchableKinds.ALL] against the only authority there is: the factory.
 *
 * The list is written out because nothing in common code can walk a `when`, and a list written
 * out is a list that goes stale. So it is checked the expensive way — every kind in the 16-bit
 * space is built and asked whether it came back searchable — which costs one sweep in the test
 * suite and buys the guarantee that the recorded set is never a guess.
 *
 * A new [SearchableEvent] in Quartz therefore fails here, naming its kind, rather than quietly
 * being left out of everything downstream that reads this list.
 */
class SearchableKindsTest {
    private fun build(kind: Int): Event = EventFactory.create("9".repeat(64), "a".repeat(64), 1L, kind, emptyArray(), "", "")

    @Test
    fun theRecordedSetIsExactlyWhatTheFactoryBuilds() {
        val actual = (0..65535).filter { build(it) is SearchableEvent }
        assertEquals(
            actual,
            SearchableKinds.ALL,
            "SearchableKinds.ALL is stale. Missing: ${actual - SearchableKinds.ALL.toSet()}; " +
                "no longer searchable: ${SearchableKinds.ALL - actual.toSet()}.",
        )
    }

    @Test
    fun theListIsSortedAndHasNoDuplicates() {
        // Read as a range in review and bisected by eye when a number is looked up; both need it
        // ordered, and neither notices a repeat.
        assertEquals(SearchableKinds.ALL.sorted(), SearchableKinds.ALL)
        assertEquals(SearchableKinds.ALL.distinct(), SearchableKinds.ALL)
    }
}
