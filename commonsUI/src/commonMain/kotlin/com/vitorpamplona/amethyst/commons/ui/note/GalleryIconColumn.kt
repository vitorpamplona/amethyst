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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.theme.WidthAuthorPictureModifier

/**
 * The column a gallery row puts its kind icon in (the heart, the boost arrows, the bolt), before
 * its row of 35dp faces. The notification cards and the expanded reactions under a note share it.
 *
 * Indented, it is the author column: [indentedModifier] (55dp wide) with the icon at its top end,
 * next to the faces. In the full-width layouts it is the 35dp picture slot of a full-width note's
 * header, with the icon centred on the first row of faces, so the faces start where a full-width
 * note's name does and the card's body can use the whole width below.
 *
 * The full-width slot is a minimum, not a fixed size: a reaction drawn as text (a bare
 * `:shortcode:`, a word, several emoji) widens it up to the indented column's 55dp and wraps
 * there, instead of being clipped to 35dp.
 *
 * [icon] receives the alignment to put on its modifier.
 */
@Composable
fun GalleryIconColumn(
    indentedModifier: Modifier = WidthAuthorPictureModifier,
    icon: @Composable BoxScope.(iconAlignment: Alignment) -> Unit,
) {
    if (LocalDisplaySettings.current.fullWidthNotes) {
        Box(FullWidthGalleryIconModifier) { icon(Alignment.Center) }
    } else {
        Box(indentedModifier) { icon(Alignment.TopEnd) }
    }
}

private val FullWidthGalleryIconModifier = Modifier.sizeIn(minWidth = 35.dp, minHeight = 35.dp, maxWidth = 55.dp)
