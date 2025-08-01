/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterStart
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.note.DisplayDraftChat
import com.vitorpamplona.amethyst.ui.note.LikeReaction
import com.vitorpamplona.amethyst.ui.note.NoteQuickActionMenu
import com.vitorpamplona.amethyst.ui.note.RelayBadgesHorizontal
import com.vitorpamplona.amethyst.ui.note.RenderZapRaiser
import com.vitorpamplona.amethyst.ui.note.ReplyReaction
import com.vitorpamplona.amethyst.ui.note.WatchBlockAndReport
import com.vitorpamplona.amethyst.ui.note.WatchNoteEvent
import com.vitorpamplona.amethyst.ui.note.ZapReaction
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.DisplayZapSplits
import com.vitorpamplona.amethyst.ui.note.elements.DisplayLocation
import com.vitorpamplona.amethyst.ui.note.elements.DisplayPoW
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.layouts.ChatBubbleLayout
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types.RenderChangeChannelMetadataNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types.RenderCreateChannelNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types.RenderDraftEvent
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types.RenderEncryptedFile
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types.RenderRegularTextNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.IncognitoBadge
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.ReactionRowHeightChat
import com.vitorpamplona.amethyst.ui.theme.ReactionRowZapraiser
import com.vitorpamplona.amethyst.ui.theme.RowColSpacing
import com.vitorpamplona.amethyst.ui.theme.Size18Modifier
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geoHashOrScope
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip13Pow.strongPoWOrNull
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftEvent
import com.vitorpamplona.quartz.nip57Zaps.splits.hasZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiserAmount

@Composable
fun ChatroomMessageCompose(
    baseNote: Note,
    routeForLastRead: String?,
    innerQuote: Boolean = false,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
) {
    WatchNoteEvent(baseNote = baseNote, accountViewModel = accountViewModel) {
        WatchBlockAndReport(
            note = baseNote,
            showHiddenWarning = false,
            modifier = Modifier.fillMaxWidth(),
            accountViewModel = accountViewModel,
            nav = nav,
        ) { canPreview ->
            NormalChatNote(
                baseNote,
                routeForLastRead,
                innerQuote,
                canPreview,
                parentBackgroundColor,
                accountViewModel,
                nav,
                onWantsToReply,
                onWantsToEditDraft,
            )
        }
    }
}

@Composable
fun NormalChatNote(
    note: Note,
    routeForLastRead: String?,
    innerQuote: Boolean = false,
    canPreview: Boolean = true,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
) {
    val isLoggedInUser =
        remember(note.author) {
            accountViewModel.isLoggedUser(note.author)
        }

    if (routeForLastRead != null) {
        LaunchedEffect(key1 = routeForLastRead) {
            accountViewModel.loadAndMarkAsRead(routeForLastRead, note.createdAt())
        }
    }

    val drawAuthorInfo by
        remember(note) {
            derivedStateOf {
                val noteEvent = note.event
                if (accountViewModel.isLoggedUser(note.author)) {
                    false // never shows the user's pictures
                } else if (noteEvent is PrivateDmEvent) {
                    false // one-on-one, never shows it.
                } else if (noteEvent is ChatroomKeyable) {
                    // only shows in a group chat.
                    noteEvent.chatroomKey(accountViewModel.userProfile().pubkeyHex).users.size > 1
                } else {
                    true
                }
            }
        }

    ChatBubbleLayout(
        isLoggedInUser = isLoggedInUser,
        isDraft = note.event is DraftEvent,
        innerQuote = innerQuote,
        isComplete = accountViewModel.settings.featureSet == FeatureSetType.COMPLETE,
        hasDetailsToShow = note.zaps.isNotEmpty() || note.zapPayments.isNotEmpty() || note.reactions.isNotEmpty(),
        drawAuthorInfo = drawAuthorInfo,
        parentBackgroundColor = parentBackgroundColor,
        onClick = {
            if (note.event is ChannelCreateEvent) {
                nav.nav(Route.PublicChatChannel(note.idHex))
                true
            } else {
                false
            }
        },
        onAuthorClick = {
            note.author?.let {
                nav.nav(routeFor(it))
            }
        },
        actionMenu = { onDismiss ->
            NoteQuickActionMenu(
                note = note,
                onDismiss = onDismiss,
                onWantsToEditDraft = { onWantsToEditDraft(note) },
                accountViewModel = accountViewModel,
                nav = nav,
            )
        },
        drawAuthorLine = {
            DrawAuthorInfo(
                note,
                accountViewModel,
                nav,
            )
        },
        detailRow = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = ReactionRowHeightChat,
                ) {
                    IncognitoBadge(note)
                    ChatTimeAgo(note)
                    RelayBadgesHorizontal(note, accountViewModel, nav = nav)

                    Spacer(modifier = DoubleHorzSpacer)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = RowColSpacing) {
                        if (!note.isDraft()) {
                            ReplyReaction(
                                baseNote = note,
                                grayTint = MaterialTheme.colorScheme.placeholderText,
                                accountViewModel = accountViewModel,
                                showCounter = false,
                                iconSizeModifier = Size18Modifier,
                            ) {
                                onWantsToReply(note)
                            }
                            Spacer(StdHorzSpacer)
                            LikeReaction(note, MaterialTheme.colorScheme.placeholderText, accountViewModel, nav)

                            ZapReaction(note, MaterialTheme.colorScheme.placeholderText, accountViewModel, nav = nav)

                            val geo = remember(note) { note.event?.geoHashOrScope() }
                            if (geo != null) {
                                Spacer(StdHorzSpacer)
                                DisplayLocation(geo, accountViewModel, nav)
                            }

                            val pow = remember(note) { note.event?.strongPoWOrNull() }
                            if (pow != null) {
                                Spacer(StdHorzSpacer)
                                DisplayPoW(pow)
                            }
                        } else {
                            DisplayDraftChat()
                        }
                    }
                }
                LoadAndDisplayClickableZapraiser(note, accountViewModel)
            }
        },
    ) { bgColor ->
        MessageBubbleLines(
            note,
            innerQuote,
            bgColor,
            onWantsToReply,
            onWantsToEditDraft,
            canPreview,
            accountViewModel,
            nav,
        )
    }
}

