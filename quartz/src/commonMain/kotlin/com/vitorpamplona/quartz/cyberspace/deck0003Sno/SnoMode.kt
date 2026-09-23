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
package com.vitorpamplona.quartz.cyberspace.deck0003Sno

/**
 * Which of the three drawings of the same vertex list a client should produce
 * (DECK-0003 §1.5). Mode is a property of the object, not of the viewer.
 */
enum class SnoMode(
    val code: String,
) {
    /** The triangles in `faces`, colours interpolated across each face. */
    SOLID("solid"),

    /** The vertices, each at its own colour. */
    POINTS("points"),

    /** The edges of the faces, each drawn once; with no faces, one polyline through the vertices in order. */
    LINES("lines"),

    ;

    companion object {
        fun parseOrNull(code: String?): SnoMode? = entries.firstOrNull { it.code == code }
    }
}
