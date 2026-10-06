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
package com.vitorpamplona.quartz.nip68Picture

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip50Search.IndexableFields
import com.vitorpamplona.quartz.nip50Search.SearchFieldExtractor
import kotlin.test.Test
import kotlin.test.assertEquals

class PictureHintProviderTest {
    private val author = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val friend = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val cited = "7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377"
    private val quoted = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val citedEvent = "b1791d7fc9ae3d38966568c257ffb3a02cbf8394cdb4805bc70f64fc3c0b6879"
    private val relay = "wss://relay.damus.io/"
    private val relay2 = "wss://nos.lol/"

    private val nprofile = NProfile.create(cited, RelayUrlNormalizer.normalizeOrNull(relay2))
    private val nevent = NEvent.create(citedEvent, null, 1, RelayUrlNormalizer.normalizeOrNull(relay))

    private val picture =
        PictureEvent(
            "00".repeat(32),
            author,
            1700000000,
            arrayOf(
                arrayOf("title", "Lisbon rooftops"),
                arrayOf("imeta", "url https://img/1.jpg", "alt Terracotta roofs at sunset", "annotate-user $friend:120:80"),
                arrayOf("imeta", "url https://img/2.jpg", "annotate-user tooshort:1:1"),
                arrayOf("p", friend, relay),
                arrayOf("q", quoted, relay2),
                arrayOf("location", "Lisbon, Portugal"),
                arrayOf("t", "travel"),
            ),
            "Golden hour with nostr:$nprofile, see nostr:$nevent",
            "00".repeat(64),
        )

    @Test
    fun picturesLinkTaggedAnnotatedAndCitedPeople() {
        assertEquals(listOf(friend, friend, cited), picture.linkedPubKeys())
        assertEquals(listOf(friend to relay, cited to relay2), picture.pubKeyHints().map { it.pubkey to it.relay.url })
    }

    @Test
    fun picturesLinkQuotedAndCitedEvents() {
        assertEquals(listOf(quoted, citedEvent), picture.linkedEventIds())
        assertEquals(listOf(quoted to relay2, citedEvent to relay), picture.eventHints().map { it.eventId to it.relay.url })
        assertEquals(emptyList(), picture.linkedAddressIds())
    }

    @Test
    fun locationAndImageDescriptionsAreSearchable() {
        val expected = "Lisbon rooftops\nGolden hour with nostr:$nprofile, see nostr:$nevent\nLisbon, Portugal\nTerracotta roofs at sunset"
        assertEquals(expected, picture.indexableContent())

        val visited = mutableListOf<String>()
        picture.forEachIndexableField { field ->
            field?.let { visited.add(it) }
            true
        }
        assertEquals(expected, visited.joinToString("\n"))

        val tiered = SearchFieldExtractor.extract(picture) as IndexableFields.Tiered
        assertEquals(listOf("Lisbon rooftops"), tiered.primary)
        assertEquals(listOf("Terracotta roofs at sunset"), tiered.secondary)
        // the funnel carries location and hashtags once
        assertEquals(listOf("Lisbon, Portugal"), tiered.locations)
        assertEquals(listOf("travel"), tiered.hashtags)
    }
}
