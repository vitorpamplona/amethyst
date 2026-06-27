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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.Podcasting20EpisodeEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.edit
import com.vitorpamplona.quartz.podcasts.PodcastAudio
import com.vitorpamplona.quartz.utils.DeterministicSigner
import com.vitorpamplona.quartz.utils.nsecToKeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Podcasting20EpisodeEventTest {
    private val signer =
        DeterministicSigner(
            "nsec10g0wheggqn9dawlc0yuv6adnat6n09anr7eyykevw2dm8xa5fffs0wsdsr".nsecToKeyPair(),
        )

    @Test
    fun `kind is 30054`() {
        assertEquals(30054, Podcasting20EpisodeEvent.KIND)
    }

    @Test
    fun `build round-trips fields and is addressable by d tag`() {
        val template =
            Podcasting20EpisodeEvent.build(
                dTag = "episode-1699123456-abc123def",
                title = "The Future of Decentralized Social Media",
                audios = listOf(PodcastAudio("https://example.com/episodes/episode-001.mp3", "audio/mpeg")),
                pubdate = "Thu, 04 Nov 2023 12:00:00 GMT",
                description = "A deep dive into how protocols like Nostr are changing social media",
                image = "https://example.com/artwork/episode-001.jpg",
                durationInSeconds = 3600,
                topics = listOf("technology", "decentralization"),
                markdownContent = "In this episode, we discuss decentralized social media.",
            )
        val event = signer.sign<Podcasting20EpisodeEvent>(template)

        assertEquals("episode-1699123456-abc123def", event.dTag())
        assertEquals("The Future of Decentralized Social Media", event.title())
        assertEquals("A deep dive into how protocols like Nostr are changing social media", event.description())
        assertEquals("https://example.com/artwork/episode-001.jpg", event.image())
        assertEquals(3600L, event.durationInSeconds())
        assertEquals("Thu, 04 Nov 2023 12:00:00 GMT", event.pubDate())
        assertEquals("In this episode, we discuss decentralized social media.", event.content)

        val audios = event.audios()
        assertEquals(1, audios.size)
        assertEquals("https://example.com/episodes/episode-001.mp3", audios[0].url)
        assertEquals("audio/mpeg", audios[0].mediaType)

        assertTrue(event.topics().containsAll(listOf("technology", "decentralization")))
        assertEquals("Podcast episode: The Future of Decentralized Social Media", event.alt())
    }

    @Test
    fun `optional fields default to null`() {
        val template =
            Podcasting20EpisodeEvent.build(
                dTag = "ep-2",
                title = "No extras",
                audios = listOf(PodcastAudio("https://example.com/ep2.mp3")),
                pubdate = "Thu, 04 Nov 2023 12:00:00 GMT",
            )
        val event = signer.sign<Podcasting20EpisodeEvent>(template)

        assertNull(event.image())
        assertNull(event.description())
        assertNull(event.durationInSeconds())
        assertNull(event.editsEventId())
    }

    @Test
    fun `edit tag tracks the original event id`() {
        val template =
            Podcasting20EpisodeEvent.build(
                dTag = "ep-3",
                title = "Corrected",
                audios = listOf(PodcastAudio("https://example.com/ep3.mp3")),
                pubdate = "Thu, 04 Nov 2023 12:00:00 GMT",
            ) {
                edit("abababababababababababababababababababababababababababababababab")
            }
        val event = signer.sign<Podcasting20EpisodeEvent>(template)

        assertEquals("abababababababababababababababababababababababababababababababab", event.editsEventId())
    }

    @Test
    fun `parses the spec example json into the typed event via EventFactory`() {
        // Verbatim example from derekross/podstr NIP.md (id and sig elided are not
        // required for parsing tags; we supply structurally valid placeholders).
        val json =
            """
            {
              "kind": 30054,
              "content": "In this episode, we discuss the latest developments in decentralized social media protocols.",
              "tags": [
                ["d", "episode-1699123456-abc123def"],
                ["title", "The Future of Decentralized Social Media"],
                ["audio", "https://example.com/episodes/episode-001.mp3", "audio/mpeg"],
                ["pubdate", "Thu, 04 Nov 2023 12:00:00 GMT"],
                ["alt", "Podcast episode: The Future of Decentralized Social Media"],
                ["description", "A deep dive into how protocols like Nostr are changing social media"],
                ["image", "https://example.com/artwork/episode-001.jpg"],
                ["duration", "3600"],
                ["t", "technology"],
                ["t", "decentralization"],
                ["t", "social-media"]
              ],
              "created_at": 1699123456,
              "pubkey": "0000000000000000000000000000000000000000000000000000000000000001",
              "id": "0000000000000000000000000000000000000000000000000000000000000002",
              "sig": "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
            }
            """.trimIndent()

        val event = Event.fromJson(json)
        assertTrue(event is Podcasting20EpisodeEvent)
        assertEquals("The Future of Decentralized Social Media", event.title())
        assertEquals(3600L, event.durationInSeconds())
        assertEquals(3, event.topics().size)
        assertEquals("https://example.com/episodes/episode-001.mp3", event.audios()[0].url)
    }
}
