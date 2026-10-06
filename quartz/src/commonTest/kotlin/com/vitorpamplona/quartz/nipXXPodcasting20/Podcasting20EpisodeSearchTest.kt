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

import com.vitorpamplona.quartz.nip50Search.IndexableFields
import com.vitorpamplona.quartz.nip50Search.SearchFieldExtractor
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.Podcasting20EpisodeEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class Podcasting20EpisodeSearchTest {
    private val author = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"

    private val event =
        Podcasting20EpisodeEvent(
            "00".repeat(32),
            author,
            1700000000,
            arrayOf(
                arrayOf("d", "ep1"),
                arrayOf("title", "Episode 42"),
                arrayOf("description", "On search"),
                arrayOf("person", "Alice Host", "host"),
                arrayOf("person", "Bob Guest", "guest", "https://img"),
                arrayOf("soundbite", "10", "30", "The big reveal"),
                arrayOf("soundbite", "60", "15"),
                arrayOf("t", "nostr"),
            ),
            "show notes",
            "00".repeat(64),
        )

    @Test
    fun personNamesAndSoundbiteTitlesAreIndexedBeforeTheTopics() {
        assertEquals("Episode 42\nOn search\nshow notes\nAlice Host\nBob Guest\nThe big reveal\nnostr", event.indexableContent())
    }

    @Test
    fun theVisitorRejoinsToTheIndexedContent() {
        val fields = mutableListOf<String>()
        event.forEachIndexableField { field ->
            field?.let { fields.add(it) }
            true
        }
        assertEquals(event.indexableContent(), fields.joinToString(event.indexableSeparator()))
    }

    @Test
    fun theExtractorPutsNamesAndSoundbitesBesideTheDescription() {
        assertEquals(
            IndexableFields.Tiered(
                primary = listOf("Episode 42"),
                secondary = listOf("On search", "Alice Host", "Bob Guest", "The big reveal"),
                text = "show notes",
                hashtags = listOf("nostr"),
            ),
            SearchFieldExtractor.extract(event),
        )
    }
}
