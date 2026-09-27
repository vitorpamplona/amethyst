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
package com.vitorpamplona.quartz.nip61Nutzaps.redemption

import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Builds a NIP-61 nutzap redemption: a NIP-60 spending history event (kind 7376)
 * that publicly marks [nutzap] as redeemed and notifies its sender.
 *
 * [encryptedContent] is the NIP-44 encrypted history payload (direction, amount, ...).
 */
fun CashuSpendingHistoryEvent.Companion.buildNutzapRedemption(
    nutzap: EventHintBundle<NutzapEvent>,
    encryptedContent: String,
    createdAt: Long = TimeUtils.now(),
    initializer: TagArrayBuilder<CashuSpendingHistoryEvent>.() -> Unit = {},
) = eventTemplate(KIND, encryptedContent, createdAt) {
    redeemedNutzap(nutzap)
    notifySender(nutzap)
    initializer()
}

/** The old `NutzapRedemptionEvent.build`. */
@Deprecated(
    "Use CashuSpendingHistoryEvent.buildNutzapRedemption.",
    ReplaceWith(
        "CashuSpendingHistoryEvent.buildNutzapRedemption(nutzap, encryptedContent, createdAt, initializer)",
        "com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent",
        "com.vitorpamplona.quartz.nip61Nutzaps.redemption.buildNutzapRedemption",
    ),
)
fun CashuSpendingHistoryEvent.Companion.build(
    nutzap: EventHintBundle<NutzapEvent>,
    encryptedContent: String,
    createdAt: Long = TimeUtils.now(),
    initializer: TagArrayBuilder<CashuSpendingHistoryEvent>.() -> Unit = {},
) = buildNutzapRedemption(nutzap, encryptedContent, createdAt, initializer)

/**
 * NIP-61 redemptions are NIP-60 spending history events (kind 7376). This used to be a
 * second class for the same kind, which `EventFactory` could never instantiate because
 * [CashuSpendingHistoryEvent] matched first.
 */
@Deprecated(
    "Kind 7376 is the NIP-60 spending history event. Use CashuSpendingHistoryEvent and CashuSpendingHistoryEvent.buildNutzapRedemption.",
    ReplaceWith("CashuSpendingHistoryEvent", "com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent"),
)
typealias NutzapRedemptionEvent = CashuSpendingHistoryEvent
