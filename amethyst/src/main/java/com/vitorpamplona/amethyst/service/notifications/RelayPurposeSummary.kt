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
package com.vitorpamplona.amethyst.service.notifications

import android.content.Context
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.purposes
import com.vitorpamplona.amethyst.ui.pluralStringRes
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * The per-job breakdown behind the always-on notification's relay count.
 *
 * **Only ever shown expanded.** The collapsed line stays exactly what it was — a count — because
 * that is all most people ever want from an ongoing notification. This is for the moment someone
 * taps to ask *why* their phone is talking to 40 relays.
 *
 * A relay usually serves several jobs at once (measured: a typical relay carries four), so these
 * counts deliberately **overlap and sum to more than the relay count**. They answer "how many relays
 * carry my DMs", not "how is the pool partitioned" — there is no partition.
 *
 * Purposes with no label — feeds and whatever screen is open — collapse into one "browsing" line.
 * Those disappear on their own once the app is backgrounded, which is exactly when this notification
 * matters most, so spelling them out would add noise precisely when the user is least interested.
 */
object RelayPurposeSummary {
    /** The jobs worth naming to a user. Everything else is browsing, and transient. */
    private fun labelOf(purpose: SubPurpose): Int? =
        when (purpose) {
            SubPurpose.NOTIFICATIONS -> R.string.relay_purpose_notifications
            SubPurpose.DIRECT_MESSAGES -> R.string.relay_purpose_direct_messages
            SubPurpose.PUBLIC_CHATS -> R.string.relay_purpose_public_chats
            SubPurpose.COMMUNITY_CHATS -> R.string.relay_purpose_community_chats
            SubPurpose.ENCRYPTED_GROUPS -> R.string.relay_purpose_encrypted_groups
            SubPurpose.LIVE_ROOMS -> R.string.relay_purpose_live_rooms
            SubPurpose.ACCOUNT_DATA -> R.string.relay_purpose_account_data
            SubPurpose.PROFILE_METADATA -> R.string.relay_purpose_profiles
            SubPurpose.RELAY_LISTS -> R.string.relay_purpose_relay_lists
            SubPurpose.FOLLOW_LISTS -> R.string.relay_purpose_follows
            SubPurpose.MODERATION -> R.string.relay_purpose_moderation
            SubPurpose.WALLET -> R.string.relay_purpose_wallet
            else -> null
        }

    /**
     * Lines for the expanded notification, busiest first. Empty when nothing is attributed yet —
     * the caller must then fall back to the bare count rather than render an empty section.
     */
    fun lines(ctx: Context): List<String> {
        val client = Amethyst.instance.client
        val named = mutableMapOf<SubPurpose, MutableSet<NormalizedRelayUrl>>()
        val browsing = mutableSetOf<NormalizedRelayUrl>()

        client.connectedRelaysFlow().value.forEach { relay ->
            client
                .activeRequests(relay)
                .values
                .flatten()
                .purposes()
                .forEach { purpose ->
                    if (labelOf(purpose) != null) {
                        named.getOrPut(purpose) { mutableSetOf() }.add(relay)
                    } else {
                        browsing.add(relay)
                    }
                }
        }

        val lines =
            named.entries
                .sortedWith(compareByDescending<Map.Entry<SubPurpose, Set<NormalizedRelayUrl>>> { it.value.size }.thenBy { it.key.name })
                .map { (purpose, relays) ->
                    pluralStringRes(ctx, R.plurals.relay_purpose_line, relays.size, ctx.getString(labelOf(purpose)!!), relays.size)
                }.toMutableList()

        if (browsing.isNotEmpty()) {
            lines.add(pluralStringRes(ctx, R.plurals.relay_purpose_line, browsing.size, ctx.getString(R.string.relay_purpose_browsing), browsing.size))
        }
        return lines
    }
}
