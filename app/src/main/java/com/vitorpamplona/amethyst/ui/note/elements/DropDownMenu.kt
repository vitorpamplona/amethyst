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
package com.vitorpamplona.amethyst.ui.note.elements

import android.content.Intent
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.core.content.ContextCompat
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.actions.EditPostView
import com.vitorpamplona.amethyst.ui.actions.NewPostView
import com.vitorpamplona.amethyst.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.ui.note.VerticalDotsIcon
import com.vitorpamplona.amethyst.ui.note.externalLinkForNote
import com.vitorpamplona.amethyst.ui.note.types.EditState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ReportNoteDialog
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.Size24Modifier
import com.vitorpamplona.quartz.events.TextNoteEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun MoreOptionsButton(
    baseNote: Note,
    editState: State<GenericLoadable<EditState>>? = null,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val popupExpanded = remember { mutableStateOf(false) }

    IconButton(
        modifier = Size24Modifier,
        onClick = { popupExpanded.value = true },
    ) {
        VerticalDotsIcon(R.string.note_options)

        NoteDropDownMenu(
            baseNote,
            popupExpanded,
            editState,
            accountViewModel,
            nav,
        )
    }
}

@Immutable
data class DropDownParams(
    val isFollowingAuthor: Boolean,
    val isPrivateBookmarkNote: Boolean,
    val isPublicBookmarkNote: Boolean,
    val isLoggedUser: Boolean,
    val isSensitive: Boolean,
    val showSensitiveContent: Boolean?,
)

