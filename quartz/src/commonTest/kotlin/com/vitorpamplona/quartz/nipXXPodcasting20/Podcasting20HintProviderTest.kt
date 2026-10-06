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

import com.vitorpamplona.quartz.nipXXPodcasting20.episode.Podcasting20EpisodeEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.trailer.Podcasting20TrailerEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class Podcasting20HintProviderTest {
    private val author = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val original = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"

    @Test
    fun anEpisodeEditLinksTheOriginalWithoutAHint() {
        val event = Podcasting20EpisodeEvent("00".repeat(32), author, 1700000000, arrayOf(arrayOf("d", "ep1"), arrayOf("edit", original)), "", "00".repeat(64))
        assertEquals(listOf(original), event.linkedEventIds())
        assertEquals(emptyList(), event.eventHints())
    }

    @Test
    fun aTrailerEditLinksTheOriginalWithoutAHint() {
        val event = Podcasting20TrailerEvent("00".repeat(32), author, 1700000000, arrayOf(arrayOf("d", "tr1"), arrayOf("edit", original)), "", "00".repeat(64))
        assertEquals(original, event.editsEventId())
        assertEquals(listOf(original), event.linkedEventIds())
        assertEquals(emptyList(), event.eventHints())
    }

    @Test
    fun aMalformedEditIdIsNotLinked() {
        val event = Podcasting20TrailerEvent("00".repeat(32), author, 1700000000, arrayOf(arrayOf("edit", "not-an-id")), "", "00".repeat(64))
        assertEquals(emptyList(), event.linkedEventIds())
    }
}
