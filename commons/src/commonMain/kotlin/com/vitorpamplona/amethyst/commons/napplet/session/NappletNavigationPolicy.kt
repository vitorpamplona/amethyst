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
package com.vitorpamplona.amethyst.commons.napplet.session

import com.vitorpamplona.amethyst.commons.napplet.HostProfile
import com.vitorpamplona.amethyst.commons.napplet.NappletOrigins
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract

/** Which frame of a launch a navigation is for. */
enum class NappletFrame {
    /** The trusted shell, the top frame. */
    SHELL,

    /** The app's own frame, a child of the shell. */
    APP,

    /** A frame inside the app's frame (a site's video player). */
    EMBED,
}

/** What a host does with one navigation. */
sealed interface NappletNavigation {
    /** Let the frame load it. */
    data object Allow : NappletNavigation

    /** Stop it; the frame stays where it is. */
    data object Cancel : NappletNavigation

    /** Stop it, and open [url] for the user outside the launch (the system browser, or the app for a nostr link). */
    data class OpenLink(
        val url: String,
    ) : NappletNavigation
}

/**
 * Where a launch's frames may go, the same on every host:
 * - The internal hosts and the documents a page makes itself load.
 * - A website's embeds (frames inside its own frame) are ordinary web pages and may load anything.
 * - The shell and the app's own frame never leave: a site's frame that left its origin could say
 *   hello to the shell as someone else. A link the user clicked there opens outside, and only then
 *   (a hostile page can't auto-redirect the user's browser), and only http(s) (no app schemes).
 */
object NappletNavigationPolicy {
    fun decide(
        url: String,
        frame: NappletFrame,
        userGesture: Boolean,
        profile: HostProfile,
        origins: NappletOrigins = NappletWebContract.HTTPS,
    ): NappletNavigation {
        if (NappletRequestPolicy.isInternal(url, origins) || NappletRequestPolicy.isLocal(url)) return NappletNavigation.Allow
        if (profile == HostProfile.WEBSITE && frame == NappletFrame.EMBED && NappletRequestPolicy.isWebLink(url)) return NappletNavigation.Allow
        if (profile.opensLinks && userGesture && NappletRequestPolicy.isWebLink(url)) return NappletNavigation.OpenLink(url)
        return NappletNavigation.Cancel
    }

    /** A `window.open` or a `target=_blank` link: never a window of the launch's own. */
    fun popup(
        url: String,
        profile: HostProfile,
    ): NappletNavigation = if (profile.opensLinks && NappletRequestPolicy.isWebLink(url)) NappletNavigation.OpenLink(url) else NappletNavigation.Cancel

    /** The frame, for a browser that says whether the frame's parent is the top frame (Chromium). */
    fun frameOf(
        isMainFrame: Boolean,
        parentIsMainFrame: Boolean?,
    ): NappletFrame =
        when {
            isMainFrame -> NappletFrame.SHELL
            parentIsMainFrame == false -> NappletFrame.EMBED
            else -> NappletFrame.APP
        }

    /**
     * The frame, for a browser that names no parent (WebKit's `WKFrameInfo`): a frame now at the app's
     * own origin is the app's frame, so it may not leave it; any other frame is an embed.
     */
    fun frameByOrigin(
        isMainFrame: Boolean,
        frameOrigin: String?,
        appOrigin: String,
    ): NappletFrame =
        when {
            isMainFrame -> NappletFrame.SHELL
            frameOrigin == appOrigin -> NappletFrame.APP
            else -> NappletFrame.EMBED
        }
}

/**
 * Who may reach the native bridge: only the shell document in the top frame. The page's frames can
 * see whatever the browser injects, and get nothing from calling it.
 */
object NappletBridgeGate {
    /** For a browser that reports the calling frame's URL (Chromium). */
    fun acceptsUrl(
        isMainFrame: Boolean,
        frameUrl: String?,
        origins: NappletOrigins = NappletWebContract.HTTPS,
    ): Boolean = isMainFrame && frameUrl == origins.shellUrl

    /** For a browser that reports the calling frame's security origin (WebKit, Android's listener). */
    fun acceptsOrigin(
        isMainFrame: Boolean,
        frameOrigin: String?,
        origins: NappletOrigins = NappletWebContract.HTTPS,
    ): Boolean = isMainFrame && frameOrigin == origins.origin
}
