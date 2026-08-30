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
package androidx.window.layout

import androidx.compose.ui.unit.IntRect

/**
 * JVM stand-in for androidx.window.layout.DisplayFeature.
 *
 * A display feature is a fold or a hinge cutting across the window. Desktop
 * monitors have neither, so the app's list of them is legitimately empty here —
 * this is not a gap to fill later, it is the correct answer, and the two-pane
 * layout that consumes it lays out the same way it does on an unfolded phone.
 */
interface DisplayFeature {
    val bounds: IntRect
}

interface FoldingFeature : DisplayFeature {
    @JvmInline
    value class Orientation private constructor(
        private val name: String,
    ) {
        override fun toString() = name

        companion object {
            val VERTICAL = Orientation("VERTICAL")
            val HORIZONTAL = Orientation("HORIZONTAL")
        }
    }

    @JvmInline
    value class OcclusionType private constructor(
        private val name: String,
    ) {
        override fun toString() = name

        companion object {
            val NONE = OcclusionType("NONE")
            val FULL = OcclusionType("FULL")
        }
    }

    val orientation: Orientation
    val occlusionType: OcclusionType
    val isSeparating: Boolean
}
