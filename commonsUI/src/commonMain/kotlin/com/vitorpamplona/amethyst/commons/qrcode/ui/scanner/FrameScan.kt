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
package com.vitorpamplona.amethyst.commons.qrcode.ui.scanner

import com.vitorpamplona.amethyst.commons.qrcode.ScanFrame
import com.vitorpamplona.amethyst.commons.qrcode.ScanResult

/** One analysed frame: what was decoded, how big the analysed image was, and how dark it is. */
data class FrameScan(
    val results: List<ScanResult>,
    val frame: ScanFrame,
    /** Mean luminance over a sparse sample of the frame, 0f (black) to 1f (white). */
    val brightness: Float,
    /**
     * Whether this frame got the expensive pass. Only those find a marginal code, so only they
     * can say a code is truly alone in view.
     */
    val thorough: Boolean = true,
)
