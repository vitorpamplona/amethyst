/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.WarningColor
import com.vitorpamplona.quartz.nip56Reports.ReportType
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportNoteDialog(
    note: Note,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    val reportTypes =
        listOf(
            Pair(ReportType.SPAM, stringRes(R.string.report_dialog_spam)),
            Pair(ReportType.PROFANITY, stringRes(R.string.report_dialog_profanity)),
            Pair(ReportType.IMPERSONATION, stringRes(R.string.report_dialog_impersonation)),
            Pair(ReportType.NUDITY, stringRes(R.string.report_dialog_nudity)),
            Pair(ReportType.ILLEGAL, stringRes(R.string.report_dialog_illegal)),
            Pair(ReportType.MALWARE, stringRes(R.string.report_malware)),
            Pair(ReportType.MOD, stringRes(R.string.report_mod)),
        )

    val reasonOptions = remember { reportTypes.map { TitleExplainer(it.second) }.toImmutableList() }
    var additionalReason by remember { mutableStateOf("") }
    var selectedReason by remember { mutableStateOf(-1) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = stringRes(id = R.string.report_dialog_title)) },
                    navigationIcon = { IconButton(onClick = onDismiss) { ArrowBackIcon() } },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                )
            },
        ) { pad ->
            Column(
                modifier =
                    Modifier.padding(16.dp, pad.calculateTopPadding(), 16.dp, pad.calculateBottomPadding()),
                verticalArrangement = Arrangement.SpaceAround,
            ) {
                SpacerH16()
                SectionHeader(text = stringRes(id = R.string.block_only))
                SpacerH16()
                Text(
                    text = stringRes(R.string.report_dialog_blocking_a_user),
                )
                SpacerH16()
                ActionButton(
                    text = stringRes(R.string.report_dialog_block_hide_user_btn),
                    icon = Icons.Default.Block,
                    onClick = {
                        note.author?.let { accountViewModel.hide(it) }
                        onDismiss()
                    },
                )
                SpacerH16()

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface, thickness = DividerThickness)

                SpacerH16()
                SectionHeader(text = stringRes(R.string.report_dialog_report_btn))
                SpacerH16()
                Text(stringRes(R.string.report_dialog_reminder_public))
                SpacerH16()
                TextSpinner(
                    label = stringRes(R.string.report_dialog_select_reason_label),
                    placeholder = stringRes(R.string.report_dialog_select_reason_placeholder),
                    options = reasonOptions,
                    onSelect = { selectedReason = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                SpacerH16()
                OutlinedTextField(
                    value = additionalReason,
                    onValueChange = { additionalReason = it },
                    placeholder = {
                        Text(text = stringRes(R.string.report_dialog_additional_reason_placeholder))
                    },
                    label = { Text(stringRes(R.string.report_dialog_additional_reason_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                SpacerH16()

                ActionButton(
                    text = stringRes(R.string.report_dialog_post_report_btn),
                    icon = Icons.Default.Report,
                    enabled = selectedReason in 0..reportTypes.lastIndex,
                    onClick = {
                        accountViewModel.report(
                            note,
                            reportTypes[selectedReason].first,
                            additionalReason,
                        )
                        note.author?.let { accountViewModel.hide(it) }
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable private fun SpacerH16() = Spacer(modifier = Modifier.height(16.dp))

@Composable
private fun SectionHeader(text: String) =
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 18.sp,
    )

@Composable
private fun ActionButton(
    text: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) = Button(
    onClick = onClick,
    enabled = enabled,
    colors = ButtonDefaults.buttonColors(containerColor = WarningColor),
    modifier = Modifier.fillMaxWidth(),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, color = Color.White)
    }
}
