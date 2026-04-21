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

import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import com.vitorpamplona.amethyst.model.AddressableNote
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
import com.vitorpamplona.amethyst.ui.note.externalLinkForNote
import com.vitorpamplona.amethyst.ui.note.types.EditState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.report.ReportNoteDialog
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size24Modifier
import com.vitorpamplona.quartz.nip01Core.tags.aTag.isTaggedAddressableNote
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitiveOrNSFW
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

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
        title = stringRes(R.string.note_actions_dialog_title),
        onDismiss = onDismiss,
    ) {
        val clipboardManager = LocalClipboard.current
        val actContext = LocalContext.current
        val scope = rememberCoroutineScope()

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
            M3ActionRow(icon = Icons.AutoMirrored.Outlined.PlaylistAdd, text = stringRes(R.string.follow_set_add_author_from_note_action)) {
                val authorHexKey = note.author?.pubkeyHex ?: return@M3ActionRow
                nav.nav(Route.PeopleListManagement(authorHexKey))
                onDismiss()
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
                        putExtra(Intent.EXTRA_TEXT, externalLinkForNote(note))
                        putExtra(Intent.EXTRA_TITLE, stringRes(actContext, R.string.quick_action_share_browser_link))
                    }
                val shareIntent = Intent.createChooser(sendIntent, stringRes(actContext, R.string.quick_action_share))
                actContext.startActivity(shareIntent)
                onDismiss()
            }
        }

        // Edit & Broadcast section
        M3ActionSection {
            if (state.isLoggedUser && note.isDraft()) {
                M3ActionRow(icon = Icons.Outlined.Edit, text = stringRes(R.string.edit_draft)) {
                    nav.nav { routeEditDraftTo(note, accountViewModel.account) }
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

        // Timestamp & Bookmarks section
        M3ActionSection {
            if (accountViewModel.account.otsState.hasPendingAttestations(note)) {
                M3ActionRow(icon = Icons.Outlined.Schedule, text = stringRes(R.string.timestamp_pending)) { onDismiss() }
            } else {
                M3ActionRow(icon = Icons.Outlined.Schedule, text = stringRes(R.string.timestamp_it)) {
                    accountViewModel.timestamp(note)
                    onDismiss()
                }
            }
            if (state.isLoggedUser) {
                if (state.isPinnedNote) {
                    M3ActionRow(icon = Icons.Outlined.PushPin, text = stringRes(R.string.unpin_from_profile)) {
                        accountViewModel.removePin(note)
                        onDismiss()
                    }
                } else {
                    M3ActionRow(icon = Icons.Outlined.PushPin, text = stringRes(R.string.pin_to_profile)) {
                        accountViewModel.addPin(note)
                        onDismiss()
                    }
                }
            }
            // Emoji packs belong in the user's emoji list (kind 10030), not the bookmark list.
            if (note.event is EmojiPackEvent) {
                val emojiText =
                    if (state.isEmojiPackInMyList) {
                        stringRes(R.string.remove_from_emoji_list)
                    } else {
                        stringRes(R.string.add_to_emoji_list)
                    }
                M3ActionRow(icon = Icons.Outlined.EmojiEmotions, text = emojiText) {
                    val address = (note as AddressableNote).address
                    nav.nav(Route.EmojiPackSelection(kind = EmojiPackEvent.KIND, pubKeyHex = address.pubKeyHex, dTag = address.dTag))
                    onDismiss()
                }
            } else {
                val noteBookmarkType = if (note.event is LongTextNoteEvent) stringRes(R.string.article) else stringRes(R.string.post)
                M3ActionRow(icon = Icons.Outlined.BookmarkAdd, text = stringRes(R.string.manage_bookmark_label, noteBookmarkType)) {
                    if (note.event is LongTextNoteEvent) {
                        nav.nav(Route.ArticleBookmarkManagement((note as AddressableNote).address))
                    } else {
                        nav.nav(Route.PostBookmarkManagement(note.idHex))
                    }
                    onDismiss()
                }
            }
            if (state.isPrivateBookmarkNote) {
                M3ActionRow(icon = Icons.Outlined.LockOpen, text = stringRes(R.string.remove_from_private_bookmarks)) {
                    accountViewModel.removePrivateBookmark(note)
                    onDismiss()
                }
            } else {
                M3ActionRow(icon = Icons.Outlined.Lock, text = stringRes(R.string.add_to_private_bookmarks)) {
                    accountViewModel.addPrivateBookmark(note)
                    onDismiss()
                }
            }
            if (state.isPublicBookmarkNote) {
                M3ActionRow(icon = Icons.Outlined.BookmarkRemove, text = stringRes(R.string.remove_from_public_bookmarks)) {
                    accountViewModel.removePublicBookmark(note)
                    onDismiss()
                }
            } else {
                M3ActionRow(icon = Icons.Outlined.Bookmark, text = stringRes(R.string.add_to_public_bookmarks)) {
                    accountViewModel.addPublicBookmark(note)
                    onDismiss()
                }
            }
        }

        // Moderation section
        M3ActionSection {
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
