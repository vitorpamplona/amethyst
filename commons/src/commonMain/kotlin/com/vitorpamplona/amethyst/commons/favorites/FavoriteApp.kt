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
package com.vitorpamplona.amethyst.commons.favorites

import androidx.compose.runtime.Immutable

/**
 * A user-favorited web app: an entry the user pinned so it's one tap away (in the bottom bar, the
 * Favorite Apps grid, or the browser launcher). Stored **device-locally** — favorites are a UX
 * convenience, not account state, so they never become a Nostr event.
 *
 * Two shapes, because the launch layer has exactly two shapes (see `FavoriteAppLauncher`):
 *
 * - [NostrApp] — a NIP-5A nsite **or** NIP-5D napplet. Both resolve to the same launch path
 *   (`NappletLauncher` → sandboxed `:napplet` host), so they share one case here. Whether the
 *   resolved event is an nsite (website mode) or a locked napplet is recomputed from the live event
 *   at launch time, never stored: the addressable [coordinate] survives code/manifest updates, but
 *   the manifest's `requires`/website-mode flags do not.
 * - [WebUrl] — an arbitrary `https://` nostr client. No identity, no coordinate; the URL is the key.
 *
 * A [NostrApp] is only launchable while its event is resolvable in `LocalCache`; a [WebUrl] is always
 * launchable. That is the one robustness difference between the two cases.
 */
@Immutable
sealed interface FavoriteApp {
    /** User-visible name shown on the tab / grid button / launcher row. */
    val label: String

    /** Epoch millis the favorite was added; used as the default ordering tiebreaker. */
    val addedAt: Long

    /** Stable, type-discriminated identity used for de-duplication and as a UI key. */
    val id: String

    /**
     * A NIP-5A nsite or NIP-5D napplet, keyed by its **addressable coordinate** `kind:pubkey:dtag`.
     * The coordinate (not the content hash) is stored so a favorite keeps working across routine
     * code updates, exactly like the permission ledger's `NappletIdentity`.
     */
    @Immutable
    data class NostrApp(
        val coordinate: String,
        override val label: String,
        override val addedAt: Long,
    ) : FavoriteApp {
        override val id: String get() = "nostr:$coordinate"
    }

    /** An arbitrary web client reached by URL. */
    @Immutable
    data class WebUrl(
        val url: String,
        override val label: String,
        override val addedAt: Long,
    ) : FavoriteApp {
        override val id: String get() = "url:$url"
    }
}
