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
package com.vitorpamplona.quartz.nipXXPodcasting20

import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.metadata.Podcasting20PodcastMetadata
import com.vitorpamplona.quartz.nipXXPodcasting20.metadata.isPodcastShowEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.metadata.resolvePodcastShow
import com.vitorpamplona.quartz.podcasts.PodcastShow
import com.vitorpamplona.quartz.utils.DeterministicSigner
import com.vitorpamplona.quartz.utils.nsecToKeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Podcasting20PodcastMetadataTest {
    private val signer =
        DeterministicSigner(
            "nsec10g0wheggqn9dawlc0yuv6adnat6n09anr7eyykevw2dm8xa5fffs0wsdsr".nsecToKeyPair(),
        )

    // Verbatim content shape from derekross/podstr (kind 30078, d="podcast-metadata").
    private val metadataJson =
        """
        {
          "title": "My Podcast",
          "description": "A podcast about interesting topics",
          "author": "John Doe",
          "email": "john@example.com",
          "image": "https://example.com/artwork.jpg",
          "language": "en",
          "categories": ["Technology", "Science"],
          "explicit": false,
          "website": "https://example.com",
          "copyright": "© 2025 John Doe",
          "funding": ["https://example.com/donate", "https://example.com/tip"],
          "locked": false,
          "value": { "amount": 100000, "currency": "sat" },
          "type": "episodic",
          "complete": true,
          "guid": "abc-123"
        }
        """.trimIndent()

    private fun appDataEvent(
        dTag: String,
        content: String,
    ): AppSpecificDataEvent = signer.sign<AppSpecificDataEvent>(AppSpecificDataEvent.build(dTag = dTag, description = content))

    @Test
    fun `parses podcast-metadata json into PodcastShow fields`() {
        val event = appDataEvent("podcast-metadata", metadataJson)
        val show = Podcasting20PodcastMetadata.parse(event)

        assertTrue(show != null)
        assertEquals("My Podcast", show.showTitle())
        assertEquals("A podcast about interesting topics", show.showDescription())
        assertEquals("https://example.com/artwork.jpg", show.showImage())
        assertEquals(listOf("https://example.com"), show.showWebsites())
        assertEquals("John Doe", show.showAuthor())
        assertEquals(listOf("Technology", "Science"), show.showCategories())
        assertEquals(listOf("https://example.com/donate", "https://example.com/tip"), show.showFundingUrls())
        assertEquals("© 2025 John Doe", show.showCopyright())
        assertFalse(show.showIsExplicit())
        assertTrue(show.showIsComplete())
        assertEquals("en", show.language())
        assertEquals("john@example.com", show.email())
        assertEquals("episodic", show.type())
        assertEquals("abc-123", show.guid())
        assertFalse(show.isLocked())
    }

    @Test
    fun `optional rich fields default to empty or null when absent`() {
        val event = appDataEvent("podcast-metadata", """{"title":"Bare","description":"d","image":"i"}""")
        val show = Podcasting20PodcastMetadata.parse(event)

        assertTrue(show != null)
        assertNull(show.showAuthor())
        assertTrue(show.showCategories().isEmpty())
        assertTrue(show.showFundingUrls().isEmpty())
        assertNull(show.showCopyright())
        assertFalse(show.showIsExplicit())
        assertFalse(show.showIsComplete())
    }

    @Test
    fun `ignores app-data events with a different d tag`() {
        val event = appDataEvent("amethyst-settings", metadataJson)
        assertNull(Podcasting20PodcastMetadata.parse(event))
        assertFalse(isPodcastShowEvent(event))
    }

    @Test
    fun `returns null when content is not valid metadata json`() {
        val event = appDataEvent("podcast-metadata", "not-json")
        assertNull(Podcasting20PodcastMetadata.parse(event))
    }

    @Test
    fun `resolver unifies both show kinds into one list`() {
        val f4 =
            signer.sign<PodcastMetadataEvent>(
                PodcastMetadataEvent.build(
                    title = "F4 Show",
                    image = "https://example.com/f4.png",
                    description = "NIP-F4 show",
                    websites = listOf("https://f4.example.com"),
                ),
            )
        val pc20 = appDataEvent("podcast-metadata", metadataJson)

        val shows: List<PodcastShow> = listOf(f4, pc20).mapNotNull { resolvePodcastShow(it) }

        assertEquals(2, shows.size)
        assertEquals(listOf("F4 Show", "My Podcast"), shows.map { it.showTitle() })
        assertTrue(isPodcastShowEvent(f4))
        assertTrue(isPodcastShowEvent(pc20))
    }
}
