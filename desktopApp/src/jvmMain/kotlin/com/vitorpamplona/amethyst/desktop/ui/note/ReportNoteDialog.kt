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
package com.vitorpamplona.amethyst.desktop.ui.note

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.quartz.nip56Reports.ReportType

private val REPORT_OPTIONS =
    listOf(
        ReportType.SPAM to "Spam",
        ReportType.PROFANITY to "Profanity / Hateful speech",
        ReportType.IMPERSONATION to "Impersonation",
        ReportType.NUDITY to "Nudity / Sexual content",
        ReportType.ILLEGAL to "Illegal content",
        ReportType.MALWARE to "Malware / Phishing",
    )

/**
 * NIP-56 report dialog. Lets the user pick a report reason and optionally add a
 * comment, then either just report or report-and-block the author. Mirrors the
 * Android `ReportNoteDialog`, adapted to Compose Desktop.
 */
@Composable
fun ReportNoteDialog(
    onDismiss: () -> Unit,
    onReport: (ReportType, String) -> Unit,
    onBlockAndReport: (ReportType, String) -> Unit,
) {
    var selected by remember { mutableStateOf(REPORT_OPTIONS.first().first) }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report note") },
        text = {
            Column {
                REPORT_OPTIONS.forEach { (type, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = selected == type, onClick = { selected = type })
                                .padding(vertical = 2.dp),
                    ) {
                        RadioButton(selected = selected == type, onClick = { selected = type })
                        Text(label, modifier = Modifier.padding(start = 4.dp))
                    }
                }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Comment (optional)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onReport(selected, comment)
                onDismiss()
            }) { Text("Report") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    onBlockAndReport(selected, comment)
                    onDismiss()
                }) { Text("Block & report") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