@Composable
fun NoteDropDownMenu(
    note: Note,
    popupExpanded: MutableState<Boolean>,
    editState: State<GenericLoadable<EditState>>? = null,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    var reportDialogShowing by remember { mutableStateOf(false) }

    var state by remember {
        mutableStateOf<DropDownParams>(
            DropDownParams(
                isFollowingAuthor = false,
                isPrivateBookmarkNote = false,
                isPublicBookmarkNote = false,
                isLoggedUser = false,
                isSensitive = false,
                showSensitiveContent = null,
            ),
        )
    }

    val onDismiss = remember(popupExpanded) { { popupExpanded.value = false } }

    val wantsToEditPost =
        remember {
            mutableStateOf(false)
        }

    val wantsToEditDraft =
        remember {
            mutableStateOf(false)
        }

    if (wantsToEditPost.value) {
        // avoids changing while drafting a note and a new event shows up.
        val versionLookingAt =
            remember {
                (editState?.value as? GenericLoadable.Loaded)?.loaded?.modificationToShow?.value
            }

        EditPostView(
            onClose = {
                popupExpanded.value = false
                wantsToEditPost.value = false
            },
            edit = note,
            versionLookingAt = versionLookingAt,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

    if (wantsToEditDraft.value) {
        NewPostView(
            onClose = {
                popupExpanded.value = false
                wantsToEditDraft.value = false
            },
            accountViewModel = accountViewModel,
            draft = note,
            nav = nav,
        )
    }

    DropdownMenu(
        expanded = popupExpanded.value,
        onDismissRequest = onDismiss,
    ) {
        val clipboardManager = LocalClipboardManager.current
        val appContext = LocalContext.current.applicationContext
        val actContext = LocalContext.current

        WatchBookmarksFollowsAndAccount(note, accountViewModel) { newState ->
            if (state != newState) {
                state = newState
            }
        }

        val scope = rememberCoroutineScope()

        if (!state.isFollowingAuthor) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.follow)) },
                onClick = {
                    val author = note.author ?: return@DropdownMenuItem
                    accountViewModel.follow(author)
                    onDismiss()
                },
            )
            HorizontalDivider(thickness = DividerThickness)
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.copy_text)) },
            onClick = {
                scope.launch(Dispatchers.IO) {
                    accountViewModel.decrypt(note) { clipboardManager.setText(AnnotatedString(it)) }
                    onDismiss()
                }
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.copy_user_pubkey)) },
            onClick = {
                scope.launch(Dispatchers.IO) {
                    clipboardManager.setText(AnnotatedString("nostr:${note.author?.pubkeyNpub()}"))
                    onDismiss()
                }
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.copy_note_id)) },
            onClick = {
                scope.launch(Dispatchers.IO) {
                    clipboardManager.setText(AnnotatedString("nostr:" + note.toNEvent()))
                    onDismiss()
                }
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.quick_action_share)) },
            onClick = {
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
                            actContext.getString(R.string.quick_action_share_browser_link),
                        )
                    }

                val shareIntent =
                    Intent.createChooser(sendIntent, appContext.getString(R.string.quick_action_share))
                ContextCompat.startActivity(actContext, shareIntent, null)
                onDismiss()
            },
        )
        HorizontalDivider(thickness = DividerThickness)
        if (state.isLoggedUser && note.isDraft()) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit_draft)) },
                onClick = {
                    wantsToEditDraft.value = true
                },
            )
        }
        if (note.event is TextNoteEvent && !note.isDraft()) {
            if (state.isLoggedUser) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit_post)) },
                    onClick = {
                        wantsToEditPost.value = true
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.propose_an_edit)) },
                    onClick = {
                        wantsToEditPost.value = true
                    },
                )
            }
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.broadcast)) },
            onClick = {
                accountViewModel.broadcast(note)
                onDismiss()
            },
        )
        HorizontalDivider(thickness = DividerThickness)
        if (accountViewModel.account.hasPendingAttestations(note)) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.timestamp_pending)) },
                onClick = {
                    onDismiss()
                },
            )
        } else {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.timestamp_it)) },
                onClick = {
                    accountViewModel.timestamp(note)
                    onDismiss()
                },
            )
        }
        HorizontalDivider(thickness = DividerThickness)
        if (state.isPrivateBookmarkNote) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.remove_from_private_bookmarks)) },
                onClick = {
                    accountViewModel.removePrivateBookmark(note)
                    onDismiss()
                },
            )
        } else {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_to_private_bookmarks)) },
                onClick = {
                    accountViewModel.addPrivateBookmark(note)
                    onDismiss()
                },
            )
        }
        if (state.isPublicBookmarkNote) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.remove_from_public_bookmarks)) },
                onClick = {
                    accountViewModel.removePublicBookmark(note)
                    onDismiss()
                },
            )
        } else {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_to_public_bookmarks)) },
                onClick = {
                    accountViewModel.addPublicBookmark(note)
                    onDismiss()
                },
            )
        }
        HorizontalDivider(thickness = DividerThickness)
        if (state.showSensitiveContent == null || state.showSensitiveContent == true) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.content_warning_hide_all_sensitive_content)) },
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        accountViewModel.hideSensitiveContent()
                        onDismiss()
                    }
                },
            )
        }
        if (state.showSensitiveContent == null || state.showSensitiveContent == false) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.content_warning_show_all_sensitive_content)) },
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        accountViewModel.disableContentWarnings()
                        onDismiss()
                    }
                },
            )
        }
        if (state.showSensitiveContent != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.content_warning_see_warnings)) },
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        accountViewModel.seeContentWarnings()
                        onDismiss()
                    }
                },
            )
        }
        HorizontalDivider(thickness = DividerThickness)
        if (state.isLoggedUser) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.request_deletion)) },
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        accountViewModel.delete(note)
                        onDismiss()
                    }
                },
            )
        } else {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.block_report)) },
                onClick = { reportDialogShowing = true },
            )
        }
    }

    if (reportDialogShowing) {
        ReportNoteDialog(note = note, accountViewModel = accountViewModel) {
            reportDialogShowing = false
            onDismiss()
        }
    }
}

@Composable
fun WatchBookmarksFollowsAndAccount(
    note: Note,
    accountViewModel: AccountViewModel,
    onNew: (DropDownParams) -> Unit,
) {
    val followState by accountViewModel.userProfile().live().follows.observeAsState()
    val bookmarkState by accountViewModel.userProfile().live().bookmarks.observeAsState()
    val showSensitiveContent by
        accountViewModel.showSensitiveContentChanges.observeAsState(
            accountViewModel.account.showSensitiveContent,
        )

    LaunchedEffect(key1 = followState, key2 = bookmarkState, key3 = showSensitiveContent) {
        launch(Dispatchers.IO) {
            accountViewModel.isInPrivateBookmarks(note) {
                val newState =
                    DropDownParams(
                        isFollowingAuthor = accountViewModel.isFollowing(note.author),
                        isPrivateBookmarkNote = it,
                        isPublicBookmarkNote = accountViewModel.isInPublicBookmarks(note),
                        isLoggedUser = accountViewModel.isLoggedUser(note.author),
                        isSensitive = note.event?.isSensitive() ?: false,
                        showSensitiveContent = showSensitiveContent,
                    )

                launch(Dispatchers.Main) {
                    onNew(
                        newState,
                    )
                }
            }
        }
    }
}
