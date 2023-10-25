package com.vitorpamplona.amethyst.ui.note

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.components.CreateClickableTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.RobohashAsyncImageProxy
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.ChatBubbleMaxSizeModifier
import com.vitorpamplona.amethyst.ui.theme.ChatBubbleShapeMe
import com.vitorpamplona.amethyst.ui.theme.ChatBubbleShapeThem
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.Font12SP
import com.vitorpamplona.amethyst.ui.theme.ReactionRowHeightChat
import com.vitorpamplona.amethyst.ui.theme.Size15dp
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.mediumImportanceLink
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.ChannelMetadataEvent
import com.vitorpamplona.quartz.events.ChatMessageEvent
import com.vitorpamplona.quartz.events.EmptyTagList
import com.vitorpamplona.quartz.events.ImmutableListOfLists
import com.vitorpamplona.quartz.events.PrivateDmEvent
import com.vitorpamplona.quartz.events.toImmutableListOfLists

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatroomMessageCompose(
    baseNote: Note,
    routeForLastRead: String?,
    innerQuote: Boolean = false,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    onWantsToReply: (Note) -> Unit
) {
    val hasEvent by baseNote.live().hasEvent.observeAsState(baseNote.event != null)

    Crossfade(targetState = hasEvent) {
        if (it) {
            CheckHiddenChatMessage(
                baseNote,
                routeForLastRead,
                innerQuote,
                parentBackgroundColor,
                accountViewModel,
                nav,
                onWantsToReply
            )
        } else {
            LongPressToQuickAction(baseNote = baseNote, accountViewModel = accountViewModel) { showPopup ->
                BlankNote(
                    remember {
                        Modifier.combinedClickable(
                            onClick = { },
                            onLongClick = showPopup
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun CheckHiddenChatMessage(
    baseNote: Note,
    routeForLastRead: String?,
    innerQuote: Boolean = false,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    onWantsToReply: (Note) -> Unit
) {
    val isHidden by remember {
        accountViewModel.account.liveHiddenUsers.map {
            baseNote.isHiddenFor(it)
        }.distinctUntilChanged()
    }.observeAsState(false)

    if (!isHidden) {
        LoadedChatMessageCompose(
            baseNote,
            routeForLastRead,
            innerQuote,
            parentBackgroundColor,
            accountViewModel,
            nav,
            onWantsToReply
        )
    }
}

@Composable
fun LoadedChatMessageCompose(
    baseNote: Note,
    routeForLastRead: String?,
    innerQuote: Boolean = false,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    onWantsToReply: (Note) -> Unit
) {
    var state by remember {
        mutableStateOf(
            AccountViewModel.NoteComposeReportState()
        )
    }

    WatchForReports(baseNote, accountViewModel) { newState ->
        if (state != newState) {
            state = newState
        }
    }

    var showReportedNote by remember { mutableStateOf(false) }

    val showHiddenNote by remember(state, showReportedNote) {
        derivedStateOf {
            !state.isAcceptable && !showReportedNote
        }
    }

    Crossfade(targetState = showHiddenNote) {
        if (it) {
            HiddenNote(
                state.relevantReports,
                state.isHiddenAuthor,
                accountViewModel,
                Modifier,
                innerQuote,
                nav,
                onClick = { showReportedNote = true }
            )
        } else {
            val canPreview by remember(state, showReportedNote) {
                derivedStateOf {
                    (!state.isAcceptable && showReportedNote) || state.canPreview
                }
            }

            NormalChatNote(
                baseNote,
                routeForLastRead,
                innerQuote,
                canPreview,
                parentBackgroundColor,
                accountViewModel,
                nav,
                onWantsToReply
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
    onWantsToReply: (Note) -> Unit
) {
    val drawAuthorInfo by remember {
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

    val backgroundBubbleColor = remember {
        if (accountViewModel.isLoggedUser(note.author)) {
            mutableStateOf(loggedInColors.compositeOver(parentBackgroundColor?.value ?: defaultBackground))
        } else {
            mutableStateOf(otherColors.compositeOver(parentBackgroundColor?.value ?: defaultBackground))
        }
    }
    val alignment: Arrangement.Horizontal = remember {
        if (accountViewModel.isLoggedUser(note.author)) {
            Arrangement.End
        } else {
            Arrangement.Start
        }
    }
    val shape: Shape = remember {
        if (accountViewModel.isLoggedUser(note.author)) {
            ChatBubbleShapeMe
        } else {
            ChatBubbleShapeThem
        }
    }

    if (routeForLastRead != null) {
        LaunchedEffect(key1 = routeForLastRead) {
            accountViewModel.loadAndMarkAsRead(routeForLastRead, note.createdAt()) { }
        }
    }

    Column() {
        val modif = remember {
            if (innerQuote) {
                Modifier.padding(top = 10.dp, end = 5.dp)
            } else {
                Modifier
                    .fillMaxWidth(1f)
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = 5.dp,
                        bottom = 5.dp
                    )
            }
        }

        Row(
            modifier = modif,
            horizontalArrangement = alignment
        ) {
            val availableBubbleSize = remember { mutableIntStateOf(0) }
            var popupExpanded by remember { mutableStateOf(false) }

            val modif2 = remember {
                if (innerQuote) Modifier else ChatBubbleMaxSizeModifier
            }

            val clickableModifier = remember {
                Modifier
                    .combinedClickable(
                        onClick = {
                            if (note.event is ChannelCreateEvent) {
                                nav("Channel/${note.idHex}")
                            }
                        },
                        onLongClick = { popupExpanded = true }
                    )
            }

            Row(
                horizontalArrangement = alignment,
                modifier = modif2.onSizeChanged {
                    if (availableBubbleSize.value != it.width) {
                        availableBubbleSize.value = it.width
                    }
                }
            ) {
                Surface(
                    color = backgroundBubbleColor.value,
                    shape = shape,
                    modifier = clickableModifier
                ) {
                    RenderBubble(
                        note,
                        drawAuthorInfo,
                        alignment,
                        innerQuote,
                        backgroundBubbleColor,
                        onWantsToReply,
                        canPreview,
                        availableBubbleSize,
                        accountViewModel,
                        nav
                    )
                }
            }

            NoteQuickActionMenu(
                note = note,
                popupExpanded = popupExpanded,
                onDismiss = { popupExpanded = false },
                accountViewModel = accountViewModel
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
    canPreview: Boolean,
    availableBubbleSize: MutableState<Int>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val bubbleSize = remember { mutableIntStateOf(0) }

    val bubbleModifier = remember {
        Modifier
            .padding(start = 10.dp, end = 5.dp, bottom = 5.dp)
            .onSizeChanged {
                if (bubbleSize.value != it.width) {
                    bubbleSize.value = it.width
                }
            }
    }

    Column(modifier = bubbleModifier) {
        MessageBubbleLines(
            drawAuthorInfo,
            baseNote,
            alignment,
            nav,
            innerQuote,
            backgroundBubbleColor,
            accountViewModel,
            onWantsToReply,
            canPreview,
            bubbleSize,
            availableBubbleSize
        )
    }
}

@Composable
private fun MessageBubbleLines(
    drawAuthorInfo: Boolean,
    baseNote: Note,
    alignment: Arrangement.Horizontal,
    nav: (String) -> Unit,
    innerQuote: Boolean,
    backgroundBubbleColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    onWantsToReply: (Note) -> Unit,
    canPreview: Boolean,
    bubbleSize: MutableState<Int>,
    availableBubbleSize: MutableState<Int>
) {
    val automaticallyShowProfilePicture = remember {
        accountViewModel.settings.showProfilePictures.value
    }

    if (drawAuthorInfo) {
        DrawAuthorInfo(
            baseNote,
            alignment,
            automaticallyShowProfilePicture,
            nav
        )
    } else {
        Spacer(modifier = StdVertSpacer)
    }

    RenderReplyRow(
        note = baseNote,
        innerQuote = innerQuote,
        backgroundBubbleColor = backgroundBubbleColor,
        accountViewModel = accountViewModel,
        nav = nav,
        onWantsToReply = onWantsToReply
    )

    NoteRow(
        note = baseNote,
        canPreview = canPreview,
        backgroundBubbleColor = backgroundBubbleColor,
        accountViewModel = accountViewModel,
        nav = nav
    )

    ConstrainedStatusRow(
        bubbleSize = bubbleSize,
        availableBubbleSize = availableBubbleSize
    ) {
        StatusRow(
            baseNote = baseNote,
            accountViewModel = accountViewModel,
            nav = nav,
            onWantsToReply = onWantsToReply
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
    onWantsToReply: (Note) -> Unit
) {
    val hasReply by remember {
        derivedStateOf {
            !innerQuote && note.replyTo?.lastOrNull() != null
        }
    }

    if (hasReply) {
        RenderReply(note, backgroundBubbleColor, accountViewModel, nav, onWantsToReply)
    }
}

@Composable
private fun RenderReply(
    note: Note,
    backgroundBubbleColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    onWantsToReply: (Note) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val replyTo by remember {
            derivedStateOf {
                note.replyTo?.lastOrNull()
            }
        }
        replyTo?.let { note ->
            ChatroomMessageCompose(
                note,
                null,
                innerQuote = true,
                parentBackgroundColor = backgroundBubbleColor,
                accountViewModel = accountViewModel,
                nav = nav,
                onWantsToReply = onWantsToReply
            )
        }
    }
}

@Composable
private fun NoteRow(
    note: Note,
    canPreview: Boolean,
    backgroundBubbleColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (remember(note) { note.event }) {
            is ChannelCreateEvent -> {
                RenderCreateChannelNote(note)
            }

            is ChannelMetadataEvent -> {
                RenderChangeChannelMetadataNote(note)
            }

            else -> {
                RenderRegularTextNote(
                    note,
                    canPreview,
                    backgroundBubbleColor,
                    accountViewModel,
                    nav
                )
            }
        }
    }
}

@Composable
private fun ConstrainedStatusRow(
    bubbleSize: MutableState<Int>,
    availableBubbleSize: MutableState<Int>,
    content: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = with(LocalDensity.current) {
            Modifier
                .height(26.dp)
                .padding(top = 5.dp)
                .widthIn(
                    bubbleSize.value.toDp(),
                    availableBubbleSize.value.toDp()
                )
        }
    ) {
        content()
    }
}

@Composable
private fun StatusRow(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    onWantsToReply: (Note) -> Unit
) {
    Column(modifier = ReactionRowHeightChat) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = ReactionRowHeightChat) {
            IncognitoBadge(baseNote)
            Spacer(modifier = StdHorzSpacer)
            ChatTimeAgo(baseNote)
            RelayBadgesHorizontal(baseNote, accountViewModel, nav = nav)
            Spacer(modifier = DoubleHorzSpacer)
        }
    }

    Column(modifier = ReactionRowHeightChat) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = ReactionRowHeightChat) {
            LikeReaction(baseNote, MaterialTheme.colorScheme.placeholderText, accountViewModel, nav)
            Spacer(modifier = StdHorzSpacer)
            ZapReaction(baseNote, MaterialTheme.colorScheme.placeholderText, accountViewModel, nav = nav)
            Spacer(modifier = DoubleHorzSpacer)
            ReplyReaction(
                baseNote = baseNote,
                grayTint = MaterialTheme.colorScheme.placeholderText,
                accountViewModel = accountViewModel,
                showCounter = false,
                iconSize = Size15dp
            ) {
                onWantsToReply(baseNote)
            }
            Spacer(modifier = StdHorzSpacer)
        }
    }
}

@Composable
fun IncognitoBadge(baseNote: Note) {
    if (baseNote.event is ChatMessageEvent) {
        Icon(
            painter = painterResource(id = R.drawable.incognito),
            null,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(14.dp),
            tint = MaterialTheme.colorScheme.placeholderText
        )
    } else if (baseNote.event is PrivateDmEvent) {
        Icon(
            painter = painterResource(id = R.drawable.incognito_off),
            null,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(14.dp),
            tint = MaterialTheme.colorScheme.placeholderText
        )
    }
}

@Composable
fun ChatTimeAgo(baseNote: Note) {
    val nowStr = stringResource(id = R.string.now)

    val time by remember(baseNote) {
        derivedStateOf {
            timeAgoShort(baseNote.createdAt() ?: 0, nowStr)
        }
    }

    Text(
        text = time,
        color = MaterialTheme.colorScheme.placeholderText,
        fontSize = Font12SP,
        maxLines = 1
    )
}

@Composable
private fun RenderRegularTextNote(
    note: Note,
    canPreview: Boolean,
    backgroundBubbleColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val tags = remember(note.event) { note.event?.tags()?.toImmutableListOfLists() ?: EmptyTagList }
    val eventContent by remember { mutableStateOf(accountViewModel.decrypt(note)) }
    val modifier = remember { Modifier.padding(top = 5.dp) }

    if (eventContent != null) {
        SensitivityWarning(
            note = note,
            accountViewModel = accountViewModel
        ) {
            TranslatableRichTextViewer(
                content = eventContent!!,
                canPreview = canPreview,
                modifier = modifier,
                tags = tags,
                backgroundColor = backgroundBubbleColor,
                accountViewModel = accountViewModel,
                nav = nav
            )
        }
    } else {
        TranslatableRichTextViewer(
            content = stringResource(id = R.string.could_not_decrypt_the_message),
            canPreview = true,
            modifier = modifier,
            tags = tags,
            backgroundColor = backgroundBubbleColor,
            accountViewModel = accountViewModel,
            nav = nav
        )
    }
}

@Composable
private fun RenderChangeChannelMetadataNote(
    note: Note
) {
    val noteEvent = note.event as? ChannelMetadataEvent ?: return

    val channelInfo = noteEvent.channelInfo()
    val text = note.author?.toBestDisplayName()
        .toString() + " ${stringResource(R.string.changed_chat_name_to)} '" + (
        channelInfo.name
            ?: ""
        ) + "', ${stringResource(R.string.description_to)} '" + (
        channelInfo.about
            ?: ""
        ) + "', ${stringResource(R.string.and_picture_to)} '" + (
        channelInfo.picture
            ?: ""
        ) + "'"

    CreateTextWithEmoji(
        text = text,
        tags = remember { note.author?.info?.latestMetadata?.tags?.toImmutableListOfLists() }
    )
}

@Composable
private fun RenderCreateChannelNote(note: Note) {
    val noteEvent = note.event as? ChannelCreateEvent ?: return
    val channelInfo = remember { noteEvent.channelInfo() }

    val text = note.author?.toBestDisplayName()
        .toString() + " ${stringResource(R.string.created)} " + (
        channelInfo.name
            ?: ""
        ) + " ${stringResource(R.string.with_description_of)} '" + (
        channelInfo.about
            ?: ""
        ) + "', ${stringResource(R.string.and_picture)} '" + (
        channelInfo.picture
            ?: ""
        ) + "'"

    CreateTextWithEmoji(
        text = text,
        tags = remember { note.author?.info?.latestMetadata?.tags?.toImmutableListOfLists() }
    )
}

@Composable
private fun DrawAuthorInfo(
    baseNote: Note,
    alignment: Arrangement.Horizontal,
    loadProfilePicture: Boolean,
    nav: (String) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = alignment,
        modifier = Modifier.padding(top = 5.dp)
    ) {
        DisplayAndWatchNoteAuthor(baseNote, loadProfilePicture, nav)
    }
}

@Composable
private fun DisplayAndWatchNoteAuthor(
    baseNote: Note,
    loadProfilePicture: Boolean,
    nav: (String) -> Unit
) {
    val author = remember {
        baseNote.author
    }
    author?.let {
        WatchAndDisplayUser(it, loadProfilePicture, nav)
    }
}

@Composable
private fun WatchAndDisplayUser(
    author: User,
    loadProfilePicture: Boolean,
    nav: (String) -> Unit
) {
    val pubkeyHex = remember { author.pubkeyHex }
    val route = remember { "User/${author.pubkeyHex}" }

    val userState by author.live().metadata.observeAsState()

    val userDisplayName by remember(userState) {
        derivedStateOf {
            userState?.user?.toBestDisplayName()
        }
    }

    val userProfilePicture by remember(userState) {
        derivedStateOf {
            userState?.user?.profilePicture()
        }
    }

    val userTags by remember(userState) {
        derivedStateOf {
            userState?.user?.info?.latestMetadata?.tags?.toImmutableListOfLists()
        }
    }

    UserIcon(pubkeyHex, userProfilePicture, loadProfilePicture, nav, route)

    userDisplayName?.let {
        DisplayMessageUsername(it, userTags, route, nav)
    }
}

@Composable
private fun UserIcon(
    pubkeyHex: String,
    userProfilePicture: String?,
    loadProfilePicture: Boolean,
    nav: (String) -> Unit,
    route: String
) {
    RobohashAsyncImageProxy(
        robot = pubkeyHex,
        model = userProfilePicture,
        contentDescription = stringResource(id = R.string.profile_image),
        loadProfilePicture = loadProfilePicture,
        modifier = remember {
            Modifier
                .width(Size25dp)
                .height(Size25dp)
                .clip(shape = CircleShape)
                .clickable(onClick = {
                    nav(route)
                })
        }
    )
}

@Composable
private fun DisplayMessageUsername(
    userDisplayName: String,
    userTags: ImmutableListOfLists<String>?,
    route: String,
    nav: (String) -> Unit
) {
    Spacer(modifier = StdHorzSpacer)
    CreateClickableTextWithEmoji(
        clickablePart = userDisplayName,
        suffix = "",
        maxLines = 1,
        tags = userTags,
        fontWeight = FontWeight.Bold,
        overrideColor = MaterialTheme.colorScheme.onBackground, // we do not want clickable names in purple here.
        route = route,
        nav = nav
    )

    Spacer(modifier = StdHorzSpacer)
    DrawPlayName(userDisplayName)
}
