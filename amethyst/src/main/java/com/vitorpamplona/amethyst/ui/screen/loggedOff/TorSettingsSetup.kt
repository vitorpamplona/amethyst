/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedOff

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.components.appendLink
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.tor.ConnectTorDialog
import com.vitorpamplona.amethyst.ui.tor.TorSettings
import com.vitorpamplona.amethyst.ui.tor.TorType

@Composable
fun TorSettingsSetup(
    torSettings: TorSettings,
    onCheckedChange: (TorSettings) -> Unit,
    onError: (String) -> Unit,
) {
    var connectOrbotDialogOpen by remember { mutableStateOf(false) }
    var activeTor by remember { mutableStateOf(false) }

    val primary = MaterialTheme.colorScheme.primary

    Text(
        text =
            buildAnnotatedString {
                append(stringRes(R.string.connect_via_tor1) + " ")
                appendLink(stringRes(R.string.connect_via_tor2), primary) { connectOrbotDialogOpen = true }
            },
        modifier = Modifier.padding(vertical = 10.dp),
    )

    if (connectOrbotDialogOpen) {
        ConnectTorDialog(
            torSettings = torSettings,
            onClose = { connectOrbotDialogOpen = false },
            onPost = { torSettings ->
                activeTor = torSettings.torType != TorType.OFF
                connectOrbotDialogOpen = false
                onCheckedChange(torSettings)
            },
            onError = onError,
        )
    }
}
