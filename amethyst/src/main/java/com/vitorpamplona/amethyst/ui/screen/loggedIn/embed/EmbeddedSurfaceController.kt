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

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.privacysandbox.ui.client.view.SandboxedSdkView

/**
 * The minimal surface controls [EmbeddedTabHost] needs to keep a session warm regardless of whether
 * it's a browser ([com.vitorpamplona.amethyst.ui.screen.loggedIn.browser.EmbeddedBrowserController])
 * or an nsite/napplet ([com.vitorpamplona.amethyst.ui.screen.loggedIn.favorites.EmbeddedNappletController]).
 *
 * Warm-keep works by keeping the session's [SandboxedSdkView] **attached** to the window the whole
 * time (just moved off-screen when not active) — the privacy-sandbox view only closes its session on
 * detach, so an attached-but-hidden view stays alive. [onShown]/[onHidden] let a controller pause its
 * applet's JS while hidden; [teardown] tears the session down for good (eviction).
 */
@RequiresApi(Build.VERSION_CODES.R)
interface EmbeddedSurfaceController {
    fun attachView(view: SandboxedSdkView)

    /** The session became the visible tab. */
    fun onShown() {}

    /** The session is warm but off-screen; a controller may pause its applet here. */
    fun onHidden() {}

    /** Permanently close the session (unbind the service); used on eviction. */
    fun teardown()
}
