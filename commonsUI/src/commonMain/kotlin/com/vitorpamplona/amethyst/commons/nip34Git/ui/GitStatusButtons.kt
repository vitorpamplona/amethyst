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
package com.vitorpamplona.amethyst.commons.nip34Git.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_status_close
import com.vitorpamplona.amethyst.commons.resources.git_status_mark_merged
import com.vitorpamplona.amethyst.commons.resources.git_status_reopen
import com.vitorpamplona.amethyst.commons.ui.stringRes

/** Compact sizing shared by the small action buttons on git cards. */
val CompactButtonHeight = Modifier.height(32.dp)
val CompactButtonPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)

/**
 * NIP-34 status buttons for an issue, patch or pull request: "Reopen" when it is
 * [closedOrApplied], otherwise "Mark merged" (patches and PRs only, [isPatchOrPr]) and "Close".
 * The host decides who may see them and publishes the status event.
 */
@Composable
fun GitStatusButtons(
    closedOrApplied: Boolean,
    isPatchOrPr: Boolean,
    onReopen: () -> Unit,
    onMarkMerged: () -> Unit,
    onClose: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (closedOrApplied) {
            FilledTonalButton(
                onClick = onReopen,
                modifier = CompactButtonHeight,
                contentPadding = CompactButtonPadding,
            ) {
                Icon(MaterialSymbols.RadioButtonChecked, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(stringRes(Res.string.git_status_reopen), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
            }
        } else {
            if (isPatchOrPr) {
                FilledTonalButton(
                    onClick = onMarkMerged,
                    modifier = CompactButtonHeight,
                    contentPadding = CompactButtonPadding,
                ) {
                    Icon(MaterialSymbols.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(stringRes(Res.string.git_status_mark_merged), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
                }
            }
            OutlinedButton(
                onClick = onClose,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = CompactButtonHeight,
                contentPadding = CompactButtonPadding,
            ) {
                Icon(MaterialSymbols.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(stringRes(Res.string.git_status_close), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
