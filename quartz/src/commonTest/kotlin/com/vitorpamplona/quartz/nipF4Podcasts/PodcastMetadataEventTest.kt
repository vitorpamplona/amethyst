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

import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.tags.AuthorTag
import com.vitorpamplona.quartz.utils.DeterministicSigner
import com.vitorpamplona.quartz.utils.nsecToKeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PodcastMetadataEventTest {
    private val signer =
        DeterministicSigner(
            "nsec10g0wheggqn9dawlc0yuv6adnat6n09anr7eyykevw2dm8xa5fffs0wsdsr".nsecToKeyPair(),
        )

    private val authorPubKey1 = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
    private val authorPubKey2 = "32e1827635450ebb3c5a7d12c1f8e7b2b514439ac10a67eef3d9fd9c5c68e245"

    @Test
    fun `kind is 10154`() {
        assertEquals(10154, PodcastMetadataEvent.KIND)
    }

    @Test
    fun `build round-trips required fields`() {
        val template =
            PodcastMetadataEvent.build(
                title = "Show Title",
                image = "https://example.com/cover.png",
                description = "A show about things.",
            )
        val event = signer.sign<PodcastMetadataEvent>(template)

        assertEquals("Show Title", event.title())
        assertEquals("https://example.com/cover.png", event.image())
        assertEquals("A show about things.", event.description())
        assertTrue(event.websites().isEmpty())
        assertTrue(event.claimedAuthors().isEmpty())
    }

    @Test
    fun `build round-trips multiple websites and authors with roles`() {
        val template =
            PodcastMetadataEvent.build(
                title = "Show",
                image = "https://example.com/cover.png",
                description = "Desc",
                websites = listOf("https://show.example", "https://show.example/feed"),
                authors =
                    listOf(
                        AuthorTag(authorPubKey1, AuthorTag.ROLE_HOST),
                        AuthorTag(authorPubKey2, AuthorTag.ROLE_COHOST),
                    ),
            )
        val event = signer.sign<PodcastMetadataEvent>(template)

        assertEquals(
            listOf("https://show.example", "https://show.example/feed"),
            event.websites(),
        )

        val authors = event.claimedAuthors()
        assertEquals(2, authors.size)
        assertEquals(authorPubKey1, authors[0].pubKey)
        assertEquals(AuthorTag.ROLE_HOST, authors[0].role)
        assertEquals(authorPubKey2, authors[1].pubKey)
        assertEquals(AuthorTag.ROLE_COHOST, authors[1].role)
    }

    @Test
    fun `author tag without role parses with null role`() {
        val template =
            PodcastMetadataEvent.build(
                title = "Show",
                image = "https://example.com/cover.png",
                description = "Desc",
                authors = listOf(AuthorTag(authorPubKey1, null)),
            )
        val event = signer.sign<PodcastMetadataEvent>(template)

        val authors = event.claimedAuthors()
        assertEquals(1, authors.size)
        assertEquals(authorPubKey1, authors[0].pubKey)
        assertNull(authors[0].role)
    }

    @Test
    fun `alt tag is emitted`() {
        val template =
            PodcastMetadataEvent.build(
                title = "Show",
                image = "https://example.com/cover.png",
                description = "Desc",
            )
        val event = signer.sign<PodcastMetadataEvent>(template)

        assertTrue(event.tags.any { it[0] == "alt" && it[1] == PodcastMetadataEvent.ALT_DESCRIPTION })
    }

    @Test
    fun `is replaceable with empty d-tag`() {
        val template =
            PodcastMetadataEvent.build(
                title = "Show",
                image = "https://example.com/cover.png",
                description = "Desc",
            )
        val event = signer.sign<PodcastMetadataEvent>(template)

        assertEquals("", event.dTag())
        assertEquals(10154, event.address().kind)
        assertEquals(event.pubKey, event.address().pubKeyHex)
    }
}
