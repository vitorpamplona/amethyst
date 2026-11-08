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
package com.vitorpamplona.amethyst.commons.relayClient.eoseManagers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedOwnerTrackerTest {
    @Test
    fun `the first feed for a key keeps the cursors`() {
        val tracker = FeedOwnerTracker<String>()

        assertFalse(tracker.ownerChanged("vitor", Any()))
    }

    @Test
    fun `the same feed again keeps the cursors`() {
        val tracker = FeedOwnerTracker<String>()
        val feed = Any()
        tracker.ownerChanged("vitor", feed)

        // Every invalidation re-reports the owner: that must stay a no-op.
        assertFalse(tracker.ownerChanged("vitor", feed))
        assertFalse(tracker.ownerChanged("vitor", feed))
    }

    @Test
    fun `a new feed for the same key drops the cursors once`() {
        val tracker = FeedOwnerTracker<String>()
        tracker.ownerChanged("vitor", Any())

        // Switching away and back builds a new AccountViewModel and so a new feed.
        val second = Any()
        assertTrue(tracker.ownerChanged("vitor", second))
        assertFalse(tracker.ownerChanged("vitor", second))
    }

    @Test
    fun `keys are tracked apart`() {
        val tracker = FeedOwnerTracker<String>()
        val vitorFeed = Any()
        tracker.ownerChanged("vitor", vitorFeed)

        assertFalse(tracker.ownerChanged("edo", Any()))
        assertFalse(tracker.ownerChanged("vitor", vitorFeed))
    }
}
