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
package com.vitorpamplona.amethyst.ui.tor

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.stringRes

/**
 * Sticky alert that appears when Tor has been stuck connecting longer than
 * [TorManager.BOOTSTRAP_TIMEOUT_MS]. The user can either:
 *
 *  - Use a regular (non-Tor) connection for the rest of this session, with the choice
 *    remembered for [TorManager.APPROVAL_REMEMBER_MS] so subsequent failures within the
 *    window auto-fall-back without re-prompting.
 *  - Keep waiting — the dialog hides until the next connecting span ends and a fresh
 *    timeout fires.
 */
@Composable
fun TorConnectionFailureDialog(torManager: TorManager) {
    val failure by torManager.connectionFailure.collectAsStateWithLifecycle()
    val bypass by torManager.sessionBypass.collectAsStateWithLifecycle()

    var dismissedForCurrentSpan by remember { mutableStateOf(false) }

    // Reset the per-span dismissal whenever the failure flag goes back to false (a new
    // Connecting span starts). That way "Keep waiting" only suppresses the current span.
    if (!failure && dismissedForCurrentSpan) {
        dismissedForCurrentSpan = false
    }

    if (!failure || bypass || dismissedForCurrentSpan) return

    AlertDialog(
        onDismissRequest = { /* sticky — user must pick a button */ },
        title = { Text(stringRes(R.string.tor_connection_failed_title)) },
        text = { Text(stringRes(R.string.tor_connection_failed_body)) },
        confirmButton = {
            Button(onClick = { torManager.approveBypassForOneHour() }) {
                Text(stringRes(R.string.tor_continue_without_for_session))
            }
        },
        dismissButton = {
            Button(onClick = { dismissedForCurrentSpan = true }) {
                Text(stringRes(R.string.tor_keep_waiting))
            }
        },
    )
}