@Composable
fun LoadAndDisplayClickableZapraiser(
    baseNote: Note,
    accountViewModel: AccountViewModel,
) {
    val zapraiserAmount = baseNote.event?.zapraiserAmount() ?: 0
    if (zapraiserAmount > 0) {
        val wantsToSeeReactions = rememberSaveable(baseNote) { mutableStateOf(false) }
        Spacer(StdVertSpacer)
        Box(
            modifier =
                ReactionRowZapraiser.clickable(
                    onClick = { wantsToSeeReactions.value = !wantsToSeeReactions.value },
                ),
            contentAlignment = CenterStart,
        ) {
            RenderZapRaiser(baseNote, zapraiserAmount, wantsToSeeReactions.value, accountViewModel)
        }
    }
}

@Composable
private fun MessageBubbleLines(
    baseNote: Note,
    innerQuote: Boolean,
    bgColor: MutableState<Color>,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    canPreview: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (baseNote.event !is DraftEvent) {
        RenderReplyRow(
            note = baseNote,
            innerQuote = innerQuote,
            bgColor = bgColor,
            accountViewModel = accountViewModel,
            nav = nav,
            onWantsToReply = onWantsToReply,
            onWantsToEditDraft = onWantsToEditDraft,
        )
    }

    NoteRow(
        note = baseNote,
        canPreview = canPreview,
        innerQuote = innerQuote,
        onWantsToReply = onWantsToReply,
        onWantsToEditDraft = onWantsToEditDraft,
        bgColor = bgColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    if (!innerQuote) {
        val noteEvent = baseNote.event
        val zapSplits = remember(noteEvent) { noteEvent?.hasZapSplitSetup() ?: false }
        if (zapSplits && noteEvent != null) {
            Column {
                DisplayZapSplits(noteEvent, false, accountViewModel, nav)

                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}

@Composable
fun RenderReplyRow(
    note: Note,
    innerQuote: Boolean,
    bgColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
) {
    if (!innerQuote && note.replyTo?.lastOrNull() != null) {
        RenderReply(note, bgColor, accountViewModel, nav, onWantsToReply, onWantsToEditDraft)
    }
}

@Composable
private fun RenderReply(
    note: Note,
    bgColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        @Suppress("ProduceStateDoesNotAssignValue")
        val replyTo =
            produceState(initialValue = note.replyTo?.lastOrNull()) {
                accountViewModel.unwrapIfNeeded(value) {
                    value = it
                }
            }

        replyTo.value?.let { note ->
            ChatroomMessageCompose(
                baseNote = note,
                routeForLastRead = null,
                innerQuote = true,
                parentBackgroundColor = bgColor,
                accountViewModel = accountViewModel,
                nav = nav,
                onWantsToReply = onWantsToReply,
                onWantsToEditDraft = onWantsToEditDraft,
            )
        }
    }
}

@Composable
fun NoteRow(
    note: Note,
    canPreview: Boolean,
    innerQuote: Boolean,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    bgColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (note.event) {
            is ChannelCreateEvent -> RenderCreateChannelNote(note, bgColor, accountViewModel, nav)
            is ChannelMetadataEvent -> RenderChangeChannelMetadataNote(note, bgColor, accountViewModel, nav)
            is DraftEvent -> RenderDraftEvent(note, canPreview, innerQuote, onWantsToReply, onWantsToEditDraft, bgColor, accountViewModel, nav)
            is ChatMessageEncryptedFileHeaderEvent -> RenderEncryptedFile(note, bgColor, accountViewModel, nav)
            else -> RenderRegularTextNote(note, canPreview, innerQuote, bgColor, accountViewModel, nav)
        }
    }
}
