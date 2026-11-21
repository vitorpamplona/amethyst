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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/**
 * `Modifier.clip(shape)` for media and the frames around it (photos, videos, link previews, quoted
 * notes). Where Skia draws in software (native Wayland on the desktop) a rounded rectangle with four
 * equal corners clips at a fraction of the cost: an antialiased rounded clip blends every pixel
 * under it through a coverage mask, so a photo drawn 1:1 inside one costs ~1.7 ms a frame against
 * ~0.4 ms inside a rectangle, and only the four corners need the rounded edge. Everywhere else, and
 * for any other shape, it is `clip(shape)`.
 */
expect fun Modifier.mediaClip(shape: Shape): Modifier
