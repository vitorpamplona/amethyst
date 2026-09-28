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
package com.vitorpamplona.amethyst.desktop.ui.tor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.app_logo
import com.vitorpamplona.amethyst.commons.resources.connect_via_tor2
import com.vitorpamplona.amethyst.commons.resources.tor_continue_without_for_session
import com.vitorpamplona.amethyst.commons.resources.tor_splash_connecting
import com.vitorpamplona.amethyst.commons.resources.tor_splash_error
import com.vitorpamplona.amethyst.commons.resources.tor_splash_explainer
import com.vitorpamplona.amethyst.commons.tor.TorServiceStatus
import com.vitorpamplona.amethyst.desktop.platform.IconResources
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * How long the splash waits before offering a way past Tor. Long enough that a normal
 * bootstrap finishes without the user ever seeing the choice, short enough that someone
 * offline (or on a network that blocks Tor) is not left staring at a spinner.
 */
private const val ESCAPE_DELAY_MS = 10_000L

/**
 * Shown instead of the app while the embedded Tor is expected but not yet routable.
 *
 * Tor never finishes bootstrapping without internet access, so this screen must not be a dead
 * end: after [ESCAPE_DELAY_MS] (or at once, on an error) it offers to continue over a regular
 * connection for this session, or to open the Tor settings (e.g. to point at an external Tor).
 */
@Composable
fun TorConnectingSplash(
    status: TorServiceStatus,
    onContinueWithoutTor: () -> Unit,
    onOpenTorSettings: () -> Unit,
) {
    var showEscape by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(ESCAPE_DELAY_MS)
        showEscape = true
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                text =
                    if (status is TorServiceStatus.Error) {
                        stringResource(Res.string.tor_splash_error, status.message)
                    } else {
                        stringResource(Res.string.tor_splash_connecting)
                    },
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.tor_splash_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Icon(
                painter = IconResources.rawBitmapPainter,
                contentDescription = stringResource(Res.string.app_logo),
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            if (showEscape || status is TorServiceStatus.Error) {
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onOpenTorSettings) {
                        Text(stringResource(Res.string.connect_via_tor2))
                    }
                    Button(onClick = onContinueWithoutTor) {
                        Text(stringResource(Res.string.tor_continue_without_for_session))
                    }
                }
            }
        }
    }
}
