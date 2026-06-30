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
 * The controls a running app surface offers through its top pull-down sheet — described as plain data so
 * [EmbeddedTabLayer] can draw the sheet over the (z-below) surface for the active tab. Deliberately not a
 * corner pill: that's where a site usually puts the user's own avatar/menu, so the handle lives at the
 * top-center instead and only the actions the surface actually supports are shown.
 */
data class EmbeddedTabChrome(
    val title: String,
    /** A sandboxed napplet/nsite (shows the shield + "what it can access"), vs a plain web client. */
    val isSandbox: Boolean,
    val onReload: () -> Unit,
    val onOpenFull: () -> Unit,
    /** Current Tor state, or null when this surface has no Tor toggle (Tor off / locked napplet). */
    val torOn: Boolean? = null,
    val onToggleTor: () -> Unit = {},
    /** The "what it can access" sheet, for sandboxed napplets/nsites; null for a plain web client. */
    val onInfo: (() -> Unit)? = null,
    /**
     * Opens this app's editable permission screen (the "Connected Apps" detail) so the user can change
     * trust level and per-capability grants as they browse; null when the surface has no managed identity.
     */
    val onPermissions: (() -> Unit)? = null,
    /** Whether the current URL/app is already saved as a favorite. */
    val isFavorite: Boolean = false,
    /** Toggles the current site/app in the favorites registry; null when not applicable. */
    val onFavorite: (() -> Unit)? = null,
)
