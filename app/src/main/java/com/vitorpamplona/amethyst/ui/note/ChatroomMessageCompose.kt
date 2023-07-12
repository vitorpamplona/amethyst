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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.map
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import com.vitorpamplona.amethyst.ui.actions.ImmutableListOfLists
import com.vitorpamplona.amethyst.ui.actions.toImmutableListOfLists
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
import com.vitorpamplona.amethyst.ui.theme.ReactionRowHeightChat
import com.vitorpamplona.amethyst.ui.theme.Size15dp
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.mediumImportanceLink
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet

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
    val isBlank by baseNote.live().metadata.map {
        it.note.event == null
    }.observeAsState(baseNote.event == null)

    Crossfade(targetState = isBlank) {
        if (it) {
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
        } else {
            CheckHiddenChatMessage(
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
    val accountState by accountViewModel.accountLiveData.observeAsState()

    val isHidden by remember(accountState) {
        derivedStateOf {
            val isSensitive = baseNote.event?.isSensitive() ?: false

            accountState?.account?.isHidden(baseNote.author!!) == true || (isSensitive && accountState?.account?.showSensitiveContent == false)
        }
    }

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
            NoteComposeReportState(
                isAcceptable = true,
                canPreview = true,
                relevantReports = persistentSetOf()
            )
        )
    }

    WatchForReports(baseNote, accountViewModel) { newIsAcceptable, newCanPreview, newRelevantReports ->
        if (newIsAcceptable != state.isAcceptable || newCanPreview != state.canPreview) {
            state = NoteComposeReportState(newIsAcceptable, newCanPreview, newRelevantReports.toImmutableSet())
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
            note.event !is PrivateDmEvent && (innerQuote || !accountViewModel.isLoggedUser(note.author))
        }
    }

    val loggedInColors = MaterialTheme.colors.mediumImportanceLink
    val otherColors = MaterialTheme.colors.subtleBorder
    val defaultBackground = MaterialTheme.colors.background

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
            val createdAt = note.createdAt()
            if (createdAt != null) {
                accountViewModel.account.markAsRead(routeForLastRead, createdAt)
            }
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
            val availableBubbleSize = remember { mutableStateOf(0) }
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
    val bubbleSize = remember { mutableStateOf(0) }

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
    if (drawAuthorInfo) {
        DrawAuthorInfo(
            baseNote,
            alignment,
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
            ChatTimeAgo(baseNote)
            RelayBadgesHorizontal(baseNote, accountViewModel, nav = nav)
            Spacer(modifier = DoubleHorzSpacer)
        }
    }

    Column(modifier = ReactionRowHeightChat) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = ReactionRowHeightChat) {
            LikeReaction(baseNote, MaterialTheme.colors.placeholderText, accountViewModel)
            Spacer(modifier = StdHorzSpacer)
            ZapReaction(baseNote, MaterialTheme.colors.placeholderText, accountViewModel)
            Spacer(modifier = DoubleHorzSpacer)
            ReplyReaction(
                baseNote = baseNote,
                grayTint = MaterialTheme.colors.placeholderText,
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
fun ChatTimeAgo(baseNote: Note) {
    val context = LocalContext.current

    val timeStr by remember(baseNote) {
        derivedStateOf {
            timeAgoShort(baseNote.createdAt() ?: 0, context = context)
        }
    }

    Text(
        text = timeStr,
        color = MaterialTheme.colors.placeholderText,
        fontSize = 12.sp,
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
    val tags = remember(note.event) { note.event?.tags()?.toImmutableListOfLists() ?: ImmutableListOfLists() }
    val eventContent = remember { accountViewModel.decrypt(note) }
    val modifier = remember { Modifier.padding(top = 5.dp) }

    if (eventContent != null) {
        SensitivityWarning(
            note = note,
            accountViewModel = accountViewModel
        ) {
            TranslatableRichTextViewer(
                content = eventContent,
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
    nav: (String) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = alignment,
        modifier = Modifier.padding(top = 5.dp)
    ) {
        DisplayAndWatchNoteAuthor(baseNote, nav)
    }
}

@Composable
private fun DisplayAndWatchNoteAuthor(
    baseNote: Note,
    nav: (String) -> Unit
) {
    val author = remember {
        baseNote.author
    }
    author?.let {
        WatchAndDisplayUser(it, nav)
    }
}

@Composable
private fun WatchAndDisplayUser(
    author: User,
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

    UserIcon(pubkeyHex, userProfilePicture, nav, route)

    userDisplayName?.let {
        DisplayMessageUsername(it, userTags, route, nav)
    }
}

@Composable
private fun UserIcon(
    pubkeyHex: String,
    userProfilePicture: String?,
    nav: (String) -> Unit,
    route: String
) {
    RobohashAsyncImageProxy(
        robot = pubkeyHex,
        model = userProfilePicture,
        contentDescription = stringResource(id = R.string.profile_image),
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
        overrideColor = MaterialTheme.colors.onBackground, // we do not want clickable names in purple here.
        route = route,
        nav = nav
    )

    Spacer(modifier = StdHorzSpacer)
    DrawPlayName(userDisplayName)
}
