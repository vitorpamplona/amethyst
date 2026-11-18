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
package com.vitorpamplona.amethyst.connectedApps.consent

import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppConnectResult
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.amethyst.commons.model.nip46Signer.Nip46ConsentPrompter
import com.vitorpamplona.amethyst.commons.ui.connectedApps.consent.Nip46ConsentPrompts
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Shows a NIP-46 bunker's questions, worded by [Nip46ConsentPrompts], in the SAME dialogs the
 * napplet/browser signer path uses: [SignerConnectCoordinator] (first-connect trust picker) and
 * [SignerConsentCoordinator] (per-operation allow/deny). A remote app prompts through one consistent
 * surface, and the user's "remember" choices land in the same
 * [com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger].
 *
 * Runs only in the main process (the signer never runs in `:napplet`), so [Amethyst.instance] is set;
 * the coordinators launch their Activity from the application context.
 */
object Nip46ConsentBridge : Nip46ConsentPrompter by Nip46ConsentPrompts(
    clientStore = { Amethyst.instance.nip46ClientStore },
    // Fail closed if a prompt is never answered, so a stuck dialog can't hold the signer hostage.
    showConnect = { info ->
        withTimeoutOrNull(CONSENT_TIMEOUT_MS) {
            SignerConnectCoordinator.requestConnect(Amethyst.instance.appContext, info)
        } ?: AppConnectResult.Cancelled
    },
    showOp = { info ->
        withTimeoutOrNull(CONSENT_TIMEOUT_MS) {
            SignerConsentCoordinator.requestConsent(Amethyst.instance.appContext, info)
        } ?: SignerOpGrant.DenyOnce
    },
)

/**
 * Upper bound on how long a consent prompt may wait. A user who ignores the dialog eventually fails
 * the request closed (deny / declined) instead of holding the signer for every other app.
 */
private const val CONSENT_TIMEOUT_MS = 120_000L
