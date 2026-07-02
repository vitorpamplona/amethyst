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
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent

/** Addressable venue kinds whose home relay may require auth: NIP-72 communities and NIP-53 live
 *  activities. Their `a` addresses are `kind:ownerPubkey:dTag`. */
private val VENUE_KINDS = setOf(CommunityDefinitionEvent.KIND, LiveActivitiesEvent.KIND)

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
            val pubkeys = event.tags.mapNotNull(PTag::parseKey)
            val venues = event.tags.mapNotNull(ATag::parseAddress).filter { it.kind in VENUE_KINDS }
            when {
                event.kind == GiftWrapEvent.KIND -> dmRecipients.addAll(pubkeys)
                event.kind == ChannelMessageEvent.KIND -> event.tags.channelRootId()?.let(postVenues::add)
                venues.isNotEmpty() -> venues.forEach { postVenues.add(it.toValue()) }
                pubkeys.isNotEmpty() -> notifyRecipients.addAll(pubkeys - event.pubKey)
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
                filter.tags
                    ?.get("a")
                    ?.mapNotNull { Address.parse(it) }
                    ?.filter { it.kind in VENUE_KINDS }
                    ?.takeIf { it.isNotEmpty() }
                    ?.let {
                        readVenues.addAll(it.map(Address::toValue))
                        matched = true
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
    private fun Array<Array<String>>.channelRootId(): HexKey? = firstNotNullOfOrNull(MarkedETag::parseRoot)?.eventId ?: firstNotNullOfOrNull(ETag::parseId)
}
