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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.ChatBubbleMaxSizeModifier
import com.vitorpamplona.amethyst.ui.theme.ChatBubbleShapeMe
import com.vitorpamplona.amethyst.ui.theme.ChatBubbleShapeThem
import com.vitorpamplona.amethyst.ui.theme.ChatPaddingInnerQuoteModifier
import com.vitorpamplona.amethyst.ui.theme.ChatPaddingModifier
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.Font12SP
import com.vitorpamplona.amethyst.ui.theme.HalfTopPadding
import com.vitorpamplona.amethyst.ui.theme.ReactionRowHeightChat
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size15Modifier
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.chatAuthorBox
import com.vitorpamplona.amethyst.ui.theme.chatAuthorImage
import com.vitorpamplona.amethyst.ui.theme.mediumImportanceLink
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.ChannelMetadataEvent
import com.vitorpamplona.quartz.events.ChatMessageEvent
import com.vitorpamplona.quartz.events.DraftEvent
import com.vitorpamplona.quartz.events.EmptyTagList
import com.vitorpamplona.quartz.events.ImmutableListOfLists
import com.vitorpamplona.quartz.events.PrivateDmEvent
import com.vitorpamplona.quartz.events.toImmutableListOfLists

@Composable
fun ChatroomMessageCompose(
    baseNote: Note,
    routeForLastRead: String?,
    innerQuote: Boolean = false,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
) {
    WatchNoteEvent(baseNote = baseNote, accountViewModel = accountViewModel) {
        WatchBlockAndReport(
            note = baseNote,
            showHiddenWarning = innerQuote,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NormalChatNote(
    note: Note,
    routeForLastRead: String?,
    innerQuote: Boolean = false,
    canPreview: Boolean = true,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
) {
    val drawAuthorInfo by
        remember(note) {
            derivedStateOf {
                val noteEvent = note.event
                if (accountViewModel.isLoggedUser(note.author)) {
                    false // never shows the user's pictures
                } else if (noteEvent is PrivateDmEvent) {
                    false // one-on-one, never shows it.
                } else if (noteEvent is ChatMessageEvent) {
                    // only shows in a group chat.
                    noteEvent.chatroomKey(accountViewModel.userProfile().pubkeyHex).users.size > 1
                } else {
                    true
                }
            }
        }

    val loggedInColors = MaterialTheme.colorScheme.mediumImportanceLink
    val otherColors = MaterialTheme.colorScheme.subtleBorder
    val defaultBackground = MaterialTheme.colorScheme.background

    val backgroundBubbleColor =
        remember {
            if (accountViewModel.isLoggedUser(note.author)) {
                mutableStateOf(
                    loggedInColors.compositeOver(parentBackgroundColor?.value ?: defaultBackground),
                )
            } else {
                mutableStateOf(otherColors.compositeOver(parentBackgroundColor?.value ?: defaultBackground))
            }
        }
    val alignment: Arrangement.Horizontal =
        remember {
            if (accountViewModel.isLoggedUser(note.author)) {
                Arrangement.End
            } else {
                Arrangement.Start
            }
        }
    val shape: Shape =
        remember {
            if (accountViewModel.isLoggedUser(note.author)) {
                ChatBubbleShapeMe
            } else {
                ChatBubbleShapeThem
            }
        }

    if (routeForLastRead != null) {
        LaunchedEffect(key1 = routeForLastRead) {
            accountViewModel.loadAndMarkAsRead(routeForLastRead, note.createdAt())
        }
    }

    Column {
        Row(
            modifier = if (innerQuote) ChatPaddingInnerQuoteModifier else ChatPaddingModifier,
            horizontalArrangement = alignment,
        ) {
            val availableBubbleSize = remember { mutableIntStateOf(0) }
            var popupExpanded by remember { mutableStateOf(false) }

            val modif2 = if (innerQuote) Modifier else ChatBubbleMaxSizeModifier

            val showDetails =
                remember {
                    mutableStateOf(
                        if (accountViewModel.settings.featureSet == FeatureSetType.SIMPLIFIED) {
                            note.zaps.isNotEmpty() || note.zapPayments.isNotEmpty() || note.reactions.isNotEmpty()
                        } else {
                            true
                        },
                    )
                }

            val clickableModifier =
                remember {
                    Modifier.combinedClickable(
                        onClick = {
                            if (note.event is ChannelCreateEvent) {
                                nav("Channel/${note.idHex}")
                            } else {
                                if (accountViewModel.settings.featureSet == FeatureSetType.SIMPLIFIED) {
                                    showDetails.value = !showDetails.value
                                }
                            }
                        },
                        onLongClick = { popupExpanded = true },
                    )
                }

            Row(
                horizontalArrangement = alignment,
                modifier =
                    modif2.onSizeChanged {
                        if (availableBubbleSize.intValue != it.width) {
                            availableBubbleSize.intValue = it.width
                        }
                    },
            ) {
                Surface(
                    color = backgroundBubbleColor.value,
                    shape = shape,
                    modifier = clickableModifier,
                ) {
                    RenderBubble(
                        note,
                        drawAuthorInfo,
                        alignment,
                        innerQuote,
                        backgroundBubbleColor,
                        onWantsToReply,
                        onWantsToEditDraft,
                        canPreview,
                        availableBubbleSize,
                        showDetails,
                        accountViewModel,
                        nav,
                    )
                }
            }

            NoteQuickActionMenu(
                note = note,
                popupExpanded = popupExpanded,
                onDismiss = { popupExpanded = false },
                onWantsToEditDraft = { onWantsToEditDraft(note) },
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
private fun RenderBubble(
    baseNote: Note,
    drawAuthorInfo: Boolean,
    alignment: Arrangement.Horizontal,
    innerQuote: Boolean,
    backgroundBubbleColor: MutableState<Color>,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    canPreview: Boolean,
    availableBubbleSize: MutableState<Int>,
    showDetails: State<Boolean>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val bubbleSize = remember { mutableIntStateOf(0) }

    val bubbleModifier =
        remember {
            Modifier
                .padding(start = 10.dp, end = 10.dp, bottom = 5.dp)
                .onSizeChanged {
                    if (bubbleSize.intValue != it.width) {
                        bubbleSize.intValue = it.width
                    }
                }
        }

    Column(modifier = bubbleModifier) {
        MessageBubbleLines(
            drawAuthorInfo,
            baseNote,
            alignment,
            availableBubbleSize,
            innerQuote,
            backgroundBubbleColor,
            bubbleSize,
            onWantsToReply,
            onWantsToEditDraft,
            canPreview,
            showDetails,
            accountViewModel,
            nav,
        )
    }
}

@Composable
private fun MessageBubbleLines(
    drawAuthorInfo: Boolean,
    baseNote: Note,
    alignment: Arrangement.Horizontal,
    availableBubbleSize: MutableState<Int>,
    innerQuote: Boolean,
    backgroundBubbleColor: MutableState<Color>,
    bubbleSize: MutableState<Int>,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    canPreview: Boolean,
    showDetails: State<Boolean>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    if (drawAuthorInfo) {
        DrawAuthorInfo(
            baseNote,
            alignment,
            accountViewModel.settings.showProfilePictures.value,
            accountViewModel,
            nav,
        )
    }

    if (baseNote.event !is DraftEvent) {
        RenderReplyRow(
            note = baseNote,
            innerQuote = innerQuote,
            backgroundBubbleColor = backgroundBubbleColor,
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
        backgroundBubbleColor = backgroundBubbleColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    if (showDetails.value) {
        ConstrainedStatusRow(
            bubbleSize = bubbleSize,
            availableBubbleSize = availableBubbleSize,
            firstColumn = {
                if (baseNote.isDraft()) {
                    DisplayDraftChat()
                }
                IncognitoBadge(baseNote)
                ChatTimeAgo(baseNote)
                RelayBadgesHorizontal(baseNote, accountViewModel, nav = nav)
                Spacer(modifier = DoubleHorzSpacer)
            },
            secondColumn = {
                LikeReaction(baseNote, MaterialTheme.colorScheme.placeholderText, accountViewModel, nav)
                Spacer(modifier = StdHorzSpacer)
                ZapReaction(baseNote, MaterialTheme.colorScheme.placeholderText, accountViewModel, nav = nav)
                Spacer(modifier = DoubleHorzSpacer)
                ReplyReaction(
                    baseNote = baseNote,
                    grayTint = MaterialTheme.colorScheme.placeholderText,
                    accountViewModel = accountViewModel,
                    showCounter = false,
                    iconSizeModifier = Size15Modifier,
                ) {
                    onWantsToReply(baseNote)
                }
                Spacer(modifier = StdHorzSpacer)
            },
        )
    }
}

@Composable
private fun RenderReplyRow(
    note: Note,
    innerQuote: Boolean,
    backgroundBubbleColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
) {
    if (!innerQuote && note.replyTo?.lastOrNull() != null) {
        RenderReply(note, backgroundBubbleColor, accountViewModel, nav, onWantsToReply, onWantsToEditDraft)
    }
}

@Composable
private fun RenderReply(
    note: Note,
    backgroundBubbleColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
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
                parentBackgroundColor = backgroundBubbleColor,
                accountViewModel = accountViewModel,
                nav = nav,
                onWantsToReply = onWantsToReply,
                onWantsToEditDraft = onWantsToEditDraft,
            )
        }
    }
}

@Composable
private fun NoteRow(
    note: Note,
    canPreview: Boolean,
    innerQuote: Boolean,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    backgroundBubbleColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (note.event) {
            is ChannelCreateEvent -> RenderCreateChannelNote(note)
            is ChannelMetadataEvent -> RenderChangeChannelMetadataNote(note)
            is DraftEvent ->
                RenderDraftEvent(
                    note,
                    canPreview,
                    innerQuote,
                    onWantsToReply,
                    onWantsToEditDraft,
                    backgroundBubbleColor,
                    accountViewModel,
                    nav,
                )
            else ->
                RenderRegularTextNote(
                    note,
                    canPreview,
                    innerQuote,
                    backgroundBubbleColor,
                    accountViewModel,
                    nav,
                )
        }
    }
}

@Composable
private fun RenderDraftEvent(
    note: Note,
    canPreview: Boolean,
    innerQuote: Boolean,
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    backgroundBubbleColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    ObserveDraftEvent(note, accountViewModel) {
        Column {
            RenderReplyRow(
                note = it,
                innerQuote = innerQuote,
                backgroundBubbleColor = backgroundBubbleColor,
                accountViewModel = accountViewModel,
                nav = nav,
                onWantsToReply = onWantsToReply,
                onWantsToEditDraft = onWantsToEditDraft,
            )

            NoteRow(
                note = it,
                canPreview = canPreview,
                innerQuote = innerQuote,
                onWantsToReply = onWantsToReply,
                onWantsToEditDraft = onWantsToEditDraft,
                backgroundBubbleColor = backgroundBubbleColor,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
private fun ConstrainedStatusRow(
    bubbleSize: MutableState<Int>,
    availableBubbleSize: MutableState<Int>,
    firstColumn: @Composable () -> Unit,
    secondColumn: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier =
            with(LocalDensity.current) {
                Modifier
                    .padding(top = Size5dp)
                    .height(Size20dp)
                    .widthIn(
                        bubbleSize.value.toDp(),
                        availableBubbleSize.value.toDp(),
                    )
            },
    ) {
        Column(modifier = ReactionRowHeightChat) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = ReactionRowHeightChat,
            ) {
                firstColumn()
            }
        }

        Column(modifier = ReactionRowHeightChat) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = ReactionRowHeightChat,
            ) {
                secondColumn()
            }
        }
    }
}

@Composable
fun IncognitoBadge(baseNote: Note) {
    if (baseNote.event is ChatMessageEvent) {
        Icon(
            painter = painterResource(id = R.drawable.incognito),
            null,
            modifier =
                Modifier
                    .padding(top = 1.dp)
                    .size(14.dp),
            tint = MaterialTheme.colorScheme.placeholderText,
        )
        Spacer(modifier = StdHorzSpacer)
    } else if (baseNote.event is PrivateDmEvent) {
        Icon(
            painter = painterResource(id = R.drawable.incognito_off),
            null,
            modifier =
                Modifier
                    .padding(top = 1.dp)
                    .size(14.dp),
            tint = MaterialTheme.colorScheme.placeholderText,
        )
        Spacer(modifier = StdHorzSpacer)
    }
}

@Composable
fun ChatTimeAgo(baseNote: Note) {
    val nowStr = stringResource(id = R.string.now)

    val time by
        remember(baseNote) { derivedStateOf { timeAgoShort(baseNote.createdAt() ?: 0, nowStr) } }

    Text(
        text = time,
        color = MaterialTheme.colorScheme.placeholderText,
        fontSize = Font12SP,
        maxLines = 1,
    )
}

@Composable
private fun RenderRegularTextNote(
    note: Note,
    canPreview: Boolean,
    innerQuote: Boolean,
    backgroundBubbleColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    LoadDecryptedContentOrNull(note = note, accountViewModel = accountViewModel) { eventContent ->
        if (eventContent != null) {
            SensitivityWarning(
                note = note,
                accountViewModel = accountViewModel,
            ) {
                val tags = remember(note.event) { note.event?.tags()?.toImmutableListOfLists() ?: EmptyTagList }

                TranslatableRichTextViewer(
                    content = eventContent,
                    canPreview = canPreview,
                    quotesLeft = if (innerQuote) 0 else 1,
                    modifier = HalfTopPadding,
                    tags = tags,
                    backgroundColor = backgroundBubbleColor,
                    id = note.idHex,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        } else {
            TranslatableRichTextViewer(
                content = stringResource(id = R.string.could_not_decrypt_the_message),
                canPreview = true,
                quotesLeft = 0,
                modifier = HalfTopPadding,
                tags = EmptyTagList,
                backgroundColor = backgroundBubbleColor,
                id = note.idHex,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
private fun RenderChangeChannelMetadataNote(note: Note) {
    val noteEvent = note.event as? ChannelMetadataEvent ?: return

    val channelInfo = noteEvent.channelInfo()
    val text =
        note.author?.toBestDisplayName().toString() +
            " ${stringResource(R.string.changed_chat_name_to)} '" +
            (channelInfo.name ?: "") +
            "', ${stringResource(R.string.description_to)} '" +
            (channelInfo.about ?: "") +
            "', ${stringResource(R.string.and_picture_to)} '" +
            (channelInfo.picture ?: "") +
            "'"

    CreateTextWithEmoji(
        text = text,
        tags = note.author?.info?.tags,
    )
}

@Composable
private fun RenderCreateChannelNote(note: Note) {
    val noteEvent = note.event as? ChannelCreateEvent ?: return
    val channelInfo = remember { noteEvent.channelInfo() }

    val text =
        note.author?.toBestDisplayName().toString() +
            " ${stringResource(R.string.created)} " +
            (channelInfo.name ?: "") +
            " ${stringResource(R.string.with_description_of)} '" +
            (channelInfo.about ?: "") +
            "', ${stringResource(R.string.and_picture)} '" +
            (channelInfo.picture ?: "") +
            "'"

    CreateTextWithEmoji(
        text = text,
        tags = note.author?.info?.tags,
    )
}

@Composable
private fun DrawAuthorInfo(
    baseNote: Note,
    alignment: Arrangement.Horizontal,
    loadProfilePicture: Boolean,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    baseNote.author?.let {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = alignment,
            modifier =
                Modifier
                    .padding(top = Size10dp)
                    .clickable {
                        nav("User/${baseNote.author?.pubkeyHex}")
                    },
        ) {
            WatchAndDisplayUser(it, loadProfilePicture, accountViewModel, nav)
        }
    }
}

@Composable
private fun WatchAndDisplayUser(
    author: User,
    loadProfilePicture: Boolean,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val userState by author.live().userMetadataInfo.observeAsState()

    Box(chatAuthorBox, contentAlignment = Alignment.TopEnd) {
        InnerUserPicture(
            userHex = author.pubkeyHex,
            userPicture = userState?.picture,
            userName = userState?.bestName(),
            size = Size20dp,
            modifier = Modifier,
            accountViewModel = accountViewModel,
        )

        ObserveAndDisplayFollowingMark(author.pubkeyHex, Size5dp, accountViewModel)
    }

    if (userState != null) {
        DisplayMessageUsername(userState?.bestName() ?: author.pubkeyDisplayHex(), userState?.tags ?: EmptyTagList)
    } else {
        DisplayMessageUsername(author.pubkeyDisplayHex(), EmptyTagList)
    }
}

@Composable
private fun UserIcon(
    pubkeyHex: String,
    userProfilePicture: String?,
    loadProfilePicture: Boolean,
) {
    RobohashFallbackAsyncImage(
        robot = pubkeyHex,
        model = userProfilePicture,
        contentDescription = stringResource(id = R.string.profile_image),
        loadProfilePicture = loadProfilePicture,
        modifier = chatAuthorImage,
    )
}

@Composable
private fun DisplayMessageUsername(
    userDisplayName: String,
    userTags: ImmutableListOfLists<String>,
) {
    Spacer(modifier = StdHorzSpacer)
    CreateTextWithEmoji(
        text = userDisplayName,
        tags = userTags,
        maxLines = 1,
        fontWeight = FontWeight.Bold,
    )
}
