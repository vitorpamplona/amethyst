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
package com.vitorpamplona.amethyst.ui.components

import android.content.Context
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.richtext.YouTubeLink
import com.vitorpamplona.amethyst.favorites.FavoriteAppLauncher

/**
 * Plays a YouTube link inside Amethyst, when the viewer has asked for that.
 *
 * YouTube publishes no media file, so there is nothing for ExoPlayer to open -- see [YouTubeLink].
 * The one supported way to play a video outside youtube.com is its IFrame player page, which needs
 * a browser. Rather than embed a WebView of our own, this reuses the one the app already has:
 * [FavoriteAppLauncher.launchUrl] opens the page full-screen in `NappletBrowserActivity`, which
 * runs in the `:napplet` process, honours the site's Tor choice, and keeps a per-account storage
 * partition. YouTube's scripts therefore never share a process with the account or `LocalCache`.
 *
 * Off by default. [open] returns false whenever it declines -- setting off, or not a video link --
 * and the caller then opens the URL exactly as it did before, in the external app.
 */
object YouTubeInApp {
    /**
     * Opens [url]'s player page in the in-app browser, returning whether it did.
     *
     * A false return is the normal, expected path and is never an error: it means "not mine", and
     * the caller must fall back to its usual handling.
     */
    fun open(
        context: Context,
        url: String,
    ): Boolean {
        if (!isEnabled()) return false
        val playerUrl = YouTubeLink.embedUrlFor(url) ?: return false

        FavoriteAppLauncher.launchUrl(context, playerUrl)
        return true
    }

    /**
     * Whether a [url] would be taken in-app, for callers that need to decide before the tap (an
     * icon, say). Same answer [open] would give.
     */
    fun handles(url: String): Boolean = isEnabled() && YouTubeLink.parse(url) != null

    // Read from the app-wide settings rather than threaded through every call site: the two
    // callers (the link-preview card and the NIP-71 video card) sit at different depths and only
    // one of them has an AccountViewModel in scope. Main-process only -- `:napplet` never loads
    // this class, and could not reach `Amethyst.instance` if it did.
    private fun isEnabled(): Boolean = Amethyst.instance.uiState.playYouTubeInApp()
}
