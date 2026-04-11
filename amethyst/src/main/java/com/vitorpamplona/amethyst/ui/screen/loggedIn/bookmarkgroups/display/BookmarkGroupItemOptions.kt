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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.display

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.platform.StringResolver
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.quick_action_share
import com.vitorpamplona.amethyst.commons.resources.quick_action_share_browser_link
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.actions.EditPostView
import com.vitorpamplona.amethyst.ui.components.ClickableBox
import com.vitorpamplona.amethyst.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.ui.components.M3ActionDialog
import com.vitorpamplona.amethyst.ui.components.M3ActionRow
import com.vitorpamplona.amethyst.ui.components.M3ActionSection
import com.vitorpamplona.amethyst.ui.components.util.setText
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.routes.routeEditDraftTo
import com.vitorpamplona.amethyst.ui.note.VerticalDotsIcon
import com.vitorpamplona.amethyst.ui.note.elements.DropDownParams
import com.vitorpamplona.amethyst.ui.note.elements.observeBookmarksFollowsAndAccount
import com.vitorpamplona.amethyst.ui.note.externalLinkForNote
import com.vitorpamplona.amethyst.ui.note.types.EditState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.report.ReportNoteDialog
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size24Modifier
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun BookmarkGroupItemOptions(
    baseNote: Note,
    isBookmarkItemPrivate: Boolean,
    onMoveBookmarkToPublic: () -> Unit,
    onMoveBookmarkToPrivate: () -> Unit,
    onDeleteBookmarkItem: () -> Unit,
    editState: State<GenericLoadable<EditState>>? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val popupExpanded = remember { mutableStateOf(false) }

    ClickableBox(
        modifier = Size24Modifier,
        onClick = { popupExpanded.value = true },
    ) {
        VerticalDotsIcon()
    }

    if (popupExpanded.value) {
        BookmarkGroupItemOptionsMenu(
            note = baseNote,
            isBookmarkItemPrivate = isBookmarkItemPrivate,
            onDismiss = { popupExpanded.value = false },
            onMoveBookmarkToPublic = onMoveBookmarkToPublic,
            onMoveBookmarkToPrivate = onMoveBookmarkToPrivate,
            onDeleteBookmarkItem = onDeleteBookmarkItem,
            editState = editState,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
fun BookmarkGroupItemOptionsMenu(
    note: Note,
    isBookmarkItemPrivate: Boolean,
    onDismiss: () -> Unit,
    onMoveBookmarkToPublic: () -> Unit,
    onMoveBookmarkToPrivate: () -> Unit,
    onDeleteBookmarkItem: () -> Unit,
    editState: State<GenericLoadable<EditState>>? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var reportDialogShowing by remember { mutableStateOf(false) }

    val state by observeBookmarksFollowsAndAccount(note, accountViewModel).collectAsStateWithLifecycle(
        DropDownParams(
            isFollowingAuthor = false,
            isPrivateBookmarkNote = false,
            isPublicBookmarkNote = false,
            isPinnedNote = false,
            isLoggedUser = false,
            isSensitive = false,
            showSensitiveContent = null,
        ),
    )

    val wantsToEditPost =
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
                onDismiss()
                wantsToEditPost.value = false
            },
            edit = note,
            versionLookingAt = versionLookingAt,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

    M3ActionDialog(
        title = stringRes(R.string.bookmark_item_actions_dialog_title),
        onDismiss = onDismiss,
    ) {
        val clipboardManager = LocalClipboard.current
        val actContext = LocalContext.current
        val scope = rememberCoroutineScope()

        // Bookmark Management section
        M3ActionSection {
            M3ActionRow(
                icon = if (isBookmarkItemPrivate) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                text = stringRes(if (isBookmarkItemPrivate) R.string.move_bookmark_to_public_label else R.string.move_bookmark_to_private_label),
            ) {
                if (isBookmarkItemPrivate) onMoveBookmarkToPublic() else onMoveBookmarkToPrivate()
            }
            M3ActionRow(
                icon = Icons.Outlined.BookmarkRemove,
                text = stringRes(R.string.bookmark_remove_action_label),
                isDestructive = true,
            ) {
                onDeleteBookmarkItem()
                onDismiss()
            }
        }

        // Follow section
        M3ActionSection {
            if (!state.isFollowingAuthor) {
                M3ActionRow(icon = Icons.Outlined.PersonAdd, text = stringRes(R.string.follow)) {
                    val author = note.author ?: return@M3ActionRow
                    accountViewModel.follow(author)
                    onDismiss()
                }
            } else {
                M3ActionRow(icon = Icons.Outlined.PersonRemove, text = stringRes(R.string.unfollow)) {
                    val author = note.author ?: return@M3ActionRow
                    accountViewModel.unfollow(author)
                    onDismiss()
                }
            }
        }

        // Copy & Share section
        M3ActionSection {
            M3ActionRow(icon = Icons.Outlined.ContentCopy, text = stringRes(R.string.copy_text)) {
                val lastNoteVersion = (editState?.value as? GenericLoadable.Loaded)?.loaded?.modificationToShow?.value ?: note
                accountViewModel.decrypt(lastNoteVersion) {
                    scope.launch {
                        clipboardManager.setText(it)
                    }
                }
                onDismiss()
            }
            M3ActionRow(icon = Icons.Outlined.ContentCopy, text = stringRes(R.string.copy_user_pubkey)) {
                note.author?.let {
                    scope.launch(Dispatchers.IO) {
                        clipboardManager.setText("nostr:${it.pubkeyNpub()}")
                        onDismiss()
                    }
                }
            }
            M3ActionRow(icon = Icons.Outlined.ContentCopy, text = stringRes(R.string.copy_note_id)) {
                scope.launch(Dispatchers.IO) {
                    clipboardManager.setText(note.toNostrUri())
                    onDismiss()
                }
            }
            M3ActionRow(icon = Icons.Outlined.Share, text = stringRes(R.string.quick_action_share)) {
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
                            StringResolver.resolve(Res.string.quick_action_share_browser_link),
                        )
                    }

                val shareIntent =
                    Intent.createChooser(sendIntent, StringResolver.resolve(Res.string.quick_action_share))
                actContext.startActivity(shareIntent)
                onDismiss()
            }
        }

        // Edit & Broadcast section
        M3ActionSection {
            if (state.isLoggedUser && note.isDraft()) {
                M3ActionRow(icon = Icons.Outlined.Edit, text = stringRes(R.string.edit_draft)) {
                    nav.nav {
                        routeEditDraftTo(note, accountViewModel.account)
                    }
                }
            }
            if (!note.isDraft()) {
                if (note.event is TextNoteEvent) {
                    if (state.isLoggedUser) {
                        M3ActionRow(icon = Icons.Outlined.Edit, text = stringRes(R.string.edit_post)) {
                            wantsToEditPost.value = true
                        }
                    } else {
                        M3ActionRow(icon = Icons.Outlined.Edit, text = stringRes(R.string.propose_an_edit)) {
                            wantsToEditPost.value = true
                        }
                    }
                } else if (note.event is LongTextNoteEvent && state.isLoggedUser) {
                    M3ActionRow(icon = Icons.Outlined.Edit, text = stringRes(R.string.edit_article)) {
                        nav.nav { Route.NewLongFormPost(version = note.idHex) }
                    }
                }
            }
            M3ActionRow(icon = Icons.Outlined.CellTower, text = stringRes(R.string.broadcast)) {
                accountViewModel.broadcast(note)
                onDismiss()
            }
        }

        // Timestamp & Moderation section
        M3ActionSection {
            if (accountViewModel.account.otsState.hasPendingAttestations(note)) {
                M3ActionRow(icon = Icons.Outlined.Schedule, text = stringRes(R.string.timestamp_pending)) {
                    onDismiss()
                }
            } else {
                M3ActionRow(icon = Icons.Outlined.Schedule, text = stringRes(R.string.timestamp_it)) {
                    accountViewModel.timestamp(note)
                    onDismiss()
                }
            }
            if (state.isLoggedUser) {
                M3ActionRow(icon = Icons.Outlined.Delete, text = stringRes(R.string.request_deletion), isDestructive = true) {
                    accountViewModel.delete(note)
                    onDismiss()
                }
            } else {
                M3ActionRow(icon = Icons.Outlined.Report, text = stringRes(R.string.block_report), isDestructive = true) {
                    reportDialogShowing = true
                }
            }
        }
    }

    if (reportDialogShowing) {
        ReportNoteDialog(note = note, accountViewModel = accountViewModel) {
            reportDialogShowing = false
            onDismiss()
        }
    }
}
