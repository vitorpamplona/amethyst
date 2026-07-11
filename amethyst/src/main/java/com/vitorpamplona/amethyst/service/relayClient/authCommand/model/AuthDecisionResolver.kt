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
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthVerdict

/**
 * Combines every logged-in account's [RelayAuthVerdict] into a single decision for whether to
 * authenticate with a relay — and, when the user is asked, what to remember. Pulled out of
 * [AuthCoordinator] so the policy is unit-testable without the relay client, signers, or Compose.
 */
object AuthDecisionResolver {
    /**
     * @param shouldAuth whether to sign the NIP-42 auth challenge.
     * @param remember a per-relay override to persist ([RelayAuthDecision.ALLOW]/[RelayAuthDecision.DENY]),
     *   or null to leave the relay's stored decision untouched.
     */
    data class Outcome(
        val shouldAuth: Boolean,
        val remember: RelayAuthDecision? = null,
    )

    /**
     * Resolves the combined verdict:
     * - **No verdicts** (no ledgers watching) => auto-authenticate; the caller has no policy to apply.
     * - **Any [RelayAuthVerdict.ALLOW]** => authenticate without asking.
     * - **Any [RelayAuthVerdict.ASK]** (and none allow) => call [prompt] and act on the user's choice,
     *   remembering ALLOW/DENY for Always-allow / Block.
     * - **Otherwise** (all DENY) => do not authenticate.
     *
     * [prompt] is only invoked in the ASK case, so the no-op paths never build a dialog.
     */
    suspend fun resolve(
        verdicts: List<RelayAuthVerdict>,
        prompt: suspend () -> UserAuthChoice,
    ): Outcome =
        when {
            verdicts.isEmpty() -> Outcome(shouldAuth = true)
            verdicts.any { it == RelayAuthVerdict.ALLOW } -> Outcome(shouldAuth = true)
            verdicts.any { it == RelayAuthVerdict.ASK } ->
                when (prompt()) {
                    UserAuthChoice.ALLOW_ONCE -> Outcome(shouldAuth = true)
                    UserAuthChoice.ALWAYS_ALLOW -> Outcome(shouldAuth = true, remember = RelayAuthDecision.ALLOW)
                    UserAuthChoice.BLOCK -> Outcome(shouldAuth = false, remember = RelayAuthDecision.DENY)
                    UserAuthChoice.DISMISS -> Outcome(shouldAuth = false)
                }
            else -> Outcome(shouldAuth = false)
        }
}
