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
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_status_close
import com.vitorpamplona.amethyst.commons.resources.git_status_mark_draft
import com.vitorpamplona.amethyst.commons.resources.git_status_mark_merged
import com.vitorpamplona.amethyst.commons.resources.git_status_ready
import com.vitorpamplona.amethyst.commons.resources.git_status_reopen
import com.vitorpamplona.amethyst.commons.resources.git_status_resolve
import com.vitorpamplona.amethyst.commons.ui.note.StatusKind
import com.vitorpamplona.amethyst.commons.ui.stringRes

/** Compact sizing shared by the small action buttons on git cards. */
val GitCardButtonHeight = Modifier.height(32.dp)
val GitCardButtonPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)

/**
 * NIP-34 status buttons for an issue, patch or pull request, by its [current] status:
 * - closed, merged or resolved: "Reopen";
 * - a draft patch or PR: "Ready for review"; an open one: "Mark as draft";
 * - an open patch or PR, to a repository maintainer ([canMerge]): "Mark merged";
 * - an open issue: "Resolve";
 * - anything open: "Close".
 * The host decides who may see them and publishes the status event.
 */
@Composable
fun GitStatusButtons(
    current: StatusKind,
    isPatchOrPr: Boolean,
    canMerge: Boolean,
    onSetStatus: (StatusKind) -> Unit,
) {
    FlowRow(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (current == StatusKind.CLOSED || current == StatusKind.APPLIED) {
            StatusButton(MaterialSymbols.RadioButtonChecked, stringRes(Res.string.git_status_reopen)) { onSetStatus(StatusKind.OPEN) }
            return@FlowRow
        }

        if (isPatchOrPr) {
            if (current == StatusKind.DRAFT) {
                StatusButton(MaterialSymbols.RadioButtonChecked, stringRes(Res.string.git_status_ready)) { onSetStatus(StatusKind.OPEN) }
            } else {
                StatusButton(MaterialSymbols.EditNote, stringRes(Res.string.git_status_mark_draft)) { onSetStatus(StatusKind.DRAFT) }
            }
            if (canMerge) {
                StatusButton(MaterialSymbols.Check, stringRes(Res.string.git_status_mark_merged)) { onSetStatus(StatusKind.APPLIED) }
            }
        } else {
            StatusButton(MaterialSymbols.Check, stringRes(Res.string.git_status_resolve)) { onSetStatus(StatusKind.APPLIED) }
        }

        OutlinedButton(
            onClick = { onSetStatus(StatusKind.CLOSED) },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = GitCardButtonHeight,
            contentPadding = GitCardButtonPadding,
        ) {
            Icon(MaterialSymbols.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(stringRes(Res.string.git_status_close), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun StatusButton(
    symbol: MaterialSymbol,
    label: String,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = GitCardButtonHeight,
        contentPadding = GitCardButtonPadding,
    ) {
        Icon(symbol, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
    }
}
