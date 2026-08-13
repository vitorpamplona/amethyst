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
package com.vitorpamplona.amethyst.ui.components.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The placeholder shown while a previously-seen PDF re-renders reserves its box from the ratio
 * cached in `MediaAspectRatioCache`, and the loaded thumbnail sizes itself from the ratio the
 * renderer reports. Those are the same number, so both sides must clamp it identically — a
 * placeholder that skipped the clamp would reserve a different box than the page lands in, which is
 * the jump it exists to remove.
 */
class PdfPreviewAspectRatioTest {
    @Test
    fun ordinaryPortraitPageIsUnchanged() {
        // US Letter, in PostScript points.
        assertEquals(612f / 792f, previewAspectRatio(612f / 792f), 0.0001f)
    }

    @Test
    fun landscapePageIsUnchanged() {
        assertEquals(792f / 612f, previewAspectRatio(792f / 612f), 0.0001f)
    }

    @Test
    fun pathologicallyTallPageIsClamped() {
        // A 200x4000 receipt would otherwise reserve 20 screens of height for a sliver of content.
        assertEquals(0.2f, previewAspectRatio(200f / 4000f), 0.0001f)
    }

    @Test
    fun theClampIsTheSameNumberBothSidesUse() {
        val cachedOnRevisit = 200f / 4000f
        val reportedByTheRenderer = 200f / 4000f

        assertEquals(previewAspectRatio(reportedByTheRenderer), previewAspectRatio(cachedOnRevisit), 0.0f)
    }
}
