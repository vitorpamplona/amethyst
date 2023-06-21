package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import com.vitorpamplona.amethyst.ui.actions.ImmutableListOfLists
import com.vitorpamplona.amethyst.ui.actions.toImmutableListOfLists
import com.vitorpamplona.amethyst.ui.components.CreateClickableTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.ResizeImage
import com.vitorpamplona.amethyst.ui.components.RobohashAsyncImageProxy
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.ChatBubbleShapeMe
import com.vitorpamplona.amethyst.ui.theme.ChatBubbleShapeThem
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.RelayIconFilter
import com.vitorpamplona.amethyst.ui.theme.Size13dp
import com.vitorpamplona.amethyst.ui.theme.Size15Modifier
import com.vitorpamplona.amethyst.ui.theme.Size16dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.mediumImportanceLink
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
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
    val noteState by baseNote.live().metadata.observeAsState()
    val noteEvent = remember(noteState) { noteState?.note?.event }

    if (noteEvent == null) {
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

    if (showHiddenNote) {
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
            (innerQuote || !accountViewModel.isLoggedUser(note.author)) && note.event !is PrivateDmEvent
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

    LaunchedEffect(key1 = routeForLastRead) {
        routeForLastRead?.let {
            val createdAt = note.createdAt()
            if (createdAt != null) {
                accountViewModel.account.markAsRead(it, createdAt)
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
            val availableBubbleSize = remember { mutableStateOf(IntSize.Zero) }
            var popupExpanded by remember { mutableStateOf(false) }

            val modif2 = remember {
                if (innerQuote) Modifier else Modifier.fillMaxWidth(0.85f)
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
                    availableBubbleSize.value = it
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
    availableBubbleSize: MutableState<IntSize>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val bubbleSize = remember { mutableStateOf(IntSize.Zero) }

    val bubbleModifier = remember {
        Modifier
            .padding(start = 10.dp, end = 5.dp, bottom = 5.dp)
            .onSizeChanged {
                bubbleSize.value = it
            }
    }

    Column(modifier = bubbleModifier) {
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
                onWantsToReply = onWantsToReply
            )
        }
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
    val replyTo by remember {
        derivedStateOf {
            note.replyTo?.lastOrNull()
        }
    }
    if (!innerQuote && replyTo != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
    bubbleSize: MutableState<IntSize>,
    availableBubbleSize: MutableState<IntSize>,
    content: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .padding(top = 5.dp)
            .then(
                with(LocalDensity.current) {
                    Modifier.widthIn(
                        bubbleSize.value.width.toDp(),
                        availableBubbleSize.value.width.toDp()
                    )
                }
            )
    ) {
        content()
    }
}

@Composable
private fun StatusRow(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    onWantsToReply: (Note) -> Unit
) {
    val grayTint = MaterialTheme.colors.placeholderText

    Column() {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChatTimeAgo(baseNote)
            RelayBadges(baseNote)
            Spacer(modifier = DoubleHorzSpacer)
        }
    }

    Column() {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LikeReaction(baseNote, grayTint, accountViewModel)
            Spacer(modifier = StdHorzSpacer)
            ZapReaction(baseNote, grayTint, accountViewModel)
            Spacer(modifier = StdHorzSpacer)
            ReplyReaction(
                baseNote,
                grayTint,
                accountViewModel,
                showCounter = false,
                iconSize = Size16dp
            ) {
                onWantsToReply(baseNote)
            }
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
        val hasSensitiveContent = remember(note.event) { note.event?.isSensitive() ?: false }

        SensitivityWarning(
            hasSensitiveContent = hasSensitiveContent,
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
    val userState by baseNote.author!!.live().metadata.observeAsState()

    val pubkeyHex = remember { baseNote.author?.pubkeyHex } ?: return
    val route = remember { "User/$pubkeyHex" }
    val userDisplayName = remember(userState) { userState?.user?.toBestDisplayName() }
    val userProfilePicture = remember(userState) { ResizeImage(userState?.user?.profilePicture(), 25.dp) }
    val userTags = remember(userState) { userState?.user?.info?.latestMetadata?.tags?.toImmutableListOfLists() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = alignment,
        modifier = Modifier.padding(top = 5.dp)
    ) {
        RobohashAsyncImageProxy(
            robot = pubkeyHex,
            model = userProfilePicture,
            contentDescription = stringResource(id = R.string.profile_image),
            modifier = remember {
                Modifier
                    .width(25.dp)
                    .height(25.dp)
                    .clip(shape = CircleShape)
                    .clickable(onClick = {
                        nav(route)
                    })
            }
        )

        userDisplayName?.let {
            Spacer(modifier = StdHorzSpacer)

            CreateClickableTextWithEmoji(
                clickablePart = it,
                suffix = "",
                tags = userTags,
                fontWeight = FontWeight.Bold,
                overrideColor = MaterialTheme.colors.onBackground,
                route = route,
                nav = nav
            )

            Spacer(modifier = StdHorzSpacer)
            DrawPlayName(it)
        }
    }
}

@Immutable
data class RelayBadgesState(
    val shouldDisplayExpandButton: Boolean,
    val noteRelays: ImmutableList<String>,
    val noteRelaysSimple: ImmutableList<String>
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelayBadges(baseNote: Note) {
    val noteRelaysState by baseNote.live().relays.observeAsState()

    val state: RelayBadgesState by remember(noteRelaysState) {
        val newShouldDisplayExpandButton = (noteRelaysState?.note?.relays?.size ?: 0) > 3
        val noteRelays = noteRelaysState?.note?.relays?.toImmutableList() ?: persistentListOf()
        val noteRelaysSimple = noteRelaysState?.note?.relays?.take(3)?.toImmutableList() ?: persistentListOf()

        mutableStateOf(RelayBadgesState(newShouldDisplayExpandButton, noteRelays, noteRelaysSimple))
    }

    var expanded by remember { mutableStateOf(false) }

    val relaysToDisplay by remember(noteRelaysState) {
        derivedStateOf {
            if (expanded) state.noteRelays else state.noteRelaysSimple
        }
    }

    FlowRow(Modifier.padding(start = 10.dp)) {
        relaysToDisplay.forEach {
            RenderRelay(it)
        }
    }

    if (state.shouldDisplayExpandButton && !expanded) {
        IconButton(
            modifier = Size15Modifier,
            onClick = { expanded = true }
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                null,
                modifier = Size15Modifier,
                tint = MaterialTheme.colors.placeholderText
            )
        }
    }
}

@Composable
fun RenderRelay(dirtyUrl: String) {
    val uri = LocalUriHandler.current
    val website = remember(dirtyUrl) {
        val cleanUrl = dirtyUrl.trim().removePrefix("wss://").removePrefix("ws://").removeSuffix("/")
        "https://$cleanUrl"
    }
    val iconUrl = remember(dirtyUrl) {
        val cleanUrl = dirtyUrl.trim().removePrefix("wss://").removePrefix("ws://").removeSuffix("/")
        "https://$cleanUrl/favicon.ico"
    }

    val clickableModifier = remember(dirtyUrl) {
        Modifier
            .padding(1.dp)
            .size(15.dp)
            .clickable(onClick = { uri.openUri(website) })
    }

    val backgroundColor = MaterialTheme.colors.background

    val iconModifier = remember(dirtyUrl) {
        Modifier
            .size(13.dp)
            .clip(shape = CircleShape)
            .drawBehind { drawRect(backgroundColor) }
    }

    Box(
        modifier = clickableModifier
    ) {
        RobohashFallbackAsyncImage(
            robot = iconUrl,
            robotSize = Size13dp,
            model = iconUrl,
            contentDescription = stringResource(id = R.string.relay_icon),
            colorFilter = RelayIconFilter,
            modifier = iconModifier
        )
    }
}
