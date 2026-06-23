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

/**
 * The active embedded tab's chrome, described as plain data so [EmbeddedTabLayer] can draw the floating
 * [AppControlPuck] **on top of** the warm surface (which the layer renders over the whole nav tree). An
 * embedded tab can't draw its own chrome above that surface, so the screen hands its controls here and
 * the layer renders them. Modeled as callbacks (not a composable) so it's a cheap snapshot value.
 */
data class EmbeddedTabChrome(
    val marker: Marker,
    val description: String,
    val onReload: () -> Unit,
    val onPopOut: () -> Unit,
    /** Current Tor state, or null when this surface has no Tor toggle (Tor unavailable / locked napplet). */
    val torOn: Boolean? = null,
    val onToggleTor: () -> Unit = {},
    /** The "what it can access" sheet, for sandboxed napplets/nsites; null for a plain web client. */
    val onInfo: (() -> Unit)? = null,
) {
    /** The always-visible trusted marker: the sandbox shield for napplets/nsites, a globe for a web URL. */
    enum class Marker { SHIELD, GLOBE }
}
