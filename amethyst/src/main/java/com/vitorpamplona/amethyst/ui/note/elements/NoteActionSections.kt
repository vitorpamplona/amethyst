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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.util.setText
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip23LongContent.LongTextNoteEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One note action, rendered as a menu row or an action-sheet tile. */
@Immutable
data class NoteAction(
    val symbol: MaterialSymbol,
    val label: String,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Callbacks for actions whose UI is owned by the rendering surface (dialogs,
 * sheets), so the shared inventory stays surface-agnostic.
 */
@Immutable
data class NoteActionHandlers(
    val onShare: () -> Unit,
    val onEditPost: () -> Unit,
    val onEditDraft: () -> Unit,
    val onAddLabel: () -> Unit,
    val onReport: () -> Unit,
    val onDeleteRequest: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * The shared note-action inventory behind both the 3-dot menu (NoteDropDownMenu)
 * and the chat long-press sheet: one list of sections, each a list of actions with
 * their visibility gating applied, so the two surfaces can never drift.
 *
 * [noteVersionToCopy] lets the menu copy the latest edit of a versioned post; every
 * other caller passes [note].
 */
@Composable
fun noteActionSections(
    note: Note,
    noteVersionToCopy: Note,
    state: DropDownParams,
    handlers: NoteActionHandlers,
    accountViewModel: AccountViewModel,
    nav: INav,
): List<List<NoteAction>> {
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    // Unsealed rumors (private replies/posts received in gift wraps) are
    // unsigned and must never be referenced by a public event: hide every
    // action that would publish an e-tag of this note (broadcast, edit,
    // OTS timestamp, pin, label, public bookmark).
    val isPrivateRumor = note.isPrivateRumor()

    val author =
        buildList {
            if (!state.isLoggedUser) {
                if (!state.isFollowingAuthor) {
                    add(
                        NoteAction(MaterialSymbols.PersonAdd, stringRes(R.string.follow)) {
                            note.author?.let { accountViewModel.follow(it) }
                            handlers.onDismiss()
                        },
                    )
                } else {
                    add(
                        NoteAction(MaterialSymbols.PersonRemove, stringRes(R.string.unfollow)) {
                            note.author?.let { accountViewModel.unfollow(it) }
                            handlers.onDismiss()
                        },
                    )
                }
            }

            add(
                NoteAction(MaterialSymbols.AutoMirrored.PlaylistAdd, stringRes(R.string.follow_set_add_author_from_note_action)) {
                    note.author?.pubkeyHex?.let { nav.nav(Route.PeopleListManagement(it)) }
                    handlers.onDismiss()
                },
            )
        }

    val copyAndShare =
        buildList {
            add(
                NoteAction(MaterialSymbols.ContentCopy, stringRes(R.string.copy_text)) {
                    accountViewModel.decrypt(noteVersionToCopy) {
                        scope.launch { clipboardManager.setText(it) }
                    }
                    handlers.onDismiss()
                },
            )
            add(
                NoteAction(MaterialSymbols.AlternateEmail, stringRes(R.string.copy_user_pubkey)) {
                    note.author?.let {
                        scope.launch(Dispatchers.IO) {
                            clipboardManager.setText("nostr:${it.pubkeyNpub()}")
                            handlers.onDismiss()
                        }
                    }
                },
            )
            add(
                NoteAction(MaterialSymbols.FormatQuote, stringRes(R.string.copy_note_id)) {
                    scope.launch(Dispatchers.IO) {
                        clipboardManager.setText(note.toNostrUri())
                        handlers.onDismiss()
                    }
                },
            )
            add(
                NoteAction(MaterialSymbols.ContentCopy, stringRes(R.string.copy_raw_json)) {
                    val event = note.event
                    if (event != null) {
                        scope.launch {
                            val json = withContext(Dispatchers.Default) { JacksonMapper.toJsonPretty(event) }
                            clipboardManager.setText(json)
                            handlers.onDismiss()
                        }
                    } else {
                        handlers.onDismiss()
                    }
                },
            )
            if (!isPrivateRumor) {
                add(NoteAction(MaterialSymbols.Share, stringRes(R.string.quick_action_share), onClick = handlers.onShare))
            }
        }

    val editAndBroadcast =
        buildList {
            if (state.isLoggedUser && note.isDraft()) {
                add(NoteAction(MaterialSymbols.Edit, stringRes(R.string.edit_draft), onClick = handlers.onEditDraft))
            }
            if (!note.isDraft() && !isPrivateRumor) {
                if (note.event is TextNoteEvent) {
                    add(
                        NoteAction(
                            MaterialSymbols.Edit,
                            stringRes(if (state.isLoggedUser) R.string.edit_post else R.string.propose_an_edit),
                            onClick = handlers.onEditPost,
                        ),
                    )
                } else if (note.event is LongTextNoteEvent && state.isLoggedUser) {
                    add(
                        NoteAction(MaterialSymbols.Edit, stringRes(R.string.edit_article)) {
                            nav.nav { Route.NewLongFormPost(version = note.idHex) }
                        },
                    )
                }
            }
            // Rumors are rebroadcast as their delivering gift wrap; hidden when the
            // wrap is unknown (the unsigned rumor must never be published).
            if (accountViewModel.canBroadcast(note)) {
                add(
                    NoteAction(MaterialSymbols.CellTower, stringRes(R.string.broadcast)) {
                        accountViewModel.broadcast(note)
                        handlers.onDismiss()
                    },
                )
            }
        }

    val organize =
        buildList {
            if (!isPrivateRumor) {
                if (accountViewModel.account.otsState.hasPendingAttestations(note)) {
                    add(NoteAction(MaterialSymbols.Schedule, stringRes(R.string.timestamp_pending)) { handlers.onDismiss() })
                } else {
                    add(
                        NoteAction(MaterialSymbols.Schedule, stringRes(R.string.timestamp_it)) {
                            accountViewModel.timestamp(note)
                            handlers.onDismiss()
                        },
                    )
                }
            }
            if (state.isLoggedUser && !isPrivateRumor) {
                add(
                    NoteAction(
                        MaterialSymbols.PushPin,
                        stringRes(if (state.isPinnedNote) R.string.unpin_from_profile else R.string.pin_to_profile),
                    ) {
                        if (state.isPinnedNote) {
                            accountViewModel.removePin(note)
                        } else {
                            accountViewModel.addPin(note)
                        }
                        handlers.onDismiss()
                    },
                )
            }
            if (!isPrivateRumor) {
                add(NoteAction(MaterialSymbols.Tag, stringRes(R.string.add_hashtag_label), onClick = handlers.onAddLabel))
            }

            // Pick exactly one curation flow per kind: music tracks go to playlists,
            // emoji packs go to the emoji list, everything else gets the standard
            // bookmark rows. No bookmark/playlist/emoji-list rows for private rumors:
            // those lists reference the note by id, which other devices can't resolve
            // from relays and public lists would leak.
            when {
                isPrivateRumor -> {}

                note.event is MusicTrackEvent && note is AddressableNote -> {
                    add(
                        NoteAction(MaterialSymbols.AutoMirrored.PlaylistAdd, stringRes(R.string.add_to_music_playlist)) {
                            nav.nav(Route.AddToMusicPlaylist(note.address.toValue()))
                            handlers.onDismiss()
                        },
                    )
                }

                note.event is EmojiPackEvent -> {
                    val emojiText =
                        if (state.isEmojiPackInMyList) {
                            stringRes(R.string.remove_from_emoji_list)
                        } else {
                            stringRes(R.string.add_to_emoji_list)
                        }
                    add(
                        NoteAction(MaterialSymbols.EmojiEmotions, emojiText) {
                            val address = (note as AddressableNote).address
                            nav.nav(Route.EmojiPackSelection(kind = EmojiPackEvent.KIND, pubKeyHex = address.pubKeyHex, dTag = address.dTag))
                            handlers.onDismiss()
                        },
                    )
                }

                else -> {
                    val noteBookmarkType = if (note.event is LongTextNoteEvent) stringRes(R.string.article) else stringRes(R.string.post)
                    add(
                        NoteAction(MaterialSymbols.BookmarkAdd, stringRes(R.string.manage_bookmark_label, noteBookmarkType)) {
                            if (note.event is LongTextNoteEvent) {
                                nav.nav(Route.ArticleBookmarkManagement((note as AddressableNote).address))
                            } else {
                                nav.nav(Route.PostBookmarkManagement(note.idHex))
                            }
                            handlers.onDismiss()
                        },
                    )
                    if (state.isPrivateBookmarkNote) {
                        add(
                            NoteAction(MaterialSymbols.LockOpen, stringRes(R.string.remove_from_private_bookmarks)) {
                                accountViewModel.removePrivateBookmark(note)
                                handlers.onDismiss()
                            },
                        )
                    } else {
                        add(
                            NoteAction(MaterialSymbols.Lock, stringRes(R.string.add_to_private_bookmarks)) {
                                accountViewModel.addPrivateBookmark(note)
                                handlers.onDismiss()
                            },
                        )
                    }
                    if (state.isPublicBookmarkNote) {
                        add(
                            NoteAction(MaterialSymbols.BookmarkRemove, stringRes(R.string.remove_from_public_bookmarks)) {
                                accountViewModel.removePublicBookmark(note)
                                handlers.onDismiss()
                            },
                        )
                    } else {
                        add(
                            NoteAction(MaterialSymbols.Bookmark, stringRes(R.string.add_to_public_bookmarks)) {
                                accountViewModel.addPublicBookmark(note)
                                handlers.onDismiss()
                            },
                        )
                    }
                }
            }
        }

    val moderation =
        buildList {
            val isThreadMuted = accountViewModel.isThreadMutedFor(note)
            add(
                NoteAction(
                    MaterialSymbols.AutoMirrored.VolumeOff,
                    stringRes(if (isThreadMuted) R.string.quick_action_unmute_thread else R.string.quick_action_mute_thread),
                ) {
                    if (isThreadMuted) {
                        accountViewModel.unmuteThread(note)
                    } else {
                        accountViewModel.muteThread(note)
                    }
                    handlers.onDismiss()
                },
            )

            // Own messages always get a delete affordance (the surface routes private
            // rumors through the gift-wrapped deletion); reporting yourself never
            // makes sense, so Report is others-only.
            if (state.isLoggedUser) {
                add(NoteAction(MaterialSymbols.Delete, stringRes(R.string.request_deletion), isDestructive = true, onClick = handlers.onDeleteRequest))
            } else {
                add(NoteAction(MaterialSymbols.Report, stringRes(R.string.block_report), isDestructive = true, onClick = handlers.onReport))
            }
        }

    return listOf(author, copyAndShare, editAndBroadcast, organize, moderation).filter { it.isNotEmpty() }
}
