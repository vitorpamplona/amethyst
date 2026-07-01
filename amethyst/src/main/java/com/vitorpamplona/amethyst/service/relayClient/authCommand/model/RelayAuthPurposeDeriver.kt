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
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent

/**
 * Infers *why* a relay wants NIP-42 auth from what Amethyst is currently doing with it — the
 * events pending delivery and the active subscription filters (both from the [INostrClient]).
 * Pure so it can be unit-tested without the relay client.
 *
 * - a pending gift wrap (kind 1059) => we're sending a DM to its `p` recipient ([AuthPurposeKind.SEND_DM]);
 * - any other pending event carrying `p` tags => we're delivering it to those users' inboxes
 *   ([AuthPurposeKind.NOTIFY_INBOX]);
 * - a subscription filter with `authors` => we're reading those authors' posts ([AuthPurposeKind.READ_OUTBOX]).
 */
object RelayAuthPurposeDeriver {
    fun derive(
        pendingEvents: List<Event>,
        activeFilters: Map<String, List<Filter>>,
    ): List<AuthPurpose> {
        val dmRecipients = mutableSetOf<HexKey>()
        val notifyRecipients = mutableSetOf<HexKey>()
        pendingEvents.forEach { event ->
            val pTags = event.tags.mapNotNullTo(mutableSetOf()) { if (it.size > 1 && it[0] == "p") it[1] else null }
            if (event.kind == GiftWrapEvent.KIND) {
                dmRecipients.addAll(pTags)
            } else {
                // We're notifying the people the event references, not its author — drop the
                // author's own key so a self-p-tag doesn't read as "notify yourself".
                notifyRecipients.addAll(pTags - event.pubKey)
            }
        }

        val readAuthors = mutableSetOf<HexKey>()
        activeFilters.values.forEach { filters ->
            filters.forEach { filter -> filter.authors?.let(readAuthors::addAll) }
        }

        return buildList {
            if (dmRecipients.isNotEmpty()) add(AuthPurpose(AuthPurposeKind.SEND_DM, dmRecipients))
            if (notifyRecipients.isNotEmpty()) add(AuthPurpose(AuthPurposeKind.NOTIFY_INBOX, notifyRecipients))
            if (readAuthors.isNotEmpty()) add(AuthPurpose(AuthPurposeKind.READ_OUTBOX, readAuthors))
        }
    }
}
