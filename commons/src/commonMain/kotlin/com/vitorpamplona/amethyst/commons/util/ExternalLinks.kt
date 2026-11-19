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
package com.vitorpamplona.amethyst.commons.util

import com.vitorpamplona.amethyst.commons.defaults.Constants
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * An `https://njump.to/…` link for a NIP-19 bech32 entity, for handing a nostr event to
 * something that does not speak nostr — a share sheet, a browser, or a log line meant to be
 * opened by a human.
 */
fun njumpLink(nip19BechAddress: String) = "https://njump.to/$nip19BechAddress"

/**
 * Where Amethyst's Share links point, and the relay that site's pages read from. Every share link
 * and every copy made for one goes through here, so moving to another provider is one edit.
 *
 * Brainstorm takes any NIP-19 entity at its root (`/nevent1…`, `/naddr1…`, `/nprofile1…`, `/npub1…`,
 * `/note1…`) and redirects it to its event (`/e/`) or profile (`/p/`) page; links keep the nevent and
 * nprofile forms because those carry relay hints. Its crawler previews (the card a chat app or social
 * site shows for a pasted link) are filled in for text notes and long-form posts; other kinds render
 * on the page but preview as Brainstorm's generic card.
 */
object ShareSite {
    const val WEB = "https://brainstorm.world"

    /** The relay Brainstorm's pages read, where Amethyst copies what it shares (see `ShareRelayPolicy`). */
    val RELAYS: Set<NormalizedRelayUrl> = setOf(Constants.brainstorm)

    /** The page for a NIP-19 entity: an event (nevent, naddr, note1) or a profile (nprofile, npub). */
    fun link(nip19BechAddress: String) = "$WEB/$nip19BechAddress"
}
