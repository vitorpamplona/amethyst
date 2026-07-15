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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteMinichatReplyCount
import com.vitorpamplona.amethyst.ui.components.LocalInlineQuoteRenderer
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
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types.RenderChatClip
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types.RenderChatRaid
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types.RenderChatZap
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types.RenderCreateChannelNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types.RenderDraftEvent
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types.RenderEncryptedFile
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types.RenderMarmotEncryptedMedia
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types.RenderRegularTextNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types.hasMip04Media
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
import com.vitorpamplona.quartz.nip10Notes.BaseNoteEvent
import com.vitorpamplona.quartz.nip13Pow.strongPoWOrNull
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip53LiveActivities.clip.LiveActivitiesClipEvent
import com.vitorpamplona.quartz.nip53LiveActivities.raid.LiveActivitiesRaidEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip57Zaps.splits.hasZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiserAmount
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as MaterialSymbolIcon

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
    onScrollToNote: ((Note) -> Unit)? = null,
    shouldHighlight: Boolean = false,
    onHighlightFinished: (() -> Unit)? = null,
    // Replaces the generic "post not found" blank while baseNote's event hasn't loaded. Used for
    // reply quotes inside a DM, where the target is simply older than the loaded window (see
    // LoadingReplyNote). Null keeps the default blank for every other caller.
    onBlank: (@Composable () -> Unit)? = null,
) {
    // Re-skin inline `nostr:...` quotes for everything inside this bubble: a quoted
    // chat message renders with the chat reply design instead of the quoted-note card.
    val inlineQuoteRenderer =
        remember(onWantsToReply, onWantsToEditDraft, onScrollToNote) {
            chatInlineQuoteRenderer(onWantsToReply, onWantsToEditDraft, onScrollToNote)
        }

    val onFound: @Composable () -> Unit = {
        WatchBlockAndReport(
            note = baseNote,
            showHiddenWarning = false,
            modifier = Modifier.fillMaxWidth(),
            accountViewModel = accountViewModel,
            nav = nav,
        ) { canPreview ->
            val event = baseNote.event
            if (event is LnZapEvent) {
                RenderChatZap(baseNote, accountViewModel, nav)
            } else if (event is LiveActivitiesRaidEvent) {
                RenderChatRaid(baseNote, accountViewModel, nav)
            } else if (event is LiveActivitiesClipEvent) {
                RenderChatClip(baseNote, accountViewModel, nav)
            } else {
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
                    onScrollToNote,
                    shouldHighlight,
                    onHighlightFinished,
                )
            }
        }
    }

    CompositionLocalProvider(LocalInlineQuoteRenderer provides inlineQuoteRenderer) {
        if (onBlank != null) {
            WatchNoteEvent(baseNote = baseNote, onNoteEventFound = onFound, onBlank = onBlank, accountViewModel = accountViewModel)
        } else {
            WatchNoteEvent(baseNote = baseNote, accountViewModel = accountViewModel, nav = nav, onNoteEventFound = onFound)
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
    onScrollToNote: ((Note) -> Unit)? = null,
    shouldHighlight: Boolean = false,
    onHighlightFinished: (() -> Unit)? = null,
) {
    val isLoggedInUser =
        remember(note.author) {
            accountViewModel.isLoggedUser(note.author)
        }

    if (routeForLastRead != null) {
        LaunchedEffect(key1 = routeForLastRead) {
            accountViewModel.loadAndMarkAsRead(routeForLastRead, note.createdAt(), dismissNotificationId = note.idHex)
        }
    }

    val drawAuthorInfo by
        remember(note) {
            derivedStateOf {
                val noteEvent = note.event
                when {
                    accountViewModel.isLoggedUser(note.author) -> false

                    // never shows the user's pictures
                    noteEvent is PrivateDmEvent -> false

                    // one-on-one, never shows it.
                    // only shows in a group chat.
                    noteEvent is ChatroomKeyable -> noteEvent.chatroomKey(accountViewModel.userProfile().pubkeyHex).users.size > 1

                    else -> true
                }
            }
        }

    ChatBubbleLayout(
        isLoggedInUser = isLoggedInUser,
        isDraft = note.event is DraftWrapEvent,
        innerQuote = innerQuote,
        isComplete = accountViewModel.settings.isCompleteUIMode(),
        hasDetailsToShow = note.zaps.isNotEmpty() || note.zapPayments.isNotEmpty() || note.reactions.isNotEmpty(),
        drawAuthorInfo = drawAuthorInfo,
        parentBackgroundColor = parentBackgroundColor,
        shouldHighlight = shouldHighlight,
        onHighlightFinished = onHighlightFinished,
        onClick = {
            if (note.event is ChannelCreateEvent) {
                nav.nav(Route.PublicChatChannel(note.idHex))
                true
            } else if (innerQuote && onScrollToNote != null) {
                onScrollToNote(note)
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
                    ChatExpiration(note)
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

                            MinichatReplyChip(note, accountViewModel, nav)

                            val geo = remember(note) { note.event?.geoHashOrScope() }
                            if (geo != null) {
                                Spacer(StdHorzSpacer)
                                DisplayLocation(geo, accountViewModel, nav)
                            }

                            val pow = remember(note) { note.event?.strongPoWOrNull() }
                            if (pow != null) {
                                Spacer(StdHorzSpacer)
                                DisplayPoW(pow, accountViewModel)
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
            onScrollToNote,
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
    onScrollToNote: ((Note) -> Unit)? = null,
) {
    if (baseNote.event !is DraftWrapEvent) {
        RenderReplyRow(
            note = baseNote,
            innerQuote = innerQuote,
            bgColor = bgColor,
            accountViewModel = accountViewModel,
            nav = nav,
            onWantsToReply = onWantsToReply,
            onWantsToEditDraft = onWantsToEditDraft,
            onScrollToNote = onScrollToNote,
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

/**
 * A chip on a chat message's action row showing how many kind-1111 thread ("minichat")
 * replies it has; tapping opens that thread. Shown only when there is at least one — an
 * inline reply is an ordinary message and isn't counted.
 *
 * Only shown where minichats are actually wired: the public chats (Concord, NIP-28, NIP-29).
 * NIP-17 DMs are deliberately excluded — most clients don't render kind-1111 replies in a DM
 * view, so a thread there would be a dead end.
 */
@Composable
private fun MinichatReplyChip(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val supportsMinichat =
        remember(note) {
            note.inGatherers?.any { it is ConcordChannel || it is PublicChatChannel || it is RelayGroupChannel } == true
        }
    if (!supportsMinichat) return

    val count by observeNoteMinichatReplyCount(note, accountViewModel)
    if (count > 0) {
        Spacer(StdHorzSpacer)
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.clickable { nav.nav(Route.ChatMinichat(note.idHex)) },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                MaterialSymbolIcon(
                    symbol = MaterialSymbols.Forum,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = pluralStringResource(R.plurals.chat_minichat_reply_count, count, count),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

/**
 * Id of a note whose reply-to preview should be suppressed on a message row. Set by a
 * thread view that already pins that note at the top (the minichat pins its root), so each
 * reply doesn't redundantly re-render the same parent as an inner quote. Null everywhere else.
 */
val LocalSuppressReplyToNoteId = compositionLocalOf<String?> { null }

@Composable
fun RenderReplyRow(
    note: Note,
    innerQuote: Boolean,
    bgColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    onScrollToNote: ((Note) -> Unit)? = null,
) {
    val replyTo = note.replyTo?.lastOrNull()
    val suppressId = LocalSuppressReplyToNoteId.current
    if (!innerQuote && replyTo != null && replyTo.idHex != suppressId && !isCitedInContent(note, replyTo)) {
        RenderReply(note, bgColor, accountViewModel, nav, onWantsToReply, onWantsToEditDraft, onScrollToNote)
    }
}

/**
 * Whether the reply target is already mentioned (`nostr:...` citation) in the middle of
 * the message text. If it is, the inline quote renderer draws it at that spot, so the
 * reply row would show the same message twice. Happens in MLS kind-9 chats, where the
 * `q`-tagged parent of a quote is also cited inline — `Note.replyTo` keeps both replies
 * and quotes for that kind.
 */
@Composable
private fun isCitedInContent(
    note: Note,
    replyTo: Note,
): Boolean =
    remember(note.event, replyTo) {
        (note.event as? BaseNoteEvent)?.findCitations()?.contains(replyTo.idHex) == true
    }

@Composable
private fun RenderReply(
    note: Note,
    bgColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    onScrollToNote: ((Note) -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        @Suppress("ProduceStateDoesNotAssignValue")
        val replyTo =
            produceState(initialValue = note.replyTo?.lastOrNull()) {
                accountViewModel.unwrapIfNeeded(value) {
                    value = it
                }
            }

        replyTo.value?.let { replyNote ->
            // For a DM, a reply target that hasn't arrived isn't lost — it's older than the loaded
            // window (and for gift wraps can't be fetched by id). Swap the generic blank for one that
            // walks history backward until it surfaces. Pick the pager by the PARENT's protocol; leave
            // public chats / marmot groups (not a DM event here) on the default blank.
            val replyBlank: (@Composable () -> Unit)? =
                when (note.event) {
                    is ChatMessageEvent, is ChatMessageEncryptedFileHeaderEvent -> {
                        { LoadingReplyNote(DmReplyProtocol.NIP17, accountViewModel) }
                    }
                    is PrivateDmEvent -> {
                        { LoadingReplyNote(DmReplyProtocol.NIP04, accountViewModel) }
                    }
                    else -> null
                }
            ChatroomMessageCompose(
                baseNote = replyNote,
                routeForLastRead = null,
                innerQuote = true,
                parentBackgroundColor = bgColor,
                accountViewModel = accountViewModel,
                nav = nav,
                onWantsToReply = onWantsToReply,
                onWantsToEditDraft = onWantsToEditDraft,
                onScrollToNote = onScrollToNote,
                onBlank = replyBlank,
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
        when {
            note.event is ChannelCreateEvent -> RenderCreateChannelNote(note, bgColor, accountViewModel, nav)
            note.event is ChannelMetadataEvent -> RenderChangeChannelMetadataNote(note, bgColor, accountViewModel, nav)
            note.event is DraftWrapEvent -> RenderDraftEvent(note, canPreview, innerQuote, onWantsToReply, onWantsToEditDraft, bgColor, accountViewModel, nav)
            note.event is ChatMessageEncryptedFileHeaderEvent -> RenderEncryptedFile(note, bgColor, accountViewModel, nav)
            hasMip04Media(note.event) -> RenderMarmotEncryptedMedia(note, bgColor, accountViewModel, nav)
            else -> RenderRegularTextNote(note, canPreview, innerQuote, bgColor, accountViewModel, nav)
        }
    }
}
