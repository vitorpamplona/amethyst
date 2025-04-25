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
package com.vitorpamplona.amethyst.ui.note

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.core.graphics.ColorUtils
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.navigation.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.report.ReportNoteDialog
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.WarningColor
import com.vitorpamplona.amethyst.ui.theme.isLight
import com.vitorpamplona.amethyst.ui.theme.secondaryButtonBackground
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.bounties.bountyBaseReward
import com.vitorpamplona.quartz.nip51Lists.PeopleListEvent
import com.vitorpamplona.quartz.nip94FileMetadata.FileHeaderEvent
import kotlinx.coroutines.launch

private fun lightenColor(
    color: Color,
    amount: Float,
): Color {
    var argb = color.toArgb()
    val hslOut = floatArrayOf(0f, 0f, 0f)
    ColorUtils.colorToHSL(argb, hslOut)
    hslOut[2] += amount
    argb = ColorUtils.HSLToColor(hslOut)
    return Color(argb)
}

val externalLinkForUser = { user: User ->
    njumpLink(user.toNProfile())
}

val njumpLink = { nip19BechAddress: String ->
    "https://njump.me/$nip19BechAddress"
}

val externalLinkForNote = { note: Note ->
    if (note is AddressableNote) {
        if (note.event?.bountyBaseReward() != null) {
            "https://nostrbounties.com/b/${note.toNAddr()}"
        } else if (note.event is PeopleListEvent) {
            "https://listr.lol/a/${note.toNAddr()}"
        } else if (note.event is AudioTrackEvent) {
            "https://zapstr.live/?track=${note.toNAddr()}"
        } else {
            njumpLink(note.toNAddr())
        }
    } else {
        if (note.event is FileHeaderEvent) {
            "https://filestr.vercel.app/e/${note.toNEvent()}"
        } else {
            njumpLink(note.toNEvent())
        }
    }
}

@Composable
fun LongPressToQuickAction(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    content: @Composable (() -> Unit) -> Unit,
) {
    val popupExpanded = remember { mutableStateOf(false) }

    content { popupExpanded.value = true }

    if (popupExpanded.value) {
        NoteQuickActionMenu(
            note = baseNote,
            onDismiss = { popupExpanded.value = false },
            accountViewModel = accountViewModel,
            nav = EmptyNav,
        )
    }
}

