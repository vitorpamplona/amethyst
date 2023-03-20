package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Report
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.ReportEvent
import com.vitorpamplona.amethyst.ui.theme.WarningColor

@Composable
fun ReportNoteDialog(note: Note, accountViewModel: AccountViewModel, onDismiss: () -> Unit) {
    val reportTypes = listOf(
        Pair(ReportEvent.ReportType.SPAM, stringResource(R.string.report_dialog_spam)),
        Pair(ReportEvent.ReportType.PROFANITY, stringResource(R.string.report_dialog_profanity)),
        Pair(ReportEvent.ReportType.IMPERSONATION, stringResource(R.string.report_dialog_impersonation)),
        Pair(ReportEvent.ReportType.NUDITY, stringResource(R.string.report_dialog_nudity)),
        Pair(ReportEvent.ReportType.ILLEGAL, stringResource(R.string.report_dialog_illegal))
    )

    val reasonOptions = reportTypes.map { it.second }
    var additionalReason by remember { mutableStateOf("") }
    var selectedReason by remember { mutableStateOf(-1) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = stringResource(id = R.string.report_dialog_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = MaterialTheme.colors.onSurface
                            )
                        }
                    },
                    backgroundColor = MaterialTheme.colors.surface,
                    elevation = 0.dp
                )
            }
        ) { pad ->
            Column(
                modifier = Modifier.padding(16.dp, pad.calculateTopPadding(), 16.dp, pad.calculateBottomPadding()),
                verticalArrangement = Arrangement.SpaceAround
            ) {
                SpacerH16()
                SectionHeader(text = stringResource(id = R.string.block_only))
                SpacerH16()
                Text(
                    text = stringResource(R.string.report_dialog_blocking_a_user)
                )
                SpacerH16()
                ActionButton(
                    text = stringResource(R.string.report_dialog_block_hide_user_btn),
                    icon = Icons.Default.Block,
                    onClick = {
                        note.author?.let { accountViewModel.hide(it) }
                        onDismiss()
                    }
                )
                SpacerH16()

                Divider(color = MaterialTheme.colors.onSurface, thickness = 0.25.dp)

                SpacerH16()
                SectionHeader(text = stringResource(R.string.report_dialog_report_btn))
                SpacerH16()
                Text(stringResource(R.string.report_dialog_reminder_public))
                SpacerH16()
                TextSpinner(
                    label = stringResource(R.string.report_dialog_select_reason_label),
                    placeholder = stringResource(R.string.report_dialog_select_reason_placeholder),
                    options = reasonOptions,
                    onSelect = {
                        selectedReason = it
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                SpacerH16()
                OutlinedTextField(
                    value = additionalReason,
                    onValueChange = { additionalReason = it },
                    placeholder = { Text(text = stringResource(R.string.report_dialog_additional_reason_placeholder)) },
                    label = { Text(stringResource(R.string.report_dialog_additional_reason_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                SpacerH16()
                ActionButton(
                    text = stringResource(R.string.report_dialog_post_report_btn),
                    icon = Icons.Default.Report,
                    enabled = selectedReason in 0..reportTypes.lastIndex,
                    onClick = {
                        accountViewModel.report(note, reportTypes[selectedReason].first, additionalReason)
                        note.author?.let { accountViewModel.hide(it) }
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun SpacerH16() = Spacer(modifier = Modifier.height(16.dp))

@Composable
private fun SectionHeader(text: String) = Text(
    text = text,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colors.onSurface,
    fontSize = 18.sp
)

@Composable
private fun ActionButton(text: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) = Button(
    onClick = onClick,
    enabled = enabled,
    colors = ButtonDefaults.buttonColors(backgroundColor = WarningColor),
    modifier = Modifier.fillMaxWidth()
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, color = Color.White)
    }
}
