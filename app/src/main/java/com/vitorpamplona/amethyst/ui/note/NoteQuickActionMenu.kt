package com.vitorpamplona.amethyst.ui.note

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonColors
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.AudioTrackEvent
import com.vitorpamplona.amethyst.service.model.PeopleListEvent
import com.vitorpamplona.amethyst.ui.components.SelectTextDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ReportNoteDialog
import com.vitorpamplona.amethyst.ui.theme.WarningColor
import kotlinx.coroutines.launch

private fun lightenColor(color: Color, amount: Float): Color {
    var argb = color.toArgb()
    val hslOut = floatArrayOf(0f, 0f, 0f)
    ColorUtils.colorToHSL(argb, hslOut)
    hslOut[2] += amount
    argb = ColorUtils.HSLToColor(hslOut)
    return Color(argb)
}

val externalLinkForNote = { note: Note ->
    if (note is AddressableNote) {
        if (note.event?.getReward() != null) {
            "https://nostrbounties.com/b/${note.address().toNAddr()}"
        } else if (note.event is PeopleListEvent) {
            "https://listr.lol/a/${note.address()?.toNAddr()}"
        } else if (note.event is AudioTrackEvent) {
            "https://zapstr.live/?track=${note.address()?.toNAddr()}"
        } else {
            "https://habla.news/a/${note.address()?.toNAddr()}"
        }
    } else {
        "https://snort.social/e/${note.toNEvent()}"
    }
}

@Composable
private fun VerticalDivider(color: Color) =
    Divider(
        color = color,
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
    )