@Composable
fun NoteQuickActionMenu(
    note: Note,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    NoteQuickActionMenu(
        note = note,
        onDismiss = onDismiss,
        onWantsToEditDraft = {
            nav.nav(
                Route.NewPost(
                    draft = note.idHex,
                ),
            )
        },
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun NoteQuickActionMenu(
    note: Note,
    onDismiss: () -> Unit,
    onWantsToEditDraft: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val showDeleteAlertDialog = remember { mutableStateOf(false) }
    val showBlockAlertDialog = remember { mutableStateOf(false) }
    val showReportDialog = remember { mutableStateOf(false) }

    RenderMainPopup(
        accountViewModel,
        note,
        onDismiss,
        showBlockAlertDialog,
        showDeleteAlertDialog,
        showReportDialog,
        onWantsToEditDraft,
    )

    if (showDeleteAlertDialog.value) {
        DeleteAlertDialog(note, accountViewModel) {
            showDeleteAlertDialog.value = false
            onDismiss()
        }
    }

    if (showBlockAlertDialog.value) {
        BlockAlertDialog(note, accountViewModel) {
            showBlockAlertDialog.value = false
            onDismiss()
        }
    }

    if (showReportDialog.value) {
        ReportNoteDialog(note, accountViewModel) {
            showReportDialog.value = false
            onDismiss()
        }
    }
}

@Composable
private fun RenderMainPopup(
    accountViewModel: AccountViewModel,
    note: Note,
    onDismiss: () -> Unit,
    showBlockAlertDialog: MutableState<Boolean>,
    showDeleteAlertDialog: MutableState<Boolean>,
    showReportDialog: MutableState<Boolean>,
    onWantsToEditDraft: () -> Unit,
) {
    val context = LocalContext.current
    val primaryLight = lightenColor(MaterialTheme.colorScheme.primary, 0.1f)
    val cardShape = RoundedCornerShape(5.dp)
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val backgroundColor =
        if (MaterialTheme.colorScheme.isLight) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.secondaryButtonBackground
        }

    val showToast = { stringRes: Int ->
        scope.launch {
            Toast
                .makeText(
                    context,
                    stringRes(context, stringRes),
                    Toast.LENGTH_SHORT,
                ).show()
        }
    }

    val isOwnNote = accountViewModel.isLoggedUser(note.author)
    val isFollowingUser = !isOwnNote && accountViewModel.isFollowing(note.author)

    Popup(onDismissRequest = onDismiss, alignment = Alignment.Center) {
        Card(
            modifier = Modifier.shadow(elevation = 6.dp, shape = cardShape),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
        ) {
            Column(modifier = Modifier.width(IntrinsicSize.Min)) {
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    NoteQuickActionItem(
                        icon = Icons.Default.ContentCopy,
                        label = stringRes(R.string.quick_action_copy_text),
                    ) {
                        accountViewModel.decrypt(note) {
                            clipboardManager.setText(AnnotatedString(it))
                            showToast(R.string.copied_note_text_to_clipboard)
                        }

                        onDismiss()
                    }
                    VerticalDivider(color = primaryLight)
                    NoteQuickActionItem(
                        Icons.Default.AlternateEmail,
                        stringRes(R.string.quick_action_copy_user_id),
                    ) {
                        note.author?.let {
                            scope.launch {
                                clipboardManager.setText(AnnotatedString(it.toNostrUri()))
                                showToast(R.string.copied_user_id_to_clipboard)
                                onDismiss()
                            }
                        }
                    }
                    VerticalDivider(color = primaryLight)
                    NoteQuickActionItem(
                        Icons.Default.FormatQuote,
                        stringRes(R.string.quick_action_copy_note_id),
                    ) {
                        scope.launch {
                            clipboardManager.setText(AnnotatedString(note.toNostrUri()))
                            showToast(R.string.copied_note_id_to_clipboard)
                            onDismiss()
                        }
                    }

                    if (!isOwnNote) {
                        VerticalDivider(color = primaryLight)

                        NoteQuickActionItem(
                            Icons.Default.Block,
                            stringRes(R.string.quick_action_block),
                        ) {
                            if (accountViewModel.account.settings.hideBlockAlertDialog) {
                                note.author?.let { accountViewModel.hide(it) }
                                onDismiss()
                            } else {
                                showBlockAlertDialog.value = true
                            }
                        }
                    }
                }
                HorizontalDivider(
                    color = primaryLight,
                )
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    if (isOwnNote) {
                        NoteQuickActionItem(
                            Icons.Default.Delete,
                            stringRes(R.string.quick_action_delete),
                        ) {
                            if (accountViewModel.account.settings.hideDeleteRequestDialog) {
                                accountViewModel.delete(note)
                                onDismiss()
                            } else {
                                showDeleteAlertDialog.value = true
                            }
                        }
                    } else if (isFollowingUser) {
                        NoteQuickActionItem(
                            Icons.Default.PersonRemove,
                            stringRes(R.string.quick_action_unfollow),
                        ) {
                            accountViewModel.unfollow(note.author!!)
                            onDismiss()
                        }
                    } else {
                        NoteQuickActionItem(
                            Icons.Default.PersonAdd,
                            stringRes(R.string.quick_action_follow),
                        ) {
                            accountViewModel.follow(note.author!!)
                            onDismiss()
                        }
                    }

                    VerticalDivider(color = primaryLight)
                    NoteQuickActionItem(
                        icon = ImageVector.vectorResource(id = R.drawable.relays),
                        label = stringRes(R.string.broadcast),
                    ) {
                        accountViewModel.broadcast(note)
                        // showSelectTextDialog = true
                        onDismiss()
                    }
                    VerticalDivider(color = primaryLight)
                    if (isOwnNote && note.isDraft()) {
                        NoteQuickActionItem(
                            Icons.Default.Edit,
                            stringRes(R.string.edit_draft),
                        ) {
                            onWantsToEditDraft()
                        }
                    } else {
                        NoteQuickActionItem(
                            icon = Icons.Default.Share,
                            label = stringRes(R.string.quick_action_share),
                        ) {
                            val sendIntent =
                                Intent().apply {
                                    action = Intent.ACTION_SEND
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        externalLinkForNote(note),
                                    )
                                    putExtra(
                                        Intent.EXTRA_TITLE,
                                        stringRes(context, R.string.quick_action_share_browser_link),
                                    )
                                }

                            val shareIntent =
                                Intent.createChooser(
                                    sendIntent,
                                    stringRes(context, R.string.quick_action_share),
                                )
                            context.startActivity(shareIntent)
                            onDismiss()
                        }
                    }

                    if (!isOwnNote) {
                        VerticalDivider(color = primaryLight)

                        NoteQuickActionItem(
                            Icons.Default.Report,
                            stringRes(R.string.quick_action_report),
                        ) {
                            showReportDialog.value = true
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NoteQuickActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .size(70.dp)
                .clickable { onClick() },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier =
                Modifier
                    .size(24.dp)
                    .padding(bottom = 5.dp),
            tint = Color.White,
        )
        Text(text = label, fontSize = 12.sp, color = Color.White, textAlign = TextAlign.Center)
    }
}

@Composable
fun DeleteAlertDialog(
    note: Note,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    QuickActionAlertDialog(
        title = stringRes(R.string.quick_action_request_deletion_alert_title),
        textContent = stringRes(R.string.quick_action_request_deletion_alert_body),
        buttonIcon = Icons.Default.Delete,
        buttonText = stringRes(R.string.quick_action_delete_dialog_btn),
        onClickDoOnce = {
            accountViewModel.delete(note)
            onDismiss()
        },
        onClickDontShowAgain = {
            accountViewModel.delete(note)
            accountViewModel.account.settings.setHideDeleteRequestDialog()
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun BlockAlertDialog(
    note: Note,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) = QuickActionAlertDialog(
    title = stringRes(R.string.report_dialog_block_hide_user_btn),
    textContent = stringRes(R.string.report_dialog_blocking_a_user),
    buttonIcon = Icons.Default.Block,
    buttonText = stringRes(R.string.quick_action_block_dialog_btn),
    buttonColors =
        ButtonDefaults.buttonColors(
            containerColor = WarningColor,
            contentColor = Color.White,
        ),
    onClickDoOnce = {
        note.author?.let { accountViewModel.hide(it) }
        onDismiss()
    },
    onClickDontShowAgain = {
        note.author?.let { accountViewModel.hide(it) }
        accountViewModel.account.settings.setHideBlockAlertDialog()
        onDismiss()
    },
    onDismiss = onDismiss,
)

@Composable
fun QuickActionAlertDialog(
    title: String,
    textContent: String,
    buttonIcon: ImageVector,
    buttonText: String,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(),
    onClickDoOnce: () -> Unit,
    onClickDontShowAgain: () -> Unit,
    onDismiss: () -> Unit,
) {
    QuickActionAlertDialog(
        title = title,
        textContent = textContent,
        icon = {
            Icon(
                imageVector = buttonIcon,
                contentDescription = null,
            )
        },
        buttonText = buttonText,
        buttonColors = buttonColors,
        onClickDoOnce = onClickDoOnce,
        onClickDontShowAgain = onClickDontShowAgain,
        onDismiss = onDismiss,
    )
}

@Composable
fun QuickActionAlertDialog(
    title: String,
    textContent: String,
    buttonIconResource: Int,
    buttonText: String,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(),
    onClickDoOnce: () -> Unit,
    onClickDontShowAgain: () -> Unit,
    onDismiss: () -> Unit,
) {
    QuickActionAlertDialog(
        title = title,
        textContent = textContent,
        icon = {
            Icon(
                painter = painterResource(buttonIconResource),
                contentDescription = null,
            )
        },
        buttonText = buttonText,
        buttonColors = buttonColors,
        onClickDoOnce = onClickDoOnce,
        onClickDontShowAgain = onClickDontShowAgain,
        onDismiss = onDismiss,
    )
}

@Composable
fun QuickActionAlertDialog(
    title: String,
    textContent: String,
    icon: @Composable () -> Unit,
    buttonText: String,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(),
    onClickDoOnce: () -> Unit,
    onClickDontShowAgain: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(textContent) },
        confirmButton = {
            Row(
                modifier =
                    Modifier
                        .padding(all = 8.dp)
                        .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onClickDontShowAgain) {
                    Text(stringRes(R.string.quick_action_dont_show_again_button))
                }
                Button(
                    onClick = onClickDoOnce,
                    colors = buttonColors,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        icon()
                        Spacer(Modifier.width(8.dp))
                        Text(buttonText)
                    }
                }
            }
        },
    )
}

@Composable
fun QuickActionAlertDialogOneButton(
    title: String,
    textContent: String,
    buttonIcon: ImageVector,
    buttonText: String,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(),
    onClickDoOnce: () -> Unit,
    onDismiss: () -> Unit,
) {
    QuickActionAlertDialogOneButton(
        title = title,
        textContent = textContent,
        icon = {
            Icon(
                imageVector = buttonIcon,
                contentDescription = null,
            )
        },
        buttonText = buttonText,
        buttonColors = buttonColors,
        onClickDoOnce = onClickDoOnce,
        onDismiss = onDismiss,
    )
}

@Composable
fun QuickActionAlertDialogOneButton(
    title: String,
    textContent: String,
    buttonIconResource: Int,
    buttonText: String,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(),
    onClickDoOnce: () -> Unit,
    onDismiss: () -> Unit,
) {
    QuickActionAlertDialogOneButton(
        title = title,
        textContent = textContent,
        icon = {
            Icon(
                painter = painterResource(buttonIconResource),
                contentDescription = null,
            )
        },
        buttonText = buttonText,
        buttonColors = buttonColors,
        onClickDoOnce = onClickDoOnce,
        onDismiss = onDismiss,
    )
}

@Composable
fun QuickActionAlertDialogOneButton(
    title: String,
    textContent: String,
    icon: @Composable () -> Unit,
    buttonText: String,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(),
    onClickDoOnce: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(textContent) },
        confirmButton = {
            Row(
                modifier =
                    Modifier
                        .padding(all = 8.dp)
                        .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Button(
                    onClick = onClickDoOnce,
                    colors = buttonColors,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        icon()
                        Spacer(Modifier.width(8.dp))
                        Text(buttonText)
                    }
                }
            }
        },
    )
}
