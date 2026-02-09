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
package com.vitorpamplona.quartz.nip51Lists.bookmarkList

import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.EventBookmark
import com.vitorpamplona.quartz.utils.nsecToKeyPair
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookmarkListEventTest {
    private val signer = NostrSignerInternal("nsec10g0wheggqn9dawlc0yuv6adnat6n09anr7eyykevw2dm8xa5fffs0wsdsr".nsecToKeyPair())

    @Test
    fun publicTagsPreservedWhenRemovingFromPrivateBookmarks() =
        runTest {
            // Create a test event bookmark
            val testEventId = "a".repeat(64)
            val testBookmark = EventBookmark(testEventId)

            // Create a bookmark list with event in public bookmarks
            val initialEvent =
                BookmarkListEvent.create(
                    publicBookmarks = listOf(testBookmark),
                    privateBookmarks = emptyList(),
                    signer = signer,
                    createdAt = 1740669816,
                )

            // Count the public tags (should be 1 for the bookmark)
            val initialPublicTagCount = initialEvent.tags.count { tag -> tag.size >= 2 && tag[0] == "e" && tag[1] == testEventId }
            assertTrue(
                initialPublicTagCount == 1,
                "Should have exactly 1 public bookmark tag initially",
            )

            // Try to remove the bookmark from private bookmarks (even though it's only in public)
            // This simulates the scenario where a bookmark exists in both lists
            val updatedEvent =
                BookmarkListEvent.remove(
                    earlierVersion = initialEvent,
                    bookmarkIdTag = testBookmark,
                    isPrivate = true,
                    signer = signer,
                    createdAt = 1740669817,
                )

            // Count the public tags again
            val finalPublicTagCount = updatedEvent.tags.count { tag -> tag.size >= 2 && tag[0] == "e" && tag[1] == testEventId }

            // CRITICAL: The public tag count should be unchanged
            // Without the fix, this would be 0 because the bug also removed public tags
            assertTrue(
                finalPublicTagCount == initialPublicTagCount,
                "Public tags should be preserved when removing from private bookmarks. Initial: $initialPublicTagCount, Final: $finalPublicTagCount",
            )

            // Also verify using publicBookmarks() method
            assertTrue(
                updatedEvent.publicBookmarks().any { it is EventBookmark && it.eventId == testEventId },
                "Bookmark should still be accessible via publicBookmarks() method",
            )
        }

    @Test
    fun publicTagsRemovedWhenRemovingFromPublicBookmarks() =
        runTest {
            // Create a test event bookmark
            val testEventId = "b".repeat(64)
            val testBookmark = EventBookmark(testEventId)

            // Create a bookmark list with event only in public bookmarks
            val initialEvent =
                BookmarkListEvent.create(
                    publicBookmarks = listOf(testBookmark),
                    privateBookmarks = emptyList(),
                    signer = signer,
                    createdAt = 1740669816,
                )

            // Remove from public bookmarks
            val updatedEvent =
                BookmarkListEvent.remove(
                    earlierVersion = initialEvent,
                    bookmarkIdTag = testBookmark,
                    isPrivate = false,
                    signer = signer,
                    createdAt = 1740669817,
                )

            // Verify the bookmark was removed from public bookmarks
            val finalPublicTagCount = updatedEvent.tags.count { tag -> tag.size >= 2 && tag[0] == "e" && tag[1] == testEventId }
            assertTrue(
                finalPublicTagCount == 0,
                "Public tags should be removed when isPrivate=false",
            )

            assertFalse(
                updatedEvent.publicBookmarks().any { it is EventBookmark && it.eventId == testEventId },
                "Bookmark should be removed from public bookmarks",
            )
        }
}
