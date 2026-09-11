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
package com.vitorpamplona.quartz.nip51Lists

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/** The new relay set of a NIP-51 list, split by where each relay must be written. */
class RelayListSplit(
    val publicRelays: List<NormalizedRelayUrl>,
    val privateRelays: List<NormalizedRelayUrl>,
)

/**
 * Splits the new relay set of a NIP-51 relay list into the relays that go into plain tags and
 * the relays that go into the NIP-44 encrypted content, keeping each one where the earlier
 * version already had it. Relays the earlier version didn't have follow its convention: public
 * when it only had public relays, private otherwise.
 *
 * Rewriting every entry as a private tag instead empties the list out for clients that only
 * read the plain tags, which is how removing a single relay used to wipe a list created
 * elsewhere. See https://github.com/vitorpamplona/amethyst/issues/4075
 */
fun splitRelayListUpdate(
    publicRelaysBefore: List<NormalizedRelayUrl>,
    privateRelaysBefore: List<NormalizedRelayUrl>,
    relays: List<NormalizedRelayUrl>,
): RelayListSplit {
    val wasPublic = publicRelaysBefore.toSet()
    val wasPrivate = privateRelaysBefore.toSet()
    val newOnesArePublic = wasPublic.isNotEmpty() && wasPrivate.isEmpty()

    val publicRelays = ArrayList<NormalizedRelayUrl>(relays.size)
    val privateRelays = ArrayList<NormalizedRelayUrl>(relays.size)

    relays.forEach { relay ->
        val isPublic =
            when {
                relay in wasPublic -> true
                relay in wasPrivate -> false
                else -> newOnesArePublic
            }

        if (isPublic) publicRelays.add(relay) else privateRelays.add(relay)
    }

    return RelayListSplit(publicRelays, privateRelays)
}
