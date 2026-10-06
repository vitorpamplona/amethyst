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
package com.vitorpamplona.quartz.nip71Video

import com.vitorpamplona.quartz.nip50Search.IndexableFields
import com.vitorpamplona.quartz.nip50Search.SearchFieldExtractor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoHintProviderTest {
    private val author = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val inspiration = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val collaborator = "7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377"
    private val audioEvent = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val creditedVideo = "34236:$inspiration:original-loop"
    private val subtitles = "39307:$author:subtitles:abc"
    private val relay = "wss://relay.damus.io/"
    private val relay2 = "wss://nos.lol/"

    private val tags =
        arrayOf(
            arrayOf("title", "Sunset loop"),
            arrayOf("p", inspiration, relay, "inspired-by"),
            arrayOf("p", collaborator, "Collaborator"),
            arrayOf("e", audioEvent, relay2, "audio"),
            arrayOf("a", creditedVideo, "inspired-by"),
            arrayOf("a", "34235:$collaborator:other", relay),
            arrayOf("text-track", "https://blossom.example/abc.vtt", "", "captions", "en"),
            arrayOf("text-track", subtitles, relay2, "subtitles", "en"),
            arrayOf("segment", "00:00:00.000", "00:00:05.000", "Golden hour", "https://img/1.jpg"),
            arrayOf("segment", "00:00:05.000", "00:00:10.000", "Blue hour"),
            arrayOf("t", "sunset"),
        )

    private fun regular() = VideoShortEvent("00".repeat(32), author, 1700000000, tags, "A beach at dusk", "00".repeat(64))

    private fun addressable() = AddressableNormalVideoEvent("00".repeat(32), author, 1700000000, tags + arrayOf(arrayOf("d", "x")), "A beach at dusk", "00".repeat(64))

    @Test
    fun regularVideoLinksEveryCreditAndTrack() {
        val video = regular()
        assertReferences(video.linkedPubKeys(), video.pubKeyHints().map { it.pubkey to it.relay.url }, video.linkedEventIds(), video.eventHints().map { it.eventId to it.relay.url }, video.linkedAddressIds(), video.addressHints().map { it.addressId to it.relay.url })
    }

    @Test
    fun addressableVideoLinksEveryCreditAndTrack() {
        val video = addressable()
        assertReferences(video.linkedPubKeys(), video.pubKeyHints().map { it.pubkey to it.relay.url }, video.linkedEventIds(), video.eventHints().map { it.eventId to it.relay.url }, video.linkedAddressIds(), video.addressHints().map { it.addressId to it.relay.url })
    }

    private fun assertReferences(
        pubKeys: List<String>,
        pubKeyHints: List<Pair<String, String>>,
        eventIds: List<String>,
        eventHints: List<Pair<String, String>>,
        addressIds: List<String>,
        addressHints: List<Pair<String, String>>,
    ) {
        assertEquals(listOf(inspiration, collaborator), pubKeys)
        // a role label in slot 2 is never mistaken for a relay
        assertEquals(listOf(inspiration to relay), pubKeyHints)

        assertEquals(listOf(audioEvent), eventIds)
        assertEquals(listOf(audioEvent to relay2), eventHints)

        // the Blossom URL track is not an address; the 39307 coordinate is
        assertEquals(listOf(creditedVideo, "34235:$collaborator:other", subtitles), addressIds)
        // `inspired-by` in slot 2 is a label, so only the real relays become hints
        assertEquals(listOf("34235:$collaborator:other" to relay, subtitles to relay2), addressHints)
    }

    @Test
    fun segmentTitlesAreIndexedAfterTheBody() {
        val expected = "Sunset loop\nA beach at dusk\nGolden hour\nBlue hour"
        assertEquals(expected, regular().indexableContent())
        assertEquals(expected, addressable().indexableContent())

        val visited = mutableListOf<String>()
        regular().forEachIndexableField { field ->
            field?.let { visited.add(it) }
            true
        }
        assertEquals(expected, visited.joinToString("\n"))

        val fields = SearchFieldExtractor.extract(addressable()) as IndexableFields.Tiered
        assertEquals(listOf("Sunset loop"), fields.primary)
        assertEquals(listOf("Golden hour", "Blue hour"), fields.secondary)
        assertEquals("A beach at dusk", fields.text)
        assertEquals(listOf("sunset"), fields.hashtags)
    }

    @Test
    fun aVideoWithoutReferencesLinksNothing() {
        val bare = VideoNormalEvent("00".repeat(32), author, 1700000000, arrayOf(arrayOf("title", "x")), "", "00".repeat(64))
        assertTrue(bare.linkedPubKeys().isEmpty())
        assertTrue(bare.linkedEventIds().isEmpty())
        assertTrue(bare.linkedAddressIds().isEmpty())
    }
}
