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
package com.vitorpamplona.amethyst.commons.ui.prototypes.mediacomposer

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.awt.Container
import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent

/** PROTOTYPE ONLY. Checks that the prototypes behave as the design doc says, not just render. */
class PrototypeComposerBehaviourTest {
    @Test
    fun postUploadsEverythingThenTurnsMarkersIntoUrls() =
        runBlocking {
            val c = sampleComposer(CoroutineScope(Dispatchers.Default))
            c.post()
            withTimeout(10_000) { while (c.phase == ComposerPhase.PUBLISHING) delay(20) }
            assertEquals(ComposerPhase.PUBLISHED, c.phase)
            val text = c.message.text.toString()
            assertFalse(MediaTokens.REGEX.containsMatchIn(text), text)
            assertEquals(3, Regex("https://blossom.primal.net/").findAll(text).count(), text)
        }

    @Test
    fun aFailureKeepsTextAndMarkersAndRetryFinishes() =
        runBlocking {
            val c = sampleComposer(CoroutineScope(Dispatchers.Default))
            c.failNext += 2
            val before = c.message.text.toString()
            c.post()
            withTimeout(10_000) { while (c.phase == ComposerPhase.PUBLISHING) delay(20) }
            assertEquals(ComposerPhase.NEEDS_ATTENTION, c.phase)
            assertEquals(before, c.message.text.toString())
            assertTrue(c.draft(2)!!.status is DraftStatus.Failed)
            assertTrue(c.draft(1)!!.status is DraftStatus.Uploaded)

            c.retry(c.draft(2)!!)
            withTimeout(10_000) { while (c.phase == ComposerPhase.PUBLISHING) delay(20) }
            assertEquals(ComposerPhase.PUBLISHED, c.phase)
        }

    @Test
    fun pickThenAddInsertsMarkersAtTheCursor() {
        val c = PrototypeComposer(CoroutineScope(Dispatchers.Unconfined))
        c.message.edit { append("Look:") }
        val picked = listOf(MediaDraft(50, DraftKind.IMAGE, sunsetPhoto(80, 60), 4032, 3024, 1), MediaDraft(51, DraftKind.IMAGE, forestPhoto(60, 80), 3024, 4032, 1))
        c.pick(picked)
        assertEquals("Look:", c.message.text.toString(), "nothing lands in the text while the Studio is open")
        c.confirmPick()
        assertEquals("Look:\n[[media:50]]\n[[media:51]]\n", c.message.text.toString())
        assertEquals(listOf(50, 51), c.orderedDrafts().map { it.id })
    }

    @Test
    fun pickThenCancelLeavesNoTrace() {
        val c = PrototypeComposer(CoroutineScope(Dispatchers.Unconfined))
        c.message.edit { append("Look:") }
        c.pick(listOf(MediaDraft(50, DraftKind.IMAGE, sunsetPhoto(80, 60), 4032, 3024, 1)))
        c.cancelPick()
        assertEquals("Look:", c.message.text.toString())
        assertTrue(c.drafts.isEmpty())
    }

    @Test
    fun lockedAspectsCropToTheShapeTheProfileShows() {
        val avatar = MediaDraft(70, DraftKind.IMAGE, forestPhoto(60, 80), 3024, 4032, 1)
        StudioConfig.avatar().prepare(listOf(avatar))
        val (aw, ah) = avatar.outputSize(QualityStop.ORIGINAL)
        assertEquals(aw, ah, "avatar is square in pixels")

        val banner = MediaDraft(71, DraftKind.IMAGE, sunsetPhoto(80, 60), 4032, 3024, 1)
        StudioConfig.banner().prepare(listOf(banner))
        val (bw, bh) = banner.outputSize(QualityStop.ORIGINAL)
        assertEquals(3f, bw.toFloat() / bh, 0.01f)
    }

    /** Backspace right after a drawn chip deletes the whole raw marker, not its last character. */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun backspaceRemovesAWholeMarker() = backspaceRemovesWholeMarker(MarkerStyle.CHIPS)

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun backspaceRemovesAWholeBlock() = backspaceRemovesWholeMarker(MarkerStyle.BLOCKS)

    @OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
    private fun backspaceRemovesWholeMarker(style: MarkerStyle) {
        val c = sampleComposer(CoroutineScope(Dispatchers.Unconfined))
        val scene = ImageComposeScene(400, 860, Density(1f)) { AmethystPreviewTheme(dark = true) { PrototypeScreen(c, style) } }
        try {
            scene.render(0)
            // Focus the field by tapping its first line, then put the cursor after Photo 1's marker.
            scene.sendPointerEvent(PointerEventType.Press, Offset(120f, 80f))
            scene.sendPointerEvent(PointerEventType.Release, Offset(120f, 80f))
            scene.render(16_000_000)
            val token = c.draft(1)!!.token
            val end = c.message.text.indexOf(token) + token.length
            c.message.edit { selection = TextRange(end) }
            scene.render(32_000_000)

            val awt = KeyEvent(Container(), KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_BACK_SPACE, KeyEvent.CHAR_UNDEFINED)
            scene.sendKeyEvent(ComposeKeyEvent(Key.Backspace, KeyEventType.KeyDown, nativeEvent = awt))
            scene.render(48_000_000)

            val text = c.message.text.toString()
            assertFalse(text.contains(token), text)
            assertFalse(text.contains("[[media:1"), text)
            assertTrue(text.contains(c.draft(2)!!.token), text)
        } finally {
            scene.close()
        }
    }
}
