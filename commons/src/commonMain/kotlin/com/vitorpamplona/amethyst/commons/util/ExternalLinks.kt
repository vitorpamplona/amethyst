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

/**
 * An `https://njump.to/…` link for a NIP-19 bech32 entity, for handing a nostr event to
 * something that does not speak nostr — a share sheet, a browser, or a log line meant to be
 * opened by a human.
 */
fun njumpLink(nip19BechAddress: String) = "https://njump.to/$nip19BechAddress"

/**
 * The web page Amethyst's Share buttons hand out for an event: Brainstorm's `/e/` page, which takes
 * an nevent, naddr or note1 and renders a link preview for chat apps and social sites. Amethyst
 * copies the shared event to Brainstorm's relay first, so the page can always find it (see
 * `EventBroadcaster.pushToShareRelay`).
 */
fun shareLinkForEvent(nip19BechAddress: String) = "https://brainstorm.world/e/$nip19BechAddress"

/** The web page for a profile: Brainstorm's `/p/` page, for an npub or nprofile. */
fun shareLinkForProfile(nip19BechAddress: String) = "https://brainstorm.world/p/$nip19BechAddress"
