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
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nipB0WebBookmarks.WebBookmarkEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchFieldExtractorTest {
    private val alice = "a1".repeat(32)

    @Test
    fun kind0DecomposesIntoTheProfileRoles() {
        val content = """{"name":"vitor","display_name":"Vitor P","about":"builds nostr","nip05":"vitor@vitorpamplona.com","lud16":"me@wallet.com","website":"https://vitorpamplona.com","picture":"https://x/y.jpg"}"""
        val fields = SearchFieldExtractor.extract(MetadataEvent("1".repeat(64), alice, 1L, emptyArray(), content, ""))
        assertEquals(
            IndexableFields.Profile(
                name = "vitor",
                displayName = "Vitor P",
                about = "builds nostr",
                nip05 = "vitor@vitorpamplona.com",
                lud16 = "me@wallet.com",
                website = "https://vitorpamplona.com",
            ),
            fields,
        )
    }

    @Test
    fun longFormDecomposesIntoTitleSummaryHashtagsContent() {
        val tags = arrayOf(arrayOf("d", "post"), arrayOf("title", "My Post"), arrayOf("summary", "tl;dr"), arrayOf("t", "nostr"), arrayOf("t", "search"))
        val fields = SearchFieldExtractor.extract(LongTextNoteEvent("2".repeat(64), alice, 1L, tags, "the whole article", ""))
        assertEquals(IndexableFields.Tiered(primary = listOf("My Post"), secondary = listOf("tl;dr"), text = "the whole article", hashtags = listOf("nostr", "search")), fields)
    }

    @Test
    fun notesUseTheSubjectAndHashtags() {
        val tags = arrayOf(arrayOf("subject", "meetup"), arrayOf("t", "brazil"))
        val fields = SearchFieldExtractor.extract(TextNoteEvent("3".repeat(64), alice, 1L, tags, "see you there", ""))
        assertEquals(IndexableFields.Tiered(primary = listOf("meetup"), text = "see you there", hashtags = listOf("brazil")), fields)
    }

    @Test
    fun locationTagsAreCarriedRawLikeHashtags() {
        val tags = arrayOf(arrayOf("location", "Rio de Janeiro"))
        val fields = SearchFieldExtractor.extract(TextNoteEvent("4".repeat(64), alice, 1L, tags, "gm", ""))
        assertEquals(IndexableFields.Tiered(text = "gm", locations = listOf("Rio de Janeiro")), fields)
    }

    @Test
    fun torrentsIndexFileNamesAndTrackers() {
        val tags =
            arrayOf(
                arrayOf("title", "Great Torrent"),
                arrayOf("file", "episode1.mkv"),
                arrayOf("file", "episode2.mkv"),
                arrayOf("tracker", "https://tracker.example.com"),
            )
        val fields = SearchFieldExtractor.extract(TorrentEvent("5".repeat(64), alice, 1L, tags, "a series", ""))
        assertEquals(
            IndexableFields.Tiered(
                primary = listOf("Great Torrent"),
                // Unjoined: the backend decides how file names are indexed.
                secondary = listOf("episode1.mkv", "episode2.mkv"),
                text = "a series",
                websites = listOf("https://tracker.example.com"),
            ),
            fields,
        )
    }

    @Test
    fun webBookmarksAreFindableByTheirUrl() {
        // NIP-B0: the d tag carries the URL scheme-less; url() re-adds https://.
        val tags = arrayOf(arrayOf("d", "vitorpamplona.com/post"), arrayOf("title", "A Post"))
        val fields = SearchFieldExtractor.extract(WebBookmarkEvent("6".repeat(64), alice, 1L, tags, "", ""))
        assertEquals(IndexableFields.Tiered(primary = listOf("A Post"), websites = listOf("https://vitorpamplona.com/post")), fields)
    }

    @Test
    fun appHandlerMetadataReusesTheProfileRoles() {
        val content = """{"name":"CoolApp","about":"an app","website":"https://coolapp.example"}"""
        val fields = SearchFieldExtractor.extract(AppDefinitionEvent("7".repeat(64), alice, 1L, arrayOf(arrayOf("d", "x")), content, ""))
        assertEquals(IndexableFields.Profile(name = "CoolApp", about = "an app", website = "https://coolapp.example"), fields)
    }

    @Test
    fun nonSearchableKindsExtractNothing() {
        // Kind 7 reactions are not SearchableEvent.
        val reaction = Event("8".repeat(64), alice, 1L, 7, emptyArray(), "+", "")
        assertEquals(IndexableFields.None, SearchFieldExtractor.extract(reaction))
    }

    @Test
    fun profileShapesNeverGetHashtagFolding() {
        // Hashtags/locations are filled only by the tiers() funnel, which
        // profile branches never use: a kind-0 with t-tags stays a pure
        // profile (and an empty one normalizes to None).
        val tags = arrayOf(arrayOf("t", "nostr"))
        val fields = SearchFieldExtractor.extract(MetadataEvent("9".repeat(64), alice, 1L, tags, "{}", ""))
        assertEquals(IndexableFields.None, fields)
    }
}
