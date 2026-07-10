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
package com.vitorpamplona.amethyst.commons.relayauth

/**
 * The per-situation switches applied under [RelayAuthPolicy.CUSTOM]. Each independently authorizes
 * one category of relay; a situation with no matching toggle falls through to a prompt.
 *
 * @param myRelaysAndVenues your own relays, plus venues (public chats, communities, live streams)
 *   you've joined, subscribed to, or favorited.
 * @param readFollows a relay serving the outbox of someone you follow (to download their posts).
 * @param messageFollows a relay serving the inbox of someone you follow (to send DMs, replies,
 *   notifications).
 * @param messageStrangers a relay serving the inbox of someone you *don't* follow. Off by default —
 *   sending to a stranger otherwise prompts.
 */
data class RelayAuthCustomToggles(
    val myRelaysAndVenues: Boolean = true,
    val readFollows: Boolean = true,
    val messageFollows: Boolean = true,
    val messageStrangers: Boolean = false,
)

/**
 * Everything the resolver needs to decide an auth challenge, gathered by the host (which owns
 * the blocked-relay list, the user's relay lists, and the follow graph). Kept as plain values
 * so the decision itself is pure and unit-testable without any account/relay wiring.
 *
 * @param storedOverride an explicit per-relay decision the user set previously, or null.
 * @param isBlocked the relay is on the user's blocked-relay list (kind 10006).
 * @param policy the top-level [RelayAuthPolicy].
 * @param toggles the [RelayAuthCustomToggles] applied when [policy] is [RelayAuthPolicy.CUSTOM].
 * @param isInMyRelayList the relay is in the user's own relay list.
 * @param servesTrustedVenue this relay hosts a venue (public chat, community, or live stream) the
 *   user has joined, subscribed to, or favorited.
 * @param servesFollowedReadCounterparty a followed user's outbox is served here (reading them).
 * @param servesFollowedWriteCounterparty a followed user's inbox is served here (messaging them).
 * @param servesStrangerWriteCounterparty a non-followed user's inbox is served here (messaging them).
 * @param hasAttributablePurpose we know *why* this relay wants auth (so a prompt can explain it).
 *   When false, an unresolved challenge is denied silently rather than prompting.
 */
data class RelayAuthInputs(
    val storedOverride: RelayAuthDecision?,
    val isBlocked: Boolean,
    val policy: RelayAuthPolicy,
    val toggles: RelayAuthCustomToggles,
    val isInMyRelayList: Boolean,
    val servesTrustedVenue: Boolean,
    val servesFollowedReadCounterparty: Boolean,
    val servesFollowedWriteCounterparty: Boolean,
    val servesStrangerWriteCounterparty: Boolean,
    val hasAttributablePurpose: Boolean,
)

/**
 * Pure NIP-42 auth decision. Precedence, highest first:
 *
 * 1. Blocked-relay list → [RelayAuthVerdict.DENY] (never reveal identity to a blocked relay).
 * 2. Explicit per-relay override → honor it.
 * 3. Top-level [RelayAuthPolicy]:
 *    - [RelayAuthPolicy.NEVER] → DENY
 *    - [RelayAuthPolicy.ALWAYS] → ALLOW
 *    - [RelayAuthPolicy.CUSTOM] → ALLOW if any *enabled* [RelayAuthCustomToggles] category matches
 *      this relay (own relays/venues, reading follows, messaging follows, messaging strangers);
 *      else fall through
 * 4. Fall-through → [RelayAuthVerdict.ASK] when the purpose is known, otherwise DENY.
 */
object RelayAuthResolver {
    fun resolve(inputs: RelayAuthInputs): RelayAuthVerdict {
        if (inputs.isBlocked) return RelayAuthVerdict.DENY

        inputs.storedOverride?.let {
            return when (it) {
                RelayAuthDecision.ALLOW -> RelayAuthVerdict.ALLOW
                RelayAuthDecision.DENY -> RelayAuthVerdict.DENY
            }
        }

        return when (inputs.policy) {
            RelayAuthPolicy.NEVER -> RelayAuthVerdict.DENY
            RelayAuthPolicy.ALWAYS -> RelayAuthVerdict.ALLOW
            RelayAuthPolicy.CUSTOM ->
                if (customAllows(inputs)) RelayAuthVerdict.ALLOW else fallThrough(inputs)
        }
    }

    private fun customAllows(inputs: RelayAuthInputs): Boolean {
        val t = inputs.toggles
        return (t.myRelaysAndVenues && (inputs.isInMyRelayList || inputs.servesTrustedVenue)) ||
            (t.readFollows && inputs.servesFollowedReadCounterparty) ||
            (t.messageFollows && inputs.servesFollowedWriteCounterparty) ||
            (t.messageStrangers && inputs.servesStrangerWriteCounterparty)
    }

    private fun fallThrough(inputs: RelayAuthInputs): RelayAuthVerdict = if (inputs.hasAttributablePurpose) RelayAuthVerdict.ASK else RelayAuthVerdict.DENY
}
