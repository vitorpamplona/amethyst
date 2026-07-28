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
package com.vitorpamplona.amethyst.desktop.filters

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.feeds.DesktopMutualFeedFilter
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopMutualFeedFilterTest {
    private val me = "0000000000000000000000000000000000000000000000000000000000000001"
    private val profile = "0000000000000000000000000000000000000000000000000000000000000002"
    private val other = "0000000000000000000000000000000000000000000000000000000000000003"
    private val cache = DesktopLocalCache()

    private fun user(hex: String): User = User(hex) { addr -> Note(addr.toValue()) }

    private fun note(
        id: String,
        pubkey: String,
        tags: Array<Array<String>>,
    ): Note {
        val event = TextNoteEvent(id, pubkey, 0L, tags, "hi", "")
        val n = Note(id)
        n.loadEvent(event, user(pubkey), emptyList())
        return n
    }

    @Test
    fun includesMyNoteTaggingProfile() {
        val filter = DesktopMutualFeedFilter(me, profile, cache)
        val n = note("aa", me, arrayOf(arrayOf("p", profile)))
        assertEquals(setOf(n), filter.applyFilter(setOf(n)))
    }

    @Test
    fun excludesMyNoteNotTaggingProfile() {
        val filter = DesktopMutualFeedFilter(me, profile, cache)
        val n = note("bb", me, arrayOf(arrayOf("p", other)))
        assertTrue("Note that doesn't tag the profile must be excluded", filter.applyFilter(setOf(n)).isEmpty())
    }

    @Test
    fun excludesOtherAuthorTaggingProfile() {
        val filter = DesktopMutualFeedFilter(me, profile, cache)
        val n = note("cc", other, arrayOf(arrayOf("p", profile)))
        assertTrue("Note not authored by me must be excluded", filter.applyFilter(setOf(n)).isEmpty())
    }
}
