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
package com.vitorpamplona.amethyst.commons.content

import com.vitorpamplona.amethyst.commons.content.djot.DjotToDocument
import kotlin.test.Test
import kotlin.test.assertEquals

class WithoutLeadingTitleTest {
    private fun parse(text: String) = DjotToDocument(NostrInlinePass.NONE).parse(text)

    @Test
    fun dropsAFirstHeadingThatRepeatsTheTitle() {
        val document = parse("# Djot  in the *Model*\n\nBody.\n").withoutLeadingTitle("Djot in the model")
        assertEquals(1, document.blocks.size)
        assertEquals("Body.", PlainText.of(document))
    }

    @Test
    fun keepsAHeadingThatSaysSomethingElse() {
        val document = parse("# Overview\n\nBody.\n")
        assertEquals(document, document.withoutLeadingTitle("Djot in the model"))
        assertEquals(document, document.withoutLeadingTitle(null))
    }

    @Test
    fun keepsATitleThatIsNotTheFirstBlock() {
        val document = parse("Intro.\n\n# Djot in the model\n")
        assertEquals(document, document.withoutLeadingTitle("Djot in the model"))
    }
}
