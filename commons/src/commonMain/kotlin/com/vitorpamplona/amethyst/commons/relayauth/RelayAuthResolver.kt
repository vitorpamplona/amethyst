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
 * Everything the resolver needs to decide an auth challenge, gathered by the host (which owns
 * the blocked-relay list, the user's relay lists, and the follow graph). Kept as plain values
 * so the decision itself is pure and unit-testable without any account/relay wiring.
 *
 * @param storedOverride an explicit per-relay decision the user set previously, or null.
 * @param isBlocked the relay is on the user's blocked-relay list (kind 10006).
 * @param policy the global [RelayAuthPolicy].
 * @param isInMyRelayList the relay is in the user's own relay list.
 * @param servesFollowedWriteCounterparty a followed user is a counterparty of a *write* purpose
 *   (send DM / deliver notification) for this relay.
 * @param servesFollowedReadCounterparty a followed user is a counterparty of a *read* purpose
 *   (download their outbox) for this relay.
 * @param readTrustEnabled the "also trust follows' outboxes when reading" sub-toggle.
 * @param hasAttributablePurpose we know *why* this relay wants auth (so a prompt can explain it).
 *   When false, an unresolved challenge is denied silently rather than prompting.
 */
data class RelayAuthInputs(
    val storedOverride: RelayAuthDecision?,
    val isBlocked: Boolean,
    val policy: RelayAuthPolicy,
    val isInMyRelayList: Boolean,
    val servesFollowedWriteCounterparty: Boolean,
    val servesFollowedReadCounterparty: Boolean,
    val readTrustEnabled: Boolean,
    val hasAttributablePurpose: Boolean,
)

/**
 * Pure NIP-42 auth decision. Precedence, highest first:
 *
 * 1. Blocked-relay list → [RelayAuthVerdict.DENY] (never reveal identity to a blocked relay).
 * 2. Explicit per-relay override → honor it.
 * 3. Global [RelayAuthPolicy]:
 *    - [RelayAuthPolicy.NEVER] → DENY
 *    - [RelayAuthPolicy.ALWAYS] → ALLOW
 *    - [RelayAuthPolicy.IF_IN_MY_LIST] → ALLOW if in my list, else fall through
 *    - [RelayAuthPolicy.TRUSTED_FOLLOWS] → ALLOW if in my list, or a followed counterparty is
 *      served for a write purpose (DM/notification), or (when [RelayAuthInputs.readTrustEnabled])
 *      for a read purpose; else fall through
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
            RelayAuthPolicy.IF_IN_MY_LIST ->
                if (inputs.isInMyRelayList) RelayAuthVerdict.ALLOW else fallThrough(inputs)
            RelayAuthPolicy.TRUSTED_FOLLOWS ->
                if (inputs.isInMyRelayList ||
                    inputs.servesFollowedWriteCounterparty ||
                    (inputs.readTrustEnabled && inputs.servesFollowedReadCounterparty)
                ) {
                    RelayAuthVerdict.ALLOW
                } else {
                    fallThrough(inputs)
                }
        }
    }

    private fun fallThrough(inputs: RelayAuthInputs): RelayAuthVerdict = if (inputs.hasAttributablePurpose) RelayAuthVerdict.ASK else RelayAuthVerdict.DENY
}
