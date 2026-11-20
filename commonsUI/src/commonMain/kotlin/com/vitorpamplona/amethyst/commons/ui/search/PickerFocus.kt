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
package com.vitorpamplona.amethyst.commons.ui.search

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties

/**
 * For a clickable row or button in a picker that opens under a text field: pressing it must not
 * take focus away from the field.
 *
 * Under a mouse, `clickable` asks for focus on press. The field blurs, and a picker that only
 * belongs under a focused field (the search pickers derive from `focused`) closes before the
 * release arrives, so the click lands on nothing and the pick never happens. Even where the picker
 * survives the blur, the reader loses their caret and has to click back into the field to keep
 * typing. The keyboard path is the field's own: the arrow keys walk the rows while the caret stays
 * where it is.
 */
fun Modifier.keepsFieldFocus(): Modifier = this then NotFocusable

// One instance for every row, rather than a new element per row per recomposition.
private val NotFocusable = Modifier.focusProperties { canFocus = false }
