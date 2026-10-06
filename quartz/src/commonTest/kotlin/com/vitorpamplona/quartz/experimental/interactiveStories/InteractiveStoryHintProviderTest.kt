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
package com.vitorpamplona.quartz.experimental.interactiveStories

import com.vitorpamplona.quartz.experimental.interactiveStories.tags.StoryOptionTag
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InteractiveStoryHintProviderTest {
    private val pk1 = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val pk2 = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val eid = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val eid2 = "98b574c3527f0ffb30b7271084e3f07480733c7289f8de424d29eae82e36c758"
    private val relay = "wss://relay.damus.io/"
    private val zero = "00".repeat(32)
    private val sig = "00".repeat(64)

    private fun visited(event: com.vitorpamplona.quartz.nip50Search.SearchableEvent): String {
        val out = mutableListOf<String>()
        event.forEachIndexableField {
            if (it != null) out.add(it)
            true
        }
        return out.joinToString("\n")
    }

    private val scene1 = "30297:$pk1:scene-1"
    private val scene2 = "30297:$pk1:scene-2"
    private val prologue = "30296:$pk1:story"

    private fun scene() =
        InteractiveStorySceneEvent(
            zero,
            pk1,
            1,
            arrayOf(
                arrayOf("d", "scene-0"),
                arrayOf("title", "The Fork"),
                arrayOf("summary", "Two roads"),
                arrayOf("option", "Go left", scene1, relay),
                arrayOf("option", "Go right", scene2),
                arrayOf("option", "Broken", "not-an-address"),
            ),
            "You stand at a fork.",
            sig,
        )

    @Test
    fun optionsAreTheStoryGraph() {
        val event = scene()
        assertEquals(listOf(scene1, scene2), event.linkedAddressIds())
        assertEquals(listOf(scene1), event.addressHints().map { it.addressId })
        assertEquals(listOf(relay), event.addressHints().map { it.relay.url })
    }

    @Test
    fun optionLabelsAreIndexed() {
        val event = scene()
        assertEquals("The Fork\nTwo roads\nYou stand at a fork.\nGo left\nGo right", event.indexableContent())
        assertEquals(event.indexableContent(), visited(event))
    }

    @Test
    fun optionLabelNeedsAWellFormedScene() {
        assertEquals("Go", StoryOptionTag.parseLabel(arrayOf("option", "Go", scene1)))
        // the bech32 form parse() accepts is kept
        assertEquals("Go", StoryOptionTag.parseLabel(arrayOf("option", "Go", NAddress.create(30297, pk1, "scene-1", null))))
        assertNull(StoryOptionTag.parseLabel(arrayOf("option", "  ", scene1)))
        assertNull(StoryOptionTag.parseLabel(arrayOf("option", "Go", "30297:${"zz".repeat(32)}:scene-1")))
        assertNull(StoryOptionTag.parseLabel(arrayOf("option", "Go", "not-an-address")))
        assertNull(StoryOptionTag.parseLabel(arrayOf("option", "Go")))
    }

    @Test
    fun readingStateLinksRootEvenWithABadRelay() {
        val event =
            InteractiveStoryReadingStateEvent(zero, pk2, 1, arrayOf(arrayOf("d", "x"), arrayOf("A", prologue, "not a relay")), "", sig)
        assertEquals(listOf(prologue), event.linkedAddressIds())
    }

    @Test
    fun readingStateLinksRootAndCurrentScene() {
        val event =
            InteractiveStoryReadingStateEvent(
                zero,
                pk2,
                1,
                arrayOf(arrayOf("d", prologue), arrayOf("A", prologue, relay), arrayOf("a", scene1)),
                "",
                sig,
            )
        assertEquals(listOf(prologue, scene1), event.linkedAddressIds())
        assertEquals(listOf(prologue), event.addressHints().map { it.addressId })

        // An old state without the `A` tag still links its root through the `d` coordinate.
        val old = InteractiveStoryReadingStateEvent(zero, pk2, 1, arrayOf(arrayOf("d", prologue)), "", sig)
        assertEquals(listOf(prologue), old.linkedAddressIds())
        assertTrue(old.addressHints().isEmpty())
    }
}
