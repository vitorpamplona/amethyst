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
package com.vitorpamplona.quartz.nip72ModCommunities

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommunityCommentScopeTest {
    private fun comment(vararg tags: Array<String>) =
        CommentEvent(
            id = "00".repeat(32),
            pubKey = "11".repeat(32),
            createdAt = 1_700_000_000L,
            tags = arrayOf(*tags),
            content = "hello",
            sig = "22".repeat(64),
        )

    private val communityOwner = "9ca0bd7450742d6a20319c0e3d4c679c9e046a9dc70e8ef55c2905e24052340b"
    private val movies = "34550:$communityOwner:movies"

    /**
     * The report that motivated this: a mostr-bridged top-level post in the "movies" community.
     * It carries the community both as `A`/`a` and -- unusually -- as an `E`/`e` pointing at the
     * definition event's id, with `K`/`k` = 34550.
     */
    private fun bridgedTopLevelPost() =
        comment(
            arrayOf("A", movies, "wss://relay.mostr.pub/"),
            arrayOf("a", movies, "wss://relay.mostr.pub/"),
            arrayOf("E", "575117c37d66a698ddd81169f88fcdab5d5d63687f79da0c5c65f1d72cb99a57", "wss://relay.mostr.pub/", communityOwner),
            arrayOf("K", "34550"),
            arrayOf("P", communityOwner, "wss://nostr.wine/"),
            arrayOf("e", "575117c37d66a698ddd81169f88fcdab5d5d63687f79da0c5c65f1d72cb99a57", "wss://relay.mostr.pub/", communityOwner),
            arrayOf("k", "34550"),
            arrayOf("p", communityOwner, "wss://nostr.wine/"),
        )

    @Test
    fun bridgedTopLevelPostIsRecognisedAsAnswerToTheCommunity() {
        val event = bridgedTopLevelPost()

        assertTrue(event.isCommunityScoped())
        assertTrue(event.isTopLevelCommunityPost())
        assertEquals(movies, (event as Event).communityAddress()?.toValue())
    }

    /**
     * The community must not become a "replying to" card: [tagsWithoutCitations] returning empty
     * is what keeps it out of `Note.replyTo`, and the UI relies on that.
     */
    @Test
    fun bridgedTopLevelPostHasNoReplyTargets() {
        assertEquals(emptyList(), bridgedTopLevelPost().tagsWithoutCitations())
    }

    @Test
    fun topLevelPostWithOnlyUppercaseRootTagsStillCounts() {
        val event =
            comment(
                arrayOf("A", movies),
                arrayOf("K", "34550"),
            )

        assertTrue(event.isTopLevelCommunityPost())
    }

    /**
     * A reply to a post inside a community keeps `K` = 34550 but points `k` at the parent's kind,
     * so it must still resolve to a real parent note rather than to the community.
     */
    @Test
    fun nestedReplyInsideACommunityIsNotATopLevelPost() {
        val parentId = "33".repeat(32)
        val event =
            comment(
                arrayOf("A", movies),
                arrayOf("K", "34550"),
                arrayOf("e", parentId),
                arrayOf("k", "1111"),
            )

        assertTrue(event.isCommunityScoped())
        assertFalse(event.isTopLevelCommunityPost())
        assertTrue(event.tagsWithoutCitations().contains(parentId))
    }

    /**
     * A nested reply that omits the parent kind (`k`) entirely -- malformed, but emitted in the
     * wild. It must not be mistaken for a top-level post just because the root kind says 34550:
     * it names a parent *event*, so it answers a post inside the community, not the community.
     */
    @Test
    fun nestedReplyWithoutAParentKindIsNotATopLevelPost() {
        val event =
            comment(
                arrayOf("A", movies),
                arrayOf("K", "34550"),
                arrayOf("e", "33".repeat(32)),
            )

        assertTrue(event.isCommunityScoped())
        assertFalse(event.isTopLevelCommunityPost())
    }

    /** The mirror case: no `k`, but the parent *address* is the community, so it is top level. */
    @Test
    fun postNamingTheCommunityAsItsParentAddressIsATopLevelPost() {
        val event =
            comment(
                arrayOf("A", movies),
                arrayOf("a", movies),
                arrayOf("K", "34550"),
            )

        assertTrue(event.isTopLevelCommunityPost())
    }

    /** A reply whose parent address is some other addressable event is not a top-level post. */
    @Test
    fun replyToANonCommunityAddressIsNotATopLevelPost() {
        val event =
            comment(
                arrayOf("A", movies),
                arrayOf("a", "30023:$communityOwner:some-article"),
                arrayOf("K", "34550"),
            )

        assertFalse(event.isTopLevelCommunityPost())
    }

    /**
     * [communityAddress] was rewritten to stop at the first community root address instead of
     * parsing every `A` tag into a list. This pins it against the original algorithm as a
     * reference oracle -- seven call sites outside this PR depend on it being unchanged.
     */
    @Test
    fun communityAddressMatchesTheOriginalAlgorithmOnEveryShape() {
        val other = "30023:$communityOwner:some-article"
        val secondCommunity = "34550:$communityOwner:films"

        val shapes =
            listOf(
                "no tags at all" to comment(),
                "one community root" to comment(arrayOf("A", movies)),
                "non-community root only" to comment(arrayOf("A", other)),
                "non-community first, community second" to comment(arrayOf("A", other), arrayOf("A", movies)),
                "two communities picks the first" to comment(arrayOf("A", movies), arrayOf("A", secondCommunity)),
                "unparseable root" to comment(arrayOf("A", "not-an-address"), arrayOf("A", movies)),
                "reply address only, no root" to comment(arrayOf("a", movies)),
                "root without a value" to comment(arrayOf("A")),
            )

        shapes.forEach { (name, event) ->
            assertEquals(
                originalCommunityAddress(event)?.toValue(),
                (event as Event).communityAddress()?.toValue(),
                "communityAddress diverged from the original algorithm for: $name",
            )
        }
    }

    /** The pre-rewrite implementation, kept here purely as the oracle for the test above. */
    private fun originalCommunityAddress(event: CommentEvent) =
        event.rootAddress().firstNotNullOfOrNull {
            if (it.kind == CommunityDefinitionEvent.KIND) it else null
        }

    @Test
    fun commentOnSomethingOtherThanACommunityIsNotATopLevelPost() {
        val event =
            comment(
                arrayOf("I", "https://example.com/article"),
                arrayOf("K", "web"),
            )

        assertFalse(event.isCommunityScoped())
        assertFalse(event.isTopLevelCommunityPost())
    }
}
