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

import com.vitorpamplona.amethyst.commons.relayauth.AuthPurposeKind
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthContext
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthCustomToggles
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthInputs
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPermissionStore
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPolicy
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthResolver
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthVerdict

/**
 * Decides whether Amethyst should authenticate with a given relay (NIP-42), for one account.
 *
 * Precedence (see [RelayAuthResolver]): blocked-relay list → per-relay override → global
 * [globalPolicy] → prompt-if-attributable-else-deny. Under [RelayAuthPolicy.CUSTOM] the
 * [customToggles] gate each category, using [isFollowed] to split the counterparties carried in the
 * [RelayAuthContext] into followed vs. stranger.
 */
class RelayAuthPermissionLedger(
    val store: RelayAuthPermissionStore,
    val globalPolicy: () -> RelayAuthPolicy,
    val customToggles: () -> RelayAuthCustomToggles = { RelayAuthCustomToggles() },
    val isInMyRelayList: (String) -> Boolean = { false },
    val isBlocked: (String) -> Boolean = { false },
    val isFollowed: (String) -> Boolean = { false },
    val isTrustedVenue: (String) -> Boolean = { false },
) {
    /** The authorization verdict for [ctx], taking the challenge's purpose into account. */
    suspend fun decide(ctx: RelayAuthContext): RelayAuthVerdict {
        fun isWrite(kind: AuthPurposeKind) = kind == AuthPurposeKind.SEND_DM || kind == AuthPurposeKind.NOTIFY_INBOX
        val inputs =
            RelayAuthInputs(
                storedOverride = store.loadDecision(ctx.relayUrl),
                isBlocked = isBlocked(ctx.relayUrl),
                policy = globalPolicy(),
                toggles = customToggles(),
                isInMyRelayList = isInMyRelayList(ctx.relayUrl),
                servesTrustedVenue =
                    ctx.purposes.any { p ->
                        (p.kind == AuthPurposeKind.POST_VENUE || p.kind == AuthPurposeKind.READ_VENUE) &&
                            p.venues.any(isTrustedVenue)
                    },
                // Reading a followed author's outbox.
                servesFollowedReadCounterparty =
                    ctx.purposes.any { p ->
                        p.kind == AuthPurposeKind.READ_OUTBOX && p.counterparties.any(isFollowed)
                    },
                // Messaging a followed user's inbox (DM / notification).
                servesFollowedWriteCounterparty =
                    ctx.purposes.any { p -> isWrite(p.kind) && p.counterparties.any(isFollowed) },
                // Messaging a non-followed user's inbox.
                servesStrangerWriteCounterparty =
                    ctx.purposes.any { p -> isWrite(p.kind) && p.counterparties.any { !isFollowed(it) } },
                hasAttributablePurpose =
                    ctx.purposes.any {
                        it.kind == AuthPurposeKind.MY_OWN_RELAY ||
                            it.kind == AuthPurposeKind.OTHER ||
                            it.counterparties.isNotEmpty() ||
                            it.venues.isNotEmpty()
                    },
            )
        return RelayAuthResolver.resolve(inputs)
    }

    /** Convenience for callers with no purpose context (e.g. a bare challenge). */
    suspend fun decide(relayUrl: String): RelayAuthVerdict = decide(RelayAuthContext(relayUrl))

    /**
     * Records why [ctx]'s relay was authenticated with, so the settings screen can show the
     * counterparties behind each grant. Only purposes that name counterparties are recorded.
     */
    suspend fun recordGrant(ctx: RelayAuthContext) {
        val additions =
            ctx.purposes
                .filter { it.counterparties.isNotEmpty() }
                .associate { it.kind to it.counterparties }
        if (additions.isNotEmpty()) store.recordUse(ctx.relayUrl, additions)
    }

    /** Stores a per-relay override for [relayUrl]. */
    suspend fun setDecision(
        relayUrl: String,
        decision: RelayAuthDecision,
    ) = store.storeDecision(relayUrl, decision)

    /** Removes the per-relay override for [relayUrl], reverting to the global policy. */
    suspend fun clearDecision(relayUrl: String) = store.clearDecision(relayUrl)

    /** All per-relay overrides — for the settings screen. */
    suspend fun allDecisions(): Map<String, RelayAuthDecision> = store.allDecisions()
}
