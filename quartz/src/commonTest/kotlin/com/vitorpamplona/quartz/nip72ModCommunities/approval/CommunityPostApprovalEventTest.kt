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
package com.vitorpamplona.quartz.nip72ModCommunities.approval

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommunityPostApprovalEventTest {
    private fun approvalWith(content: String) =
        CommunityPostApprovalEvent(
            id = "00".repeat(32),
            pubKey = "11".repeat(32),
            createdAt = 1_700_000_000L,
            tags = arrayOf(arrayOf("a", "34550:${"11".repeat(32)}:community")),
            content = content,
            sig = "22".repeat(64),
        )

    @Test
    fun containedPostParsesEmbeddedEventJson() {
        val embedded =
            """{"id":"${"33".repeat(32)}","pubkey":"${"44".repeat(32)}","created_at":1700000000,"kind":1,"tags":[],"content":"hello","sig":"${"55".repeat(64)}"}"""

        val post = approvalWith(embedded).containedPost()

        assertEquals("33".repeat(32), post?.id)
        assertEquals(1, post?.kind)
        assertEquals("hello", post?.content)
    }

    @Test
    fun containedPostIgnoresBlankContent() {
        assertNull(approvalWith("").containedPost())
        assertNull(approvalWith("   ").containedPost())
    }

    /**
     * Clients in the wild fill approval contents with plain text (e.g. braille
     * ASCII art) instead of the NIP-72 stringified event. These must be skipped
     * without even attempting a JSON parse.
     */
    @Test
    fun containedPostIgnoresNonJsonContent() {
        assertNull(approvalWith("⠀⠀⠀⠐⣖⠢⢤⣄⡀⠀").containedPost())
        assertNull(approvalWith("Approved by moderator").containedPost())
    }

    /** Valid JSON that isn't a Nostr event (no pubkey field) must return null. */
    @Test
    fun containedPostIgnoresNonEventJson() {
        assertNull(approvalWith("""{"approvedEvent":{"kind":1111},"moderation":{"action":"approve"}}""").containedPost())
    }
}
