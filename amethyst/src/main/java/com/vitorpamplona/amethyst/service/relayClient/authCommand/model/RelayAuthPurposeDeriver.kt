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
package com.vitorpamplona.amethyst.service.relayClient.authCommand.model

import com.vitorpamplona.amethyst.commons.relayauth.AuthPurpose
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurposeKind
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent

/** `a`-address prefixes of venues whose home relay may require auth: NIP-72 communities (34550) and
 *  NIP-53 live activities (30311). Their addresses are `kind:ownerPubkey:dTag`. */
private val VENUE_ADDRESS_PREFIXES = listOf("34550:", "30311:")

/**
 * Infers *why* a relay wants NIP-42 auth from what Amethyst is currently doing with it — the
 * events pending delivery and the active subscription filters (both from the [INostrClient]).
 * Pure so it can be unit-tested without the relay client.
 *
 * - a pending gift wrap (kind 1059) => sending a DM to its `p` recipient ([AuthPurposeKind.SEND_DM]);
 * - a pending channel/community/live post => [AuthPurposeKind.POST_VENUE] for that venue;
 * - any other pending event with `p` tags => delivering it to those users' inboxes ([AuthPurposeKind.NOTIFY_INBOX]);
 * - a filter with `authors` => reading those authors' posts ([AuthPurposeKind.READ_OUTBOX]);
 * - a filter with `#e`/`#a` venue tags => reading a venue ([AuthPurposeKind.READ_VENUE]);
 * - anything else we're actively doing but can't attribute => [AuthPurposeKind.OTHER], so the relay
 *   is prompted about instead of silently failing.
 */
object RelayAuthPurposeDeriver {
    fun derive(
        pendingEvents: List<Event>,
        activeFilters: Map<String, List<Filter>>,
    ): List<AuthPurpose> {
        val dmRecipients = mutableSetOf<HexKey>()
        val notifyRecipients = mutableSetOf<HexKey>()
        val postVenues = mutableSetOf<String>()
        var unattributedWrite = false

        pendingEvents.forEach { event ->
            val pTags = event.tags.mapNotNullTo(mutableSetOf()) { if (it.size > 1 && it[0] == "p") it[1] else null }
            val venueAddresses = event.tags.venueAddresses()
            when {
                event.kind == GiftWrapEvent.KIND -> dmRecipients.addAll(pTags)
                event.kind == ChannelMessageEvent.KIND -> event.channelRootId()?.let { postVenues.add(it) }
                venueAddresses.isNotEmpty() -> postVenues.addAll(venueAddresses)
                pTags.isNotEmpty() -> notifyRecipients.addAll(pTags - event.pubKey)
                else -> unattributedWrite = true
            }
        }

        val readAuthors = mutableSetOf<HexKey>()
        val readVenues = mutableSetOf<String>()
        var unattributedRead = false
        activeFilters.values.forEach { filters ->
            filters.forEach { filter ->
                var matched = false
                filter.authors?.let {
                    readAuthors.addAll(it)
                    matched = true
                }
                filter.tags?.get("e")?.let {
                    readVenues.addAll(it)
                    matched = true
                }
                filter.tags?.get("a")?.filter { addr -> VENUE_ADDRESS_PREFIXES.any(addr::startsWith) }?.let {
                    if (it.isNotEmpty()) {
                        readVenues.addAll(it)
                        matched = true
                    }
                }
                if (!matched) unattributedRead = true
            }
        }

        return buildList {
            if (dmRecipients.isNotEmpty()) add(AuthPurpose(AuthPurposeKind.SEND_DM, dmRecipients))
            if (notifyRecipients.isNotEmpty()) add(AuthPurpose(AuthPurposeKind.NOTIFY_INBOX, notifyRecipients))
            if (postVenues.isNotEmpty()) add(AuthPurpose(AuthPurposeKind.POST_VENUE, venues = postVenues))
            if (readAuthors.isNotEmpty()) add(AuthPurpose(AuthPurposeKind.READ_OUTBOX, readAuthors))
            if (readVenues.isNotEmpty()) add(AuthPurpose(AuthPurposeKind.READ_VENUE, venues = readVenues))
            // Safety net: we're using this relay but couldn't say how — prompt rather than fail silently.
            if (isEmpty() && (unattributedWrite || unattributedRead)) add(AuthPurpose(AuthPurposeKind.OTHER))
        }
    }

    /** The venue a channel message posts into: the `e` tag marked "root", else the first `e` tag. */
    private fun Event.channelRootId(): HexKey? {
        val eTags = tags.filter { it.size > 1 && it[0] == "e" }
        return eTags.firstOrNull { it.size > 3 && it[3] == "root" }?.get(1) ?: eTags.firstOrNull()?.get(1)
    }

    private fun Array<Array<String>>.venueAddresses(): List<String> = mapNotNull { if (it.size > 1 && it[0] == "a" && VENUE_ADDRESS_PREFIXES.any(it[1]::startsWith)) it[1] else null }
}
