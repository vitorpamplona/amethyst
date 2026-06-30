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
package com.vitorpamplona.quartz.nipF4Podcasts

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Verifies the fingerprint used to drop the "Mock Podcast" spam flood, and that it spares real shows. */
class PodcastMockSpamTest {
    private fun event(
        title: String?,
        description: String?,
        content: String,
    ): PodcastMetadataEvent {
        val tags =
            buildList {
                title?.let { add(arrayOf("title", it)) }
                description?.let { add(arrayOf("description", it)) }
            }.toTypedArray()
        return PodcastMetadataEvent(EMPTY_ID, EMPTY_ID, 0, tags, content, EMPTY_ID)
    }

    @Test
    fun `the exact mock spam structure matches`() {
        assertTrue(event("Mock Podcast", "Headless test feed", "Headless test feed").isMockSpam())
    }

    @Test
    fun `real shows are not flagged`() {
        // A genuine show that happens to share none of the three fields.
        assertFalse(event("My Real Podcast", "A show about things", "Welcome!").isMockSpam())
        // Same title only — not enough.
        assertFalse(event("Mock Podcast", "A different description", "Real content").isMockSpam())
        // Same content only — not enough.
        assertFalse(event("Another Show", "Another desc", "Headless test feed").isMockSpam())
        // Missing tags entirely.
        assertFalse(event(null, null, "Headless test feed").isMockSpam())
    }

    companion object {
        private val EMPTY_ID: HexKey = "0".repeat(64)
    }
}
