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

import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import com.vitorpamplona.quartz.nipF4Podcasts.episode.tags.AudioTag
import com.vitorpamplona.quartz.utils.DeterministicSigner
import com.vitorpamplona.quartz.utils.nsecToKeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PodcastEpisodeEventTest {
    private val signer =
        DeterministicSigner(
            "nsec10g0wheggqn9dawlc0yuv6adnat6n09anr7eyykevw2dm8xa5fffs0wsdsr".nsecToKeyPair(),
        )

    @Test
    fun `kind is 54`() {
        assertEquals(54, PodcastEpisodeEvent.KIND)
    }

    @Test
    fun `build round-trips fields and content`() {
        val markdown = "# Episode notes\n\nSome **bold** text."
        val template =
            PodcastEpisodeEvent.build(
                title = "Ep 1",
                description = "First episode",
                audios = listOf(AudioTag("https://example.com/ep1.mp3", "audio/mpeg")),
                markdownContent = markdown,
                image = "https://example.com/ep1.png",
            )
        val event = signer.sign<PodcastEpisodeEvent>(template)

        assertEquals("Ep 1", event.title())
        assertEquals("First episode", event.description())
        assertEquals("https://example.com/ep1.png", event.image())
        assertEquals(markdown, event.content)

        val audios = event.audios()
        assertEquals(1, audios.size)
        assertEquals("https://example.com/ep1.mp3", audios[0].url)
        assertEquals("audio/mpeg", audios[0].mediaType)
    }

    @Test
    fun `image is optional`() {
        val template =
            PodcastEpisodeEvent.build(
                title = "Ep 2",
                description = "No artwork",
                audios = listOf(AudioTag("https://example.com/ep2.mp3")),
            )
        val event = signer.sign<PodcastEpisodeEvent>(template)

        assertNull(event.image())
    }

    @Test
    fun `multiple audio tags are preserved with optional media type`() {
        val template =
            PodcastEpisodeEvent.build(
                title = "Ep 3",
                description = "Multi-codec",
                audios =
                    listOf(
                        AudioTag("https://example.com/ep3.mp3", "audio/mpeg"),
                        AudioTag("https://example.com/ep3.opus", "audio/opus"),
                        AudioTag("https://example.com/ep3.unknown"),
                    ),
            )
        val event = signer.sign<PodcastEpisodeEvent>(template)

        val audios = event.audios()
        assertEquals(3, audios.size)
        assertEquals("audio/mpeg", audios[0].mediaType)
        assertEquals("audio/opus", audios[1].mediaType)
        assertNull(audios[2].mediaType)
    }

    @Test
    fun `alt tag includes title`() {
        val template =
            PodcastEpisodeEvent.build(
                title = "Ep Title",
                description = "x",
                audios = listOf(AudioTag("https://example.com/x.mp3")),
            )
        val event = signer.sign<PodcastEpisodeEvent>(template)

        val alt = event.tags.first { it[0] == "alt" }
        assertTrue(alt[1].endsWith("Ep Title"))
        assertTrue(alt[1].startsWith(PodcastEpisodeEvent.ALT_DESCRIPTION_PREFIX))
    }
}
