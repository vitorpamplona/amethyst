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
package com.vitorpamplona.quartz.podcasts

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import kotlinx.serialization.Serializable

/**
 * A Podcasting-2.0 `podcast:person` — a host, guest, or other contributor credited on a show or a
 * single episode. Unlike a NIP-F4 author (a Nostr pubkey), a person is free-text: a [name] plus an
 * optional [role]/[group], an avatar [img] URL, and a [href] link to their page. None of these need
 * to be a Nostr identity, so it maps RSS `<podcast:person>` credits straight through.
 *
 * Carried two ways: show-level persons live in the `kind:30078` metadata JSON (a `persons` array),
 * and episode-level persons are `["person", ...]` tags on the `kind:30054` event.
 */
@Immutable
@Serializable
class PodcastPerson(
    val name: String = "",
    /** e.g. "host", "guest", "cohost" — free text per the Podcasting 2.0 taxonomy. */
    val role: String? = null,
    /** Grouping such as "cast" or "writing"; rarely displayed, kept for round-trip fidelity. */
    val group: String? = null,
    /** Avatar image URL. */
    val img: String? = null,
    /** Link to the person's page/profile. */
    val href: String? = null,
) {
    fun isValid() = name.isNotBlank()

    /**
     * The Nostr pubkey (hex) this person points at, when [href] is (or embeds) an `npub`/`nprofile`
     * — including `nostr:` URIs and `njump.me`-style links. Null for a plain web link or no href.
     * Lets a client upgrade a free-text credit to a real Nostr profile when the publisher linked one.
     */
    fun nostrPubKey(): HexKey? =
        href?.let {
            when (val entity = Nip19Parser.uriToRoute(it)?.entity) {
                is NPub -> entity.hex
                is NProfile -> entity.hex
                else -> null
            }
        }
}
