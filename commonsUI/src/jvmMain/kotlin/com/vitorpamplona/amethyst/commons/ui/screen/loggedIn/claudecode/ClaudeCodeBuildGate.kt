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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.claudecode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.claudecode.GateState
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.claude_code_close
import com.vitorpamplona.amethyst.commons.resources.claude_code_gate
import com.vitorpamplona.amethyst.commons.resources.claude_code_gate_cancelled
import com.vitorpamplona.amethyst.commons.resources.claude_code_gate_desc
import com.vitorpamplona.amethyst.commons.resources.claude_code_gate_failed
import com.vitorpamplona.amethyst.commons.resources.claude_code_gate_full_log
import com.vitorpamplona.amethyst.commons.resources.claude_code_gate_none
import com.vitorpamplona.amethyst.commons.resources.claude_code_gate_outdated
import com.vitorpamplona.amethyst.commons.resources.claude_code_gate_passed
import com.vitorpamplona.amethyst.commons.resources.claude_code_gate_run
import com.vitorpamplona.amethyst.commons.resources.claude_code_gate_running
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import java.io.File

/**
 * The repository's build checks before publishing: their state for the branch as it is now, the
 * button that runs or stops them, the newest lines of a run and its whole log. The same card on
 * every way of publishing, over the same run ([BuildGate]).
 */
@Composable
internal fun GateSection(
    gate: GateState,
    logFile: File?,
    fullLog: () -> String,
    onRun: () -> Unit,
    onCancel: () -> Unit,
) {
    var showLog by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, tint) =
                    when (gate) {
                        is GateState.Passed -> MaterialSymbols.Check to AddedColor
                        is GateState.Failed -> MaterialSymbols.Error to MaterialTheme.colorScheme.error
                        else -> MaterialSymbols.Security to MaterialTheme.colorScheme.grayText
                    }
                if (gate is GateState.Running) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringRes(Res.string.claude_code_gate), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringRes(
                            when (gate) {
                                GateState.None -> Res.string.claude_code_gate_none
                                GateState.NotRun -> Res.string.claude_code_gate_desc
                                GateState.Outdated -> Res.string.claude_code_gate_outdated
                                is GateState.Running -> Res.string.claude_code_gate_running
                                is GateState.Passed -> Res.string.claude_code_gate_passed
                                is GateState.Failed -> Res.string.claude_code_gate_failed
                                is GateState.Cancelled -> Res.string.claude_code_gate_cancelled
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.grayText,
                    )
                }
                when (gate) {
                    GateState.None -> {}
                    is GateState.Running -> OutlinedButton(onClick = onCancel) { Text(stringRes(Res.string.cancel)) }
                    else -> OutlinedButton(onClick = onRun) { Text(stringRes(Res.string.claude_code_gate_run)) }
                }
            }
            val tail =
                when (gate) {
                    is GateState.Running -> gate.tail
                    is GateState.Failed -> gate.tail
                    is GateState.Cancelled -> gate.tail
                    else -> ""
                }
            if (tail.isNotBlank()) CodeBlock(tail.lines().takeLast(GATE_LINES_SHOWN).joinToString("\n"), maxHeight = 220)
            if (logFile != null && gate !is GateState.None && gate !is GateState.NotRun) {
                TextButton(onClick = { showLog = true }) { Text(stringRes(Res.string.claude_code_gate_full_log)) }
            }
        }
    }
    if (showLog && logFile != null) {
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text(stringRes(Res.string.claude_code_gate)) },
            text = {
                Column(Modifier.widthIn(min = 560.dp, max = 1000.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectionContainer {
                        Text(logFile.absolutePath, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
                    }
                    // The newest part: a whole Gradle run is too long to lay out as one text.
                    CodeBlock(remember(gate) { fullLog().takeLast(FULL_LOG_SHOWN) }, maxHeight = 560)
                }
            },
            confirmButton = { TextButton(onClick = { showLog = false }) { Text(stringRes(Res.string.claude_code_close)) } },
        )
    }
}

private const val GATE_LINES_SHOWN = 12
private const val FULL_LOG_SHOWN = 200_000
