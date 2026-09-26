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

import com.vitorpamplona.amethyst.commons.browser.BrowserChrome

/**
 * The controls a running app surface offers through its top pull-down pill — described as plain data so
 * [EmbeddedTabLayer] can draw the sheet over the (z-below) surface for the active tab. Deliberately not a
 * corner pill: that's where a site usually puts the user's own avatar/menu, so the handle lives at the
 * top-center instead.
 *
 * *Which* actions show, and in what order, comes from [BrowserChrome] with [state] — the same layout the
 * full-screen browser's native sheet uses — so the two can't drift. Everything the user picks arrives in
 * [onAction]; the console and find-in-page rows are handled by the layer itself.
 */
data class EmbeddedTabChrome(
    val title: String,
    val state: BrowserChrome.State,
    val isFavorite: Boolean = false,
    val desktopSite: Boolean = false,
    val textZoom: Int = BrowserChrome.DEFAULT_TEXT_ZOOM,
    val onAction: (BrowserChrome.Action) -> Unit,
    /** The user typed an address into "Edit address" and pressed Go. */
    val onNavigate: (String) -> Unit = {},
    val onTextZoom: (Int) -> Unit = {},
    /** The origin chip was tapped: page info (web) or the access summary (sandboxed apps). */
    val onOriginTap: () -> Unit = {},
)
