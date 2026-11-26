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
package com.vitorpamplona.amethyst.commons.napplet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.commons.connectedApps.consent.ConsentQueue
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerConnectInfo
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerConsentInfo
import com.vitorpamplona.amethyst.commons.connectedApps.consent.oldestAccountGroup
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppConnectResult
import com.vitorpamplona.amethyst.commons.napplet.NappletConsentInfo
import com.vitorpamplona.amethyst.commons.napplet.NappletDownloadProgress
import com.vitorpamplona.amethyst.commons.napplet.NappletPromptQueues
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_fetching
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_retry
import com.vitorpamplona.amethyst.commons.ui.connectedApps.consent.BatchedConsentCard
import com.vitorpamplona.amethyst.commons.ui.connectedApps.consent.ConsentInputGuard
import com.vitorpamplona.amethyst.commons.ui.connectedApps.consent.SignerConnectCard
import com.vitorpamplona.amethyst.commons.ui.connectedApps.consent.SignerConsentCard
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/*
 * The panes an app screen shows around its browser, the same on every host that draws a napplet as a
 * screen: fetching its files, waiting for Tor, a failure with a retry, and the consent questions it
 * asks, answered with the shared cards.
 */

/** The app's files are on their way: [download]'s bar while one big download runs, a spinner otherwise. */
@Composable
fun NappletFetchingPane(
    download: NappletDownloadProgress?,
    title: String,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NappletDownloadIndicator(download, spinnerSize = 40.dp)
            Text(stringRes(Res.string.napplet_desktop_fetching, title), textAlign = TextAlign.Center)
        }
    }
}

/** Something the app waits on before anything is fetched (Tor). */
@Composable
fun NappletWaitingPane(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator()
            Text(message, textAlign = TextAlign.Center)
        }
    }
}

/** Why the app isn't showing, with a retry where one can help. */
@Composable
fun NappletMessagePane(
    message: String,
    onRetry: (() -> Unit)?,
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error, modifier = Modifier.widthIn(max = 480.dp))
            if (onRetry != null) Button(onClick = onRetry) { Text(stringRes(Res.string.napplet_desktop_retry)) }
        }
    }
}

/**
 * The oldest question the [apps] wait on, of any kind, in a dialog over the screen: the in-window
 * counterpart of the desktop's consent windows. Several signatures waiting at once for the same
 * account are shown together, as on Android. Dismissing the dialog answers "not now".
 */
@Composable
fun NappletConsentDialogs(
    queues: NappletPromptQueues,
    /** Which asking apps this screen shows the questions of. */
    apps: (app: String?) -> Boolean,
    /**
     * The active account's view model, for a note preview of what would be signed. A question for
     * another account is shown without one.
     */
    accountViewModel: AccountViewModel?,
) {
    val capabilities by queues.capabilities.pending
        .collectAsState()
    val connects by queues.connects.pending
        .collectAsState()
    val signerOps by queues.signerOps.pending
        .collectAsState()
    val question = oldestQuestionOf(apps, capabilities, connects, signerOps) ?: return
    val batch = if (question.info is SignerConsentInfo) signerOps.filter { it.app == question.app }.oldestAccountGroup() else emptyList()
    val preview =
        accountViewModel?.takeIf { viewModel ->
            val asked = (question.info as? SignerConsentInfo)?.accountPubKey
            asked == null || asked == viewModel.account.pubKey
        }

    key(question) {
        Dialog(
            onDismissRequest = { question.dismiss() },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier =
                    Modifier
                        .padding(16.dp)
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .heightIn(max = 720.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
            ) {
                ConsentInputGuard {
                    when (val info = question.info) {
                        is NappletConsentInfo -> NappletConsentCard(info, onDecision = { queues.capabilities.answer(question.token, it) })
                        is SignerConnectInfo ->
                            SignerConnectCard(
                                info = info,
                                onConnect = { queues.connects.answer(question.token, AppConnectResult.Connected(it)) },
                                onBlock = { queues.connects.answer(question.token, AppConnectResult.Blocked) },
                                onCancel = { queues.connects.answer(question.token, AppConnectResult.Cancelled) },
                            )
                        is SignerConsentInfo -> {
                            if (batch.size <= 1) {
                                SignerConsentCard(info, preview, onGrant = { queues.signerOps.answer(question.token, it) })
                            } else {
                                BatchedConsentCard(
                                    pending = batch,
                                    accountViewModel = preview,
                                    onResolve = { tokens, grant -> tokens.forEach { queues.signerOps.answer(it, grant) } },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The oldest question, of any kind, from the [apps] asked about. */
fun oldestQuestionOf(
    apps: (app: String?) -> Boolean,
    vararg queues: List<ConsentQueue.Request<*, *>>,
): ConsentQueue.Request<*, *>? =
    queues
        .asSequence()
        .flatten()
        .filter { apps(it.app) }
        .minByOrNull { it.order }
