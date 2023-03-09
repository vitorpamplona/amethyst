package com.vitorpamplona.amethyst.ui.note

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.SelectTextDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.launch

fun lightenColor(color: Color, amount: Float): Color {
    var argb = color.toArgb()
    val hslOut = floatArrayOf(0f, 0f, 0f)
    ColorUtils.colorToHSL(argb, hslOut)
    hslOut[2] += amount
    argb = ColorUtils.HSLToColor(hslOut)
    return Color(argb)
}

val externalLinkForNote = { note: Note -> "https://snort.social/e/${note.idNote()}" }

@Composable
fun VerticalDivider(color: Color) =
    Divider(
        color = color,
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
    )

@Composable
fun NoteQuickActionMenu(note: Note, popupExpanded: Boolean, onDismiss: () -> Unit, accountViewModel: AccountViewModel) {
    val context = LocalContext.current
    val primaryLight = lightenColor(MaterialTheme.colors.primary, 0.2f)
    val cardShape = RoundedCornerShape(5.dp)
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var showSelectTextDialog by remember { mutableStateOf(false) }
    var showDeleteAlertDialog by remember { mutableStateOf(false) }
    val isOwnNote = note.author == accountViewModel.userProfile()
    val isFollowingUser = !isOwnNote && accountViewModel.isFollowing(note.author!!)

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
        Popup(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier.shadow(elevation = 6.dp, shape = cardShape),
                shape = cardShape,
                backgroundColor = MaterialTheme.colors.primary
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
                            clipboardManager.setText(AnnotatedString("@${note.author?.pubkeyNpub()}" ?: ""))
                            showToast(R.string.copied_user_id_to_clipboard)
                            onDismiss()
                        }
                        VerticalDivider(primaryLight)
                        NoteQuickActionItem(Icons.Default.FormatQuote, stringResource(R.string.quick_action_copy_note_id)) {
                            clipboardManager.setText(AnnotatedString("@${note.idNote()}"))
                            showToast(R.string.copied_note_id_to_clipboard)
                            onDismiss()
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
                                if (accountViewModel.hideDeleteRequestInfo()) {
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
        AlertDialog(
            onDismissRequest = { onDismiss() },
            title = {
                Text(text = stringResource(R.string.quick_action_request_deletion_alert_title))
            },
            text = {
                Text(text = stringResource(R.string.quick_action_request_deletion_alert_body))
            },
            buttons = {
                Row(
                    modifier = Modifier.padding(all = 8.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            accountViewModel.setHideDeleteRequestInfo()
                            accountViewModel.delete(note)
                            onDismiss()
                        }
                    ) {
                        Text("Don't show again")
                    }
                    Button(
                        onClick = { accountViewModel.delete(note); onDismiss() }
                    ) {
                        Text("Delete")
                    }
                }
            }
        )
    }
}

@Composable
fun NoteQuickActionItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .size(64.dp)
            .clickable { onClick() },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colors.onPrimary
        )
        Text(text = label, fontSize = 12.sp)
    }
}
