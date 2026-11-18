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
package com.vitorpamplona.amethyst.desktop.napplet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerConnectInfo
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerConsentInfo
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46ClientStore
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppConnectResult
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.amethyst.commons.model.nip46Signer.Nip46ConsentPrompter
import com.vitorpamplona.amethyst.commons.napplet.NappletPromptQueues
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_remote_app
import com.vitorpamplona.amethyst.commons.ui.connectedApps.consent.Nip46ConsentPrompts
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The desktop's NIP-46 bunker questions, worded by [Nip46ConsentPrompts] like Android's: each waits
 * in [queues] under its app's ledger coordinate (`nip46:<account>:<app>`), and
 * [Nip46ConsentWindows] shows the oldest in a window of its own.
 *
 * Every question fails closed. Once on screen it has [ANSWER_WITHIN_MS] to be answered (the window
 * dismisses it), and closing its window declines it. One that is never shown, behind other
 * questions, gives up after [UNSHOWN_LIMIT_MS], so it can't hold the signer forever.
 */
class DesktopNip46Consent(
    clientStore: () -> Nip46ClientStore,
    val queues: NappletPromptQueues = NappletPromptQueues(),
) : Nip46ConsentPrompter by Nip46ConsentPrompts(
        clientStore = clientStore,
        showConnect = { info: SignerConnectInfo ->
            withTimeoutOrNull(UNSHOWN_LIMIT_MS) { queues.connects.ask(info, info.coordinate) } ?: AppConnectResult.Cancelled
        },
        showOp = { info: SignerConsentInfo ->
            withTimeoutOrNull(UNSHOWN_LIMIT_MS) { queues.signerOps.ask(info, info.coordinate) } ?: SignerOpGrant.DenyOnce
        },
    ) {
    companion object {
        /**
         * How long a question on screen may wait. An app gives up on a signer after a minute or two,
         * and a question left open must not hold the signer for every other app; as on Android.
         */
        const val ANSWER_WITHIN_MS = 120_000L

        /**
         * The ceiling for a question still waiting its turn. Each one gets [ANSWER_WITHIN_MS] from
         * when it is shown, so this only ends one that never could be.
         */
        const val UNSHOWN_LIMIT_MS = 10 * 60_000L
    }
}

/**
 * Every NIP-46 question waiting on the user, oldest first, each in a window of its own. A question
 * brings the main window back ([onQuestion]) and comes to the front itself: the bunker answers apps
 * while the user is elsewhere, and a question nobody sees is a silent "no".
 */
@Composable
fun Nip46ConsentWindows(
    consent: DesktopNip46Consent,
    onQuestion: () -> Unit,
) {
    val connects by consent.queues.connects.pending
        .collectAsState()
    val signerOps by consent.queues.signerOps.pending
        .collectAsState()
    val waiting = connects.isNotEmpty() || signerOps.isNotEmpty()
    LaunchedEffect(waiting) { if (waiting) onQuestion() }

    val remoteApp = stringRes(Res.string.nip46_signer_remote_app)
    ConsentWindows(
        apps = { true },
        titleOf = { _, info -> consentTitle(info) ?: remoteApp },
        queues = consent.queues,
        answerWithinMs = DesktopNip46Consent.ANSWER_WITHIN_MS,
        raise = true,
    )
}
