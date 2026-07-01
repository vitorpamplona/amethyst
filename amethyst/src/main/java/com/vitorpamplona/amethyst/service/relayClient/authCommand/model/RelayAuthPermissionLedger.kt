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

import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPermissionStore
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPolicy

/**
 * Decides whether Amethyst should authenticate with a given relay (NIP-42).
 *
 * Decision order:
 * 1. Per-relay override stored in [store] — always wins.
 * 2. [globalPolicy]:
 *    - [RelayAuthPolicy.ALWAYS] → [RelayAuthDecision.ALLOW]
 *    - [RelayAuthPolicy.NEVER] → [RelayAuthDecision.DENY]
 *    - [RelayAuthPolicy.IF_IN_MY_LIST] → [RelayAuthDecision.ALLOW] iff [isInMyRelayList] returns true.
 */
class RelayAuthPermissionLedger(
    val store: RelayAuthPermissionStore,
    val globalPolicy: () -> RelayAuthPolicy,
    val isInMyRelayList: (String) -> Boolean = { false },
) {
    /** The authorization verdict for [relayUrl]. */
    suspend fun decide(relayUrl: String): RelayAuthDecision {
        store.loadDecision(relayUrl)?.let { return it }
        return when (globalPolicy()) {
            RelayAuthPolicy.ALWAYS -> RelayAuthDecision.ALLOW
            RelayAuthPolicy.NEVER -> RelayAuthDecision.DENY
            RelayAuthPolicy.IF_IN_MY_LIST ->
                if (isInMyRelayList(relayUrl)) RelayAuthDecision.ALLOW else RelayAuthDecision.DENY
            // This URL-only entry point has no purpose/counterparty context, so it can only
            // apply the "in my list" half of TRUSTED_FOLLOWS. The follow-graph half runs in the
            // context-aware path (RelayAuthResolver) once the challenge purpose is known.
            RelayAuthPolicy.TRUSTED_FOLLOWS ->
                if (isInMyRelayList(relayUrl)) RelayAuthDecision.ALLOW else RelayAuthDecision.DENY
        }
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
