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
package com.vitorpamplona.quartz.podcasts

import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import com.vitorpamplona.quartz.nipF4Podcasts.episode.tags.AudioTag
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.Podcasting20EpisodeEvent
import com.vitorpamplona.quartz.utils.DeterministicSigner
import com.vitorpamplona.quartz.utils.nsecToKeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Proves the merge: a NIP-F4 episode (`kind:54`) and a Podcasting-2.0 episode
 * (`kind:30054`) — built on different identity models and event kinds — flow into
 * a single `List<PodcastEpisode>` and expose the same fields through the shared
 * abstraction.
 */
class UnifiedPodcastEpisodeTest {
    private val signer =
        DeterministicSigner(
            "nsec10g0wheggqn9dawlc0yuv6adnat6n09anr7eyykevw2dm8xa5fffs0wsdsr".nsecToKeyPair(),
        )

    @Test
    fun `both kinds satisfy the shared PodcastEpisode abstraction`() {
        val f4 =
            signer.sign<PodcastEpisodeEvent>(
                PodcastEpisodeEvent.build(
                    title = "F4 Episode",
                    description = "Published under NIP-F4",
                    audios = listOf(AudioTag("https://example.com/f4.mp3", "audio/mpeg")),
                    image = "https://example.com/f4.png",
                    createdAt = 1000,
                ),
            )

        val pc20 =
            signer.sign<Podcasting20EpisodeEvent>(
                Podcasting20EpisodeEvent.build(
                    dTag = "ep-1",
                    title = "Podcasting 2.0 Episode",
                    audios = listOf(PodcastAudio("https://example.com/pc20.mp3", "audio/mpeg")),
                    pubdate = "Thu, 04 Nov 2023 12:00:00 GMT",
                    description = "Published under kind 30054",
                    image = "https://example.com/pc20.png",
                    durationInSeconds = 1800,
                    createdAt = 2000,
                ),
            )

        // The whole point: one list, mixed kinds, ordered newest-first.
        val unified: List<PodcastEpisode> = listOf(f4, pc20).sortedByDescending { it.episodePublishedAt() }

        assertEquals(listOf("Podcasting 2.0 Episode", "F4 Episode"), unified.map { it.episodeTitle() })

        unified.forEach { episode ->
            assertEquals("audio/mpeg", episode.episodeAudio().single().mediaType)
        }

        // F4 has no duration tag; the Podcasting-2.0 episode carries one.
        val byTitle = unified.associateBy { it.episodeTitle() }
        assertNull(byTitle["F4 Episode"]!!.episodeDurationInSeconds())
        assertEquals(1800L, byTitle["Podcasting 2.0 Episode"]!!.episodeDurationInSeconds())

        assertEquals(1000L, byTitle["F4 Episode"]!!.episodePublishedAt())
        assertEquals(2000L, byTitle["Podcasting 2.0 Episode"]!!.episodePublishedAt())
    }
}
