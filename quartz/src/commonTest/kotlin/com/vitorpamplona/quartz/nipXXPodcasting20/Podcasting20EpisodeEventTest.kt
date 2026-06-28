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
import com.vitorpamplona.quartz.podcasts.PodcastEpisode
import com.vitorpamplona.quartz.podcasts.PodcastValue
import com.vitorpamplona.quartz.podcasts.PodcastValueRecipient
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
        assertNull(event.video())
        assertNull(event.number())
        assertNull(event.season())
        assertNull(event.transcriptUrl())
        assertNull(event.chaptersUrl())
        assertNull(event.value())
    }

    @Test
    fun `value-for-value split round-trips through the value tag and the interface`() {
        val value =
            PodcastValue(
                enabled = true,
                amount = 1000,
                currency = "sat",
                recipients =
                    listOf(
                        PodcastValueRecipient(name = "Host", type = "lnaddress", address = "host@ln.tips", split = 90),
                        PodcastValueRecipient(name = "Producer", type = "node", address = "02abcd", split = 10, fee = true),
                    ),
            )
        val episode: PodcastEpisode =
            signer.sign<Podcasting20EpisodeEvent>(
                Podcasting20EpisodeEvent.build(
                    dTag = "ep-v4v",
                    title = "V4V",
                    audios = listOf(PodcastAudio("https://x/a.mp3")),
                    pubdate = "Thu, 04 Nov 2023 12:00:00 GMT",
                    value = value,
                ),
            )

        val parsed = episode.episodeValue()
        assertTrue(parsed != null)
        assertEquals(true, parsed.enabled)
        assertEquals("sat", parsed.currency)
        assertEquals(100, parsed.totalSplit())
        assertEquals(2, parsed.recipients.size)
        assertEquals("Host", parsed.recipients[0].name)
        assertEquals("lnaddress", parsed.recipients[0].type)
        assertEquals("host@ln.tips", parsed.recipients[0].address)
        assertEquals(90, parsed.recipients[0].split)
        assertEquals(true, parsed.recipients[1].fee)
    }

    @Test
    fun `rich Podcasting 2 point 0 tags round-trip and surface through the interface`() {
        val template =
            Podcasting20EpisodeEvent.build(
                dTag = "ep-rich",
                title = "Rich Episode",
                audios = listOf(PodcastAudio("https://example.com/ep.mp3", "audio/mpeg")),
                pubdate = "Thu, 04 Nov 2023 12:00:00 GMT",
                video = PodcastAudio("https://example.com/ep.mp4", "video/mp4"),
                episodeNumber = 5,
                season = 2,
                transcriptUrl = "https://example.com/ep.srt",
                chaptersUrl = "https://example.com/ep.chapters.json",
            )
        val episode: PodcastEpisode = signer.sign<Podcasting20EpisodeEvent>(template)

        assertEquals("https://example.com/ep.mp4", episode.episodeVideo()?.url)
        assertEquals("video/mp4", episode.episodeVideo()?.mediaType)
        assertEquals(5, episode.episodeNumber())
        assertEquals(2, episode.episodeSeason())
        assertEquals("https://example.com/ep.srt", episode.episodeTranscriptUrl())
        assertEquals("https://example.com/ep.chapters.json", episode.episodeChaptersUrl())
        // Audio still comes through independently of the video source.
        assertEquals("https://example.com/ep.mp3", episode.episodeAudio().single().url)
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
