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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode

import androidx.compose.runtime.Composable
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.scanner.QrCodeScannerDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.scanner.ScanOutcome
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.scanner.ScannedPayload
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.scanner.classifyScannedPayload
import com.vitorpamplona.amethyst.ui.uriToRoute
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException

/**
 * Scans a QR code and navigates wherever it points.
 *
 * A payload we decode but cannot route no longer closes the scanner: it stays open and explains
 * itself (see `ScanOutcomeSheet`), because "that is a Lightning invoice, not a profile" and "the
 * camera never read anything" used to look identical from the outside.
 */
@Composable
fun NIP19QrCodeScanner(
    accountViewModel: AccountViewModel,
    onScan: (Route?) -> Unit,
) {
    QrCodeScannerDialog(
        onDismiss = { onScan(null) },
        onScan = { contents ->
            val route = routeFor(contents, accountViewModel)
            if (route != null) {
                onScan(route)
                ScanOutcome.Handled
            } else {
                ScanOutcome.NotSupported
            }
        },
    )
}

/**
 * The route a scanned string leads to, or null when nothing here can open it.
 *
 * A bare hex pubkey gets re-encoded as an npub first. Plenty of web tools hand out a raw
 * 64-character key with no bech32 wrapper, and treating that as unreadable has been a reported
 * papercut since 2023 (issue #417).
 */
private fun routeFor(
    contents: String,
    accountViewModel: AccountViewModel,
): Route? =
    try {
        val payload = classifyScannedPayload(contents)
        val uri = if (payload is ScannedPayload.HexPubKey) payload.npub else contents
        uriToRoute(uri, accountViewModel.account)
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        // The payload itself never reaches the log. A QR code is as likely to hold an nsec, a
        // wallet-connect secret or a Cashu token as a profile link, and logcat is readable over
        // adb and swept up by device bug reports — the same material ScannedPayload.containsSecret
        // exists to keep off the screen two files away. The classification and the length say
        // enough to debug a routing failure; classifying again here is wrapped because this is
        // the branch for a payload that already made something throw.
        val kind = runCatching { classifyScannedPayload(contents)::class.simpleName }.getOrNull() ?: "unclassifiable"
        Log.e("NIP19 Scanner", "Could not route a scanned $kind payload of ${contents.length} chars", e)
        // A QR code can hold anything at all. Never let one throw.
        null
    }

/**
 * Scans a QR code and hands back whatever it says.
 *
 * For callers that do their own validation — a wallet-connect URI, a `bunker://` offer, a key on
 * the login screen. `null` means the user backed out.
 */
@Composable
fun SimpleQrCodeScanner(onScan: (String?) -> Unit) {
    QrCodeScannerDialog(
        onDismiss = { onScan(null) },
        onScan = { contents ->
            onScan(contents)
            ScanOutcome.Handled
        },
    )
}
