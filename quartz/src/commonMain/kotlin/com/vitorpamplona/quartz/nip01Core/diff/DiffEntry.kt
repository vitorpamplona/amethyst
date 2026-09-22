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
package com.vitorpamplona.quartz.nip01Core.diff

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * One meaningful item of an event, as seen when comparing two versions of it: a followed
 * person, a relay, a muted word, a profile field…
 *
 * Every entry has a [key], its identity inside the event. Two versions of an event are
 * compared by key: a key only in the older one was removed, a key only in the newer one was
 * added, and a key in both whose entries are not equal was changed (a new relay marker, an
 * edited bio, a different relay hint).
 *
 * Events decide how their own tags and content become entries by overriding
 * [com.vitorpamplona.quartz.nip01Core.core.Event.diffEntry] and
 * [com.vitorpamplona.quartz.nip01Core.core.Event.diffEntries].
 */
@Immutable
sealed interface DiffEntry {
    val key: String

    /** A kind:0 profile field, e.g. `name`, `about`, `lud16`. */
    data class ProfileField(
        val name: String,
        val value: String,
    ) : DiffEntry {
        override val key get() = "field:$name"
    }

    /** A `p` tag: a followed, muted or listed person. */
    data class Person(
        val pubKey: HexKey,
        val relayHint: String? = null,
        val petName: String? = null,
    ) : DiffEntry {
        override val key get() = "p:$pubKey"
    }

    /** A `t` tag. */
    data class Hashtag(
        val hashtag: String,
    ) : DiffEntry {
        override val key get() = "t:$hashtag"
    }

    /** A `word` tag (NIP-51 mute lists). */
    data class Word(
        val word: String,
    ) : DiffEntry {
        override val key get() = "word:$word"
    }

    /** A `g` tag. */
    data class Geohash(
        val geohash: String,
    ) : DiffEntry {
        override val key get() = "g:$geohash"
    }

    /**
     * An `r` or `relay` tag. NIP-65 relays can be limited to [read] or [write]; a relay
     * without a marker is both.
     */
    data class Relay(
        val url: String,
        val read: Boolean = true,
        val write: Boolean = true,
    ) : DiffEntry {
        override val key get() = "relay:$url"
    }

    /** An `e` tag: a muted thread, a joined public chat, a bookmarked note… */
    data class EventRef(
        val eventId: HexKey,
        val relayHint: String? = null,
    ) : DiffEntry {
        override val key get() = "e:$eventId"
    }

    /** An `a` tag: a community, a feed, an addressable item. */
    data class AddressRef(
        val address: String,
        val relayHint: String? = null,
    ) : DiffEntry {
        override val key get() = "a:$address"
    }

    /** A NIP-29 relay-based group the user joined. */
    data class RelayGroup(
        val groupId: String,
        val relay: String,
        val name: String? = null,
    ) : DiffEntry {
        override val key get() = "group:$groupId@$relay"
    }

    /** An ephemeral chat room the user joined. */
    data class ChatRoom(
        val roomId: String,
        val relay: String,
    ) : DiffEntry {
        override val key get() = "room:$roomId@$relay"
    }

    /** A NIP-85 trusted service provider: who computes [service] (e.g. `30382:rank`) for the user. */
    data class TrustProvider(
        val service: String,
        val pubKey: HexKey,
        val relay: String,
    ) : DiffEntry {
        override val key get() = "provider:$service:$pubKey"
    }

    /** A NIP-61 mint the user accepts nutzaps from. */
    data class Mint(
        val url: String,
        val units: List<String> = emptyList(),
    ) : DiffEntry {
        override val key get() = "mint:$url"
    }

    /** The NIP-61 key nutzaps are locked to. */
    data class NutzapKey(
        val pubKey: HexKey,
    ) : DiffEntry {
        override val key get() = "nutzap-key:$pubKey"
    }

    /** A NIP-A3 payment target, e.g. `bitcoin` + an address. */
    data class PaymentTarget(
        val type: String,
        val authority: String,
    ) : DiffEntry {
        override val key get() = "payto:$type:$authority"
    }

    /** A BOLT12 offer. */
    data class Bolt12Offer(
        val offer: String,
    ) : DiffEntry {
        override val key get() = "offer:$offer"
    }

    /** Any other tag, kept as-is and identified by its name and first value. */
    data class OtherTag(
        val values: List<String>,
    ) : DiffEntry {
        val name get() = values.firstOrNull() ?: ""
        val value get() = values.getOrNull(1) ?: ""

        override val key get() = "tag:$name:$value"
    }
}
