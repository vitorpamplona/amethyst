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
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.purposes
import com.vitorpamplona.amethyst.shared.R
import com.vitorpamplona.amethyst.ui.pluralStringRes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.SubPurposeLabels
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * The per-job breakdown behind the always-on notification's relay count.
 *
 * **Only ever shown on request.** The card stays exactly what it was — a count — because that is all
 * most people ever want from an ongoing notification. This is for the moment someone taps
 * "show details" to ask *why* their phone is talking to 40 relays. It is not attached to the
 * notification otherwise: Android auto-expands a lone notification, so anything hung off the
 * expanded view alone would be the default view rather than an opt-in one.
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
                    if (SubPurposeLabels.isWorthNamingInNotification(purpose)) {
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
                    pluralStringRes(ctx, R.plurals.relay_purpose_line, relays.size, ctx.getString(SubPurposeLabels.labelOf(purpose)), relays.size)
                }.toMutableList()

        if (browsing.isNotEmpty()) {
            lines.add(pluralStringRes(ctx, R.plurals.relay_purpose_line, browsing.size, ctx.getString(R.string.relay_purpose_browsing), browsing.size))
        }
        return lines
    }
}
