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

import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingStateBuildTest {
    @Test
    fun theReadingStateKeepsItsRootAndItsSummaryAndImage() {
        val pk = "1".repeat(64)
        val prologue =
            InteractiveStoryPrologueEvent(
                "0".repeat(64),
                pk,
                1,
                arrayOf(arrayOf("d", "story"), arrayOf("title", "A Story"), arrayOf("summary", "the summary"), arrayOf("image", "https://img.example/cover.png")),
                "",
                "0".repeat(128),
            )
        val scene = InteractiveStorySceneEvent("2".repeat(64), pk, 2, arrayOf(arrayOf("d", "scene-2")), "", "0".repeat(128))
        val state = NostrSignerSync().sign(InteractiveStoryReadingStateEvent.build(EventHintBundle(prologue), EventHintBundle(scene)))

        assertEquals(prologue.address().toValue(), state.root()?.toTag())
        assertEquals(scene.address().toValue(), state.currentScene()?.toValue())
        assertEquals("the summary", state.summary())
        assertEquals("https://img.example/cover.png", state.image())
    }

    @Test
    fun aStateWrittenWithoutItsRootTagFallsBackToItsDTagAndHealsOnUpdate() {
        val pk = "1".repeat(64)
        val story = "30296:$pk:story"
        val scene = "30297:$pk:scene-2"
        // What the old builder published: the root's lowercase `a` was replaced by the scene's.
        val old =
            InteractiveStoryReadingStateEvent("0".repeat(64), pk, 1, arrayOf(arrayOf("d", story), arrayOf("a", scene), arrayOf("status", "reading")), "", "0".repeat(128))
        assertEquals(story, old.root()?.toTag())

        val next = InteractiveStorySceneEvent("3".repeat(64), pk, 2, arrayOf(arrayOf("d", "scene-3")), "", "0".repeat(128))
        val updated = NostrSignerSync().sign(InteractiveStoryReadingStateEvent.update(old, EventHintBundle(next)))
        assertEquals(listOf(story), updated.tags.filter { it[0] == "A" }.map { it[1] })
        assertEquals(next.address().toValue(), updated.currentScene()?.toValue())
    }
}