@Composable
fun NoteQuickActionMenu(note: Note, popupExpanded: Boolean, onDismiss: () -> Unit, accountViewModel: AccountViewModel) {
    val context = LocalContext.current
    val primaryLight = lightenColor(MaterialTheme.colors.primary, 0.1f)
    val cardShape = RoundedCornerShape(5.dp)
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var showSelectTextDialog by remember { mutableStateOf(false) }
    var showDeleteAlertDialog by remember { mutableStateOf(false) }
    var showBlockAlertDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    val backgroundColor = if (MaterialTheme.colors.isLight) {
        MaterialTheme.colors.primary
    } else {
        MaterialTheme.colors.primary.copy(alpha = 0.32f).compositeOver(MaterialTheme.colors.background)
    }

    val showToast = { stringResource: Int ->
        scope.launch {
            Toast.makeText(
                context,
                context.getString(stringResource),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    if (popupExpanded) {
        val isOwnNote = accountViewModel.isLoggedUser(note.author)
        val isFollowingUser = !isOwnNote && accountViewModel.isFollowing(note.author)

        Popup(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier.shadow(elevation = 6.dp, shape = cardShape),
                shape = cardShape,
                backgroundColor = backgroundColor
            ) {
                Column(modifier = Modifier.width(IntrinsicSize.Min)) {
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        NoteQuickActionItem(
                            icon = Icons.Default.ContentCopy,
                            label = stringResource(R.string.quick_action_copy_text)
                        ) {
                            clipboardManager.setText(
                                AnnotatedString(
                                    accountViewModel.decrypt(note) ?: ""
                                )
                            )
                            showToast(R.string.copied_note_text_to_clipboard)
                            onDismiss()
                        }
                        VerticalDivider(primaryLight)
                        NoteQuickActionItem(Icons.Default.AlternateEmail, stringResource(R.string.quick_action_copy_user_id)) {
                            clipboardManager.setText(AnnotatedString("nostr:${note.author?.pubkeyNpub()}"))
                            showToast(R.string.copied_user_id_to_clipboard)
                            onDismiss()
                        }
                        VerticalDivider(primaryLight)
                        NoteQuickActionItem(Icons.Default.FormatQuote, stringResource(R.string.quick_action_copy_note_id)) {
                            clipboardManager.setText(AnnotatedString("nostr:${note.toNEvent()}"))
                            showToast(R.string.copied_note_id_to_clipboard)
                            onDismiss()
                        }

                        if (!isOwnNote) {
                            VerticalDivider(primaryLight)

                            NoteQuickActionItem(Icons.Default.Block, stringResource(R.string.quick_action_block)) {
                                if (accountViewModel.hideBlockAlertDialog) {
                                    note.author?.let { accountViewModel.hide(it) }
                                    onDismiss()
                                } else {
                                    showBlockAlertDialog = true
                                }
                            }
                        }
                    }
                    Divider(
                        color = primaryLight,
                        modifier = Modifier
                            .fillMaxWidth()
                            .width(1.dp)
                    )
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        if (isOwnNote) {
                            NoteQuickActionItem(Icons.Default.Delete, stringResource(R.string.quick_action_delete)) {
                                if (accountViewModel.hideDeleteRequestDialog) {
                                    accountViewModel.delete(note)
                                    onDismiss()
                                } else {
                                    showDeleteAlertDialog = true
                                }
                            }
                        } else if (isFollowingUser) {
                            NoteQuickActionItem(Icons.Default.PersonRemove, stringResource(R.string.quick_action_unfollow)) {
                                accountViewModel.unfollow(note.author!!)
                                onDismiss()
                            }
                        } else {
                            NoteQuickActionItem(Icons.Default.PersonAdd, stringResource(R.string.quick_action_follow)) {
                                accountViewModel.follow(note.author!!)
                                onDismiss()
                            }
                        }

                        VerticalDivider(primaryLight)
                        NoteQuickActionItem(
                            icon = ImageVector.vectorResource(id = R.drawable.text_select_move_forward_character),
                            label = stringResource(R.string.quick_action_select)
                        ) {
                            showSelectTextDialog = true
                            onDismiss()
                        }
                        VerticalDivider(primaryLight)
                        NoteQuickActionItem(icon = Icons.Default.Share, label = stringResource(R.string.quick_action_share)) {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    externalLinkForNote(note)
                                )
                                putExtra(Intent.EXTRA_TITLE, context.getString(R.string.quick_action_share_browser_link))
                            }

                            val shareIntent = Intent.createChooser(sendIntent, context.getString(R.string.quick_action_share))
                            ContextCompat.startActivity(context, shareIntent, null)
                            onDismiss()
                        }

                        if (!isOwnNote) {
                            VerticalDivider(primaryLight)

                            NoteQuickActionItem(Icons.Default.Report, stringResource(R.string.quick_action_report)) {
                                showReportDialog = true
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSelectTextDialog) {
        accountViewModel.decrypt(note)?.let {
            SelectTextDialog(it) { showSelectTextDialog = false }
        }
    }

    if (showDeleteAlertDialog) {
        DeleteAlertDialog(note, accountViewModel) {
            showDeleteAlertDialog = false
            onDismiss()
        }
    }

    if (showBlockAlertDialog) {
        BlockAlertDialog(note, accountViewModel) {
            showBlockAlertDialog = false
            onDismiss()
        }
    }

    if (showReportDialog) {
        ReportNoteDialog(note, accountViewModel) {
            showReportDialog = false
            onDismiss()
        }
    }
}

@Composable
fun NoteQuickActionItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .size(70.dp)
            .clickable { onClick() },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .padding(bottom = 5.dp),
            tint = Color.White
        )
        Text(text = label, fontSize = 12.sp, color = Color.White, textAlign = TextAlign.Center)
    }
}

@Composable
fun DeleteAlertDialog(note: Note, accountViewModel: AccountViewModel, onDismiss: () -> Unit) =
    QuickActionAlertDialog(
        title = stringResource(R.string.quick_action_request_deletion_alert_title),
        textContent = stringResource(R.string.quick_action_request_deletion_alert_body),
        buttonIcon = Icons.Default.Delete,
        buttonText = stringResource(R.string.quick_action_delete_dialog_btn),
        onClickDoOnce = {
            accountViewModel.delete(note)
            onDismiss()
        },
        onClickDontShowAgain = {
            accountViewModel.delete(note)
            accountViewModel.dontShowDeleteRequestDialog()
            onDismiss()
        },
        onDismiss = onDismiss
    )

@Composable
private fun BlockAlertDialog(note: Note, accountViewModel: AccountViewModel, onDismiss: () -> Unit) =
    QuickActionAlertDialog(
        title = stringResource(R.string.report_dialog_block_hide_user_btn),
        textContent = stringResource(R.string.report_dialog_blocking_a_user),
        buttonIcon = Icons.Default.Block,
        buttonText = stringResource(R.string.quick_action_block_dialog_btn),
        buttonColors = ButtonDefaults.buttonColors(
            backgroundColor = WarningColor,
            contentColor = Color.White
        ),
        onClickDoOnce = {
            note.author?.let { accountViewModel.hide(it) }
            onDismiss()
        },
        onClickDontShowAgain = {
            note.author?.let { accountViewModel.hide(it) }
            accountViewModel.dontShowBlockAlertDialog()
            onDismiss()
        },
        onDismiss = onDismiss
    )

@Composable
private fun QuickActionAlertDialog(
    title: String,
    textContent: String,
    buttonIcon: ImageVector,
    buttonText: String,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(),
    onClickDoOnce: () -> Unit,
    onClickDontShowAgain: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title)
        },
        text = {
            Text(textContent)
        },
        buttons = {
            Row(
                modifier = Modifier.padding(all = 8.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onClickDontShowAgain) {
                    Text(stringResource(R.string.quick_action_dont_show_again_button))
                }
                Button(onClick = onClickDoOnce, colors = buttonColors) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = buttonIcon,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(buttonText)
                    }
                }
            }
        }
    )
}
