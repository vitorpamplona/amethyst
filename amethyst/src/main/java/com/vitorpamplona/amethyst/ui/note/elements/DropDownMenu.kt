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
package com.vitorpamplona.amethyst.ui.note.elements

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.actions.EditPostView
import com.vitorpamplona.amethyst.ui.components.ClickableBox
import com.vitorpamplona.amethyst.ui.components.M3ActionDialog
import com.vitorpamplona.amethyst.ui.components.M3ActionRow
import com.vitorpamplona.amethyst.ui.components.M3ActionSection
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeEditDraftTo
import com.vitorpamplona.amethyst.ui.note.QuickActionAlertDialog
import com.vitorpamplona.amethyst.ui.note.VerticalDotsIcon
import com.vitorpamplona.amethyst.ui.note.types.EditState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.report.ReportNoteDialog
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size24Modifier
import com.vitorpamplona.quartz.nip01Core.tags.aTag.isTaggedAddressableNote
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitiveOrNSFW
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart

@Composable
fun MoreOptionsButton(
    baseNote: Note,
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
        NoteDropDownMenu(
            note = baseNote,
            onDismiss = { popupExpanded.value = false },
            editState = editState,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Immutable
data class DropDownParams(
    val isFollowingAuthor: Boolean,
    val isPrivateBookmarkNote: Boolean,
    val isPublicBookmarkNote: Boolean,
    val isPinnedNote: Boolean,
    val isLoggedUser: Boolean,
    val isSensitive: Boolean,
    val showSensitiveContent: Boolean?,
    val isEmojiPackInMyList: Boolean = false,
)

@Composable
fun NoteDropDownMenu(
    note: Note,
    onDismiss: () -> Unit,
    editState: State<GenericLoadable<EditState>>? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var reportDialogShowing by remember { mutableStateOf(false) }
    var addLabelDialogShowing by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var deleteConfirmationShowing by remember { mutableStateOf(false) }

    // Tapping "Share" hands the note's share options off to the shared Share
    // drawer (ShareOptionsBottomSheet). We render it INSTEAD of the menu dialog
    // — not on top of it — so the menu doesn't sit dimmed behind the sheet;
    // dismissing the drawer closes the whole menu.
    if (showShareSheet) {
        ShareOptionsBottomSheet(
            note = note,
            nav = nav,
            onDismiss = {
                showShareSheet = false
                onDismiss()
            },
        )
        return
    }

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

    // Own private rumors (NIP-17 DMs) must be retracted with a gift-wrapped
    // deletion — a public NIP-09 would e-tag the rumor id onto public relays.
    val performDelete = {
        if (note.isPrivateRumor()) {
            accountViewModel.deletePrivately(note)
        } else {
            accountViewModel.delete(note)
        }
    }

    if (deleteConfirmationShowing) {
        QuickActionAlertDialog(
            title = stringRes(R.string.quick_action_request_deletion_alert_title),
            textContent = stringRes(R.string.quick_action_request_deletion_alert_body),
            buttonIcon = MaterialSymbols.Delete,
            buttonText = stringRes(R.string.quick_action_delete_dialog_btn),
            onClickDoOnce = {
                performDelete()
                onDismiss()
            },
            onClickDontShowAgain = {
                performDelete()
                accountViewModel.account.settings.setHideDeleteRequestDialog()
                onDismiss()
            },
            onDismiss = { deleteConfirmationShowing = false },
        )
    }

    val handlers =
        NoteActionHandlers(
            onShare = { showShareSheet = true },
            onEditPost = { wantsToEditPost.value = true },
            onEditDraft = { nav.nav { routeEditDraftTo(note, accountViewModel.account) } },
            onAddLabel = { addLabelDialogShowing = true },
            onReport = { reportDialogShowing = true },
            onDeleteRequest = {
                if (accountViewModel.account.settings.hideDeleteRequestDialog) {
                    performDelete()
                    onDismiss()
                } else {
                    deleteConfirmationShowing = true
                }
            },
            onDismiss = onDismiss,
        )

    // "Copy Text" copies the newest version of a versioned post when the caller
    // is looking at one.
    val lastNoteVersion = (editState?.value as? GenericLoadable.Loaded)?.loaded?.modificationToShow?.value ?: note

    M3ActionDialog(
        title = stringRes(R.string.note_actions_dialog_title),
        onDismiss = onDismiss,
    ) {
        // The action inventory is shared with the chat long-press sheet
        // (noteActionSections), so the two surfaces cannot drift.
        noteActionSections(
            note = note,
            noteVersionToCopy = lastNoteVersion,
            state = state,
            handlers = handlers,
            accountViewModel = accountViewModel,
            nav = nav,
        ).forEach { section ->
            M3ActionSection {
                section.forEach { action ->
                    M3ActionRow(
                        icon = action.symbol,
                        text = action.label,
                        isDestructive = action.isDestructive,
                        onClick = action.onClick,
                    )
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

    if (addLabelDialogShowing) {
        AddHashtagLabelDialog(
            note = note,
            accountViewModel = accountViewModel,
            onDismiss = {
                addLabelDialogShowing = false
                onDismiss()
            },
        )
    }
}

@Composable
fun observeBookmarksFollowsAndAccount(
    note: Note,
    accountViewModel: AccountViewModel,
) = remember(note) {
    val noteIdForEmoji = if (note.event is EmojiPackEvent) note.idHex else null
    combine(
        accountViewModel.account.kind3FollowList.flow,
        accountViewModel.account.bookmarkState.bookmarks,
        accountViewModel.account.pinState.pinnedEventIdSet,
        accountViewModel.showSensitiveContent(),
        accountViewModel.account.emoji.getEmojiPackSelectionFlow(),
    ) { follows, bookmarks, pinnedIds, showSensitiveContent, emojiSelectionState ->
        val isEmojiPackInMyList =
            if (noteIdForEmoji != null) {
                emojiSelectionState.note.event?.isTaggedAddressableNote(noteIdForEmoji) == true
            } else {
                false
            }
        DropDownParams(
            isFollowingAuthor = note.author?.pubkeyHex in follows.authors,
            isPrivateBookmarkNote = note in bookmarks.private,
            isPublicBookmarkNote = note in bookmarks.public,
            isPinnedNote = note.idHex in pinnedIds,
            isLoggedUser = accountViewModel.isLoggedUser(note.author),
            isSensitive = note.event?.isSensitiveOrNSFW() ?: false,
            showSensitiveContent = showSensitiveContent,
            isEmojiPackInMyList = isEmojiPackInMyList,
        )
    }.onStart {
        emit(
            DropDownParams(
                isFollowingAuthor = note.author?.pubkeyHex?.let { accountViewModel.account.isFollowing(it) } ?: false,
                isPrivateBookmarkNote = note in accountViewModel.account.bookmarkState.bookmarks.value.private,
                isPublicBookmarkNote = note in accountViewModel.account.bookmarkState.bookmarks.value.public,
                isPinnedNote = accountViewModel.account.pinState.isPinned(note),
                isLoggedUser = accountViewModel.isLoggedUser(note.author),
                isSensitive = note.event?.isSensitiveOrNSFW() ?: false,
                showSensitiveContent = accountViewModel.showSensitiveContent().value,
                isEmojiPackInMyList =
                    noteIdForEmoji?.let {
                        accountViewModel.account.emoji
                            .getEmojiPackSelection()
                            ?.isTaggedAddressableNote(it) == true
                    } ?: false,
            ),
        )
    }.flowOn(Dispatchers.IO)
}
