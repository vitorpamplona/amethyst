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
package com.vitorpamplona.quartz.nip01Core.relay.client.pool

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FiltersChangedTest {
    private fun authors(vararg a: String) = Filter(authors = a.toList())

    @Test
    fun emptyListsAreUnchanged() {
        assertFalse(FiltersChanged.needsToResendRequest(emptyList(), emptyList()))
    }

    @Test
    fun differentSizesNeedResend() {
        assertTrue(FiltersChanged.needsToResendRequest(listOf(authors("a")), listOf(authors("a"), authors("b"))))
    }

    @Test
    fun identicalSingleFilterDoesNotNeedResend() {
        assertFalse(FiltersChanged.needsToResendRequest(listOf(authors("a")), listOf(authors("a"))))
    }

    @Test
    fun changedFirstFilterNeedsResend() {
        assertTrue(FiltersChanged.needsToResendRequest(listOf(authors("a")), listOf(authors("b"))))
    }

    /**
     * Regression: the loop used a non-local `return` on the first iteration, so only
     * filters[0] was ever compared. A subscription whose first filter was unchanged
     * reported "no resend needed" no matter what happened to the rest, and silently
     * went stale — the relay kept serving the old filter set.
     */
    @Test
    fun changedSecondFilterNeedsResend() {
        val old = listOf(authors("a"), authors("b"))
        val new = listOf(authors("a"), authors("CHANGED"))
        assertTrue(FiltersChanged.needsToResendRequest(old, new))
    }

    @Test
    fun changedLastOfManyNeedsResend() {
        val old = listOf(authors("a"), authors("b"), authors("c"), authors("d"))
        val new = listOf(authors("a"), authors("b"), authors("c"), authors("CHANGED"))
        assertTrue(FiltersChanged.needsToResendRequest(old, new))
    }

    @Test
    fun allIdenticalOfManyDoesNotNeedResend() {
        val old = listOf(authors("a"), authors("b"), authors("c"))
        val new = listOf(authors("a"), authors("b"), authors("c"))
        assertFalse(FiltersChanged.needsToResendRequest(old, new))
    }

    /** `since` moving forward is deliberately NOT a resend trigger, on any index. */
    @Test
    fun sinceMovingForwardOnLaterFilterDoesNotNeedResend() {
        val old = listOf(Filter(authors = listOf("a"), since = 100), Filter(authors = listOf("b"), since = 100))
        val new = listOf(Filter(authors = listOf("a"), since = 100), Filter(authors = listOf("b"), since = 200))
        assertFalse(FiltersChanged.needsToResendRequest(old, new))
    }

    /** ...but moving backwards in time is, including on a later filter. */
    @Test
    fun sinceMovingBackwardsOnLaterFilterNeedsResend() {
        val old = listOf(Filter(authors = listOf("a"), since = 100), Filter(authors = listOf("b"), since = 200))
        val new = listOf(Filter(authors = listOf("a"), since = 100), Filter(authors = listOf("b"), since = 100))
        assertTrue(FiltersChanged.needsToResendRequest(old, new))
    }
}
