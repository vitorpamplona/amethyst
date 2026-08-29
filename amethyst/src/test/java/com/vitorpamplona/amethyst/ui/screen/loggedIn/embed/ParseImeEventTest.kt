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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.embed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Page -> host `ime.*` envelopes, parsed the way the shim actually emits them. */
class ParseImeEventTest {
    @Test
    fun focusCarriesFieldConfigurationAndBuffer() {
        val e =
            parseImeEvent(
                """{"type":"ime.focus","inputType":"email","enterKeyHint":"go","multiline":false,
                   "readOnly":false,"text":"hi","selStart":1,"selEnd":2}""",
            ) as ImeEvent.Focus
        assertEquals("email", e.inputType)
        assertEquals("go", e.enterKeyHint)
        assertFalse(e.multiline)
        assertFalse(e.readOnly)
        assertEquals("hi", e.text)
        assertEquals(1, e.selStart)
        assertEquals(2, e.selEnd)
        assertNull(e.geometry)
    }

    @Test
    fun focusDefaultsToATextFieldWhenThePageSaysNothing() {
        val e = parseImeEvent("""{"type":"ime.focus"}""") as ImeEvent.Focus
        assertEquals("text", e.inputType)
        assertEquals("", e.enterKeyHint)
        assertFalse(e.multiline)
        assertEquals("", e.text)
        assertEquals(0, e.selStart)
    }

    @Test
    fun readOnlyFieldIsReported() {
        val e = parseImeEvent("""{"type":"ime.focus","readOnly":true}""") as ImeEvent.Focus
        assertTrue(e.readOnly)
    }

    @Test
    fun payloadFreeEventsAreTheSingletons() {
        assertSame(ImeEvent.WantKeyboard, parseImeEvent("""{"type":"ime.wantkb"}"""))
        assertSame(ImeEvent.Blur, parseImeEvent("""{"type":"ime.blur"}"""))
    }

    @Test
    fun refocusWrapsAFocus() {
        val e = parseImeEvent("""{"type":"ime.refocus","text":"abc","selStart":3,"selEnd":3}""") as ImeEvent.ReFocus
        assertEquals("abc", e.focus.text)
        assertEquals(3, e.focus.selStart)
    }

    @Test
    fun stateCarriesGeometry() {
        val e =
            parseImeEvent(
                """{"type":"ime.state","text":"abc","selStart":0,"selEnd":3,
                   "geom":{"l":1,"t":2,"r":3,"b":4,"sx":5,"sb":6,"ex":7,"eb":8,"vw":360,"rng":true}}""",
            ) as ImeEvent.State
        assertEquals("abc", e.text)
        assertEquals(0, e.selStart)
        assertEquals(3, e.selEnd)
        val g = e.geometry!!
        assertEquals(1f, g.left, 0f)
        assertEquals(4f, g.bottom, 0f)
        assertEquals(5f, g.startX, 0f)
        assertEquals(8f, g.endBottom, 0f)
        assertEquals(360f, g.viewportWidth, 0f)
        assertTrue(g.isRange)
        // Absent caret keys stay null so the host can tell "no caret rect" from "caret at 0".
        assertNull(g.caretX)
        assertNull(g.caretTop)
        assertNull(g.caretBottom)
    }

    @Test
    fun caretTapCarriesTheCaretRect() {
        val e = parseImeEvent("""{"type":"ime.carettap","geom":{"cx":12.5,"ct":30,"cb":48}}""") as ImeEvent.CaretTap
        val g = e.geometry!!
        assertEquals(12.5f, g.caretX!!, 0f)
        assertEquals(30f, g.caretTop!!, 0f)
        assertEquals(48f, g.caretBottom!!, 0f)
    }

    @Test
    fun pageSelectionAndScrollFlipOnActive() {
        val sel = parseImeEvent("""{"type":"ime.pagesel","active":true,"text":"sel","geom":{}}""") as ImeEvent.PageSelection
        assertTrue(sel.active)
        assertEquals("sel", sel.text)
        assertNotNull(sel.geometry)

        val cleared = parseImeEvent("""{"type":"ime.pagesel","active":false}""") as ImeEvent.PageSelection
        assertFalse(cleared.active)
        assertNull(cleared.geometry)

        assertTrue((parseImeEvent("""{"type":"ime.scroll","active":true}""") as ImeEvent.Scroll).active)
        assertFalse((parseImeEvent("""{"type":"ime.scroll","active":false}""") as ImeEvent.Scroll).active)
    }

    @Test
    fun garbageAndUnknownTypesAreDropped() {
        assertNull(parseImeEvent("""{"type":"ime.whatever"}"""))
        assertNull(parseImeEvent("""{"no":"type"}"""))
        assertNull(parseImeEvent("<html>"))
        assertNull(parseImeEvent(""))
    }
}
