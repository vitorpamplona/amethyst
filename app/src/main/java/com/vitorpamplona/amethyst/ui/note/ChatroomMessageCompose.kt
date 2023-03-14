package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
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
import androidx.navigation.NavController
import com.google.accompanist.flowlayout.FlowRow
import com.vitorpamplona.amethyst.NotificationCache
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.ui.components.ResizeImage
import com.vitorpamplona.amethyst.ui.components.RobohashAsyncImageProxy
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.components.TranslateableRichTextViewer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val ChatBubbleShapeMe = RoundedCornerShape(15.dp, 15.dp, 3.dp, 15.dp)
val ChatBubbleShapeThem = RoundedCornerShape(3.dp, 15.dp, 15.dp, 15.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatroomMessageCompose(
    baseNote: Note,
    routeForLastRead: String?,
    innerQuote: Boolean = false,
    parentBackgroundColor: Color? = null,
    accountViewModel: AccountViewModel,
    navController: NavController,
    onWantsToReply: (Note) -> Unit
) {
    val noteState by baseNote.live().metadata.observeAsState()
    val note = noteState?.note

    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    val noteReportsState by baseNote.live().reports.observeAsState()
    val noteForReports = noteReportsState?.note ?: return

    val accountUser = account.userProfile()

    var popupExpanded by remember { mutableStateOf(false) }
    var showHiddenNote by remember { mutableStateOf(false) }

    val context = LocalContext.current.applicationContext

    if (note?.event == null) {
        BlankNote(Modifier)
    } else if (!account.isAcceptable(noteForReports) && !showHiddenNote) {
        HiddenNote(
            account.getRelevantReports(noteForReports),
            account.userProfile(),
            Modifier,
            innerQuote,
            navController,
            onClick = { showHiddenNote = true }
        )
    } else {
        var backgroundBubbleColor: Color
        var alignment: Arrangement.Horizontal
        var shape: Shape

        if (note.author == accountUser) {
            backgroundBubbleColor = MaterialTheme.colors.primary.copy(alpha = 0.32f)
            alignment = Arrangement.End
            shape = ChatBubbleShapeMe
        } else {
            backgroundBubbleColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
            alignment = Arrangement.Start
            shape = ChatBubbleShapeThem
        }

        if (parentBackgroundColor != null) {
            backgroundBubbleColor = backgroundBubbleColor.compositeOver(parentBackgroundColor)
        } else {
            backgroundBubbleColor = backgroundBubbleColor.compositeOver(MaterialTheme.colors.background)
        }

        var isNew by remember { mutableStateOf<Boolean>(false) }

        LaunchedEffect(key1 = routeForLastRead) {
            routeForLastRead?.let {
                withContext(Dispatchers.IO) {
                    val lastTime = NotificationCache.load(it)

                    val createdAt = note.createdAt()
                    if (createdAt != null) {
                        NotificationCache.markAsRead(it, createdAt)
                        isNew = createdAt > lastTime
                    }
                }
            }
        }

        Column() {
            val modif = if (innerQuote) {
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

            Row(
                modifier = modif,
                horizontalArrangement = alignment
            ) {
                var availableBubbleSize by remember { mutableStateOf(IntSize.Zero) }
                val modif2 = if (innerQuote) Modifier else Modifier.fillMaxWidth(0.85f)

                Row(
                    horizontalArrangement = alignment,
                    modifier = modif2.onSizeChanged {
                        availableBubbleSize = it
                    }
                ) {
                    Surface(
                        color = backgroundBubbleColor,
                        shape = shape,
                        modifier = Modifier
                            .combinedClickable(
                                onClick = { },
                                onLongClick = { popupExpanded = true }
                            )
                    ) {
                        var bubbleSize by remember { mutableStateOf(IntSize.Zero) }

                        Column(
                            modifier = Modifier
                                .padding(start = 10.dp, end = 5.dp, bottom = 5.dp)
                                .onSizeChanged {
                                    bubbleSize = it
                                }
                        ) {
                            val authorState by note.author!!.live().metadata.observeAsState()
                            val author = authorState?.user!!

                            if (innerQuote || author != accountUser && note.event is ChannelMessageEvent) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = alignment,
                                    modifier = Modifier.padding(top = 5.dp)
                                ) {
                                    RobohashAsyncImageProxy(
                                        robot = author.pubkeyHex,
                                        model = ResizeImage(author.profilePicture(), 25.dp),
                                        contentDescription = stringResource(id = R.string.profile_image),
                                        modifier = Modifier
                                            .width(25.dp)
                                            .height(25.dp)
                                            .clip(shape = CircleShape)
                                            .clickable(onClick = {
                                                author.let {
                                                    navController.navigate("User/${it.pubkeyHex}")
                                                }
                                            })
                                    )

                                    Text(
                                        "  ${author.toBestDisplayName()}",
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable(onClick = {
                                            author.let {
                                                navController.navigate("User/${it.pubkeyHex}")
                                            }
                                        })
                                    )
                                }
                            }

                            val replyTo = note.replyTo
                            if (!innerQuote && !replyTo.isNullOrEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    replyTo.toSet().mapIndexed { _, note ->
                                        if (note.event != null) {
                                            ChatroomMessageCompose(
                                                note,
                                                null,
                                                innerQuote = true,
                                                parentBackgroundColor = backgroundBubbleColor,
                                                accountViewModel = accountViewModel,
                                                navController = navController,
                                                onWantsToReply = onWantsToReply
                                            )
                                        }
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val event = note.event
                                if (event is ChannelCreateEvent) {
                                    Text(
                                        text = note.author?.toBestDisplayName()
                                            .toString() + " ${stringResource(R.string.created)} " + (
                                            event.channelInfo().name
                                                ?: ""
                                            ) + " ${stringResource(R.string.with_description_of)} '" + (
                                            event.channelInfo().about
                                                ?: ""
                                            ) + "', ${stringResource(R.string.and_picture)} '" + (
                                            event.channelInfo().picture
                                                ?: ""
                                            ) + "'"
                                    )
                                } else if (event is ChannelMetadataEvent) {
                                    Text(
                                        text = note.author?.toBestDisplayName()
                                            .toString() + " ${stringResource(R.string.changed_chat_name_to)} '" + (
                                            event.channelInfo().name
                                                ?: ""
                                            ) + "$', {stringResource(R.string.description_to)} '" + (
                                            event.channelInfo().about
                                                ?: ""
                                            ) + "', ${stringResource(R.string.and_picture_to)} '" + (
                                            event.channelInfo().picture
                                                ?: ""
                                            ) + "'"
                                    )
                                } else {
                                    val eventContent = accountViewModel.decrypt(note)

                                    val canPreview = note.author == accountUser ||
                                        (note.author?.let { accountUser.isFollowing(it) } ?: true) ||
                                        !noteForReports.hasAnyReports()

                                    if (eventContent != null) {
                                        TranslateableRichTextViewer(
                                            eventContent,
                                            canPreview,
                                            Modifier,
                                            note.event?.tags(),
                                            backgroundBubbleColor,
                                            accountViewModel,
                                            navController
                                        )
                                    } else {
                                        TranslateableRichTextViewer(
                                            stringResource(R.string.could_not_decrypt_the_message),
                                            true,
                                            Modifier,
                                            note.event?.tags(),
                                            backgroundBubbleColor,
                                            accountViewModel,
                                            navController
                                        )
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .padding(top = 5.dp)
                                    .then(
                                        with(LocalDensity.current) {
                                            Modifier.widthIn(
                                                bubbleSize.width.toDp(),
                                                availableBubbleSize.width.toDp()
                                            )
                                        }
                                    )
                            ) {
                                Row() {
                                    Text(
                                        timeAgoShort(note.createdAt(), context),
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                                        fontSize = 12.sp
                                    )

                                    RelayBadges(note)

                                    Spacer(modifier = Modifier.width(10.dp))
                                }

                                Row() {
                                    LikeReaction(baseNote, accountViewModel)
                                    Spacer(modifier = Modifier.width(5.dp))
                                    ZapReaction(baseNote, accountViewModel)
                                    Spacer(modifier = Modifier.width(5.dp))
                                    ReplyReaction(baseNote, accountViewModel, showCounter = false) {
                                        onWantsToReply(baseNote)
                                    }
                                }
                            }
                        }
                    }
                }

                NoteDropDownMenu(note, popupExpanded, { popupExpanded = false }, accountViewModel)
            }
        }
    }
}

@Composable
private fun RelayBadges(baseNote: Note) {
    val noteRelaysState by baseNote.live().relays.observeAsState()
    val noteRelays = noteRelaysState?.note?.relays ?: emptySet()

    var expanded by remember { mutableStateOf(false) }

    val relaysToDisplay = if (expanded) noteRelays else noteRelays.take(3)

    val uri = LocalUriHandler.current

    FlowRow(Modifier.padding(start = 10.dp)) {
        relaysToDisplay.forEach {
            val url = it.removePrefix("wss://")
            Box(
                Modifier
                    .size(15.dp)
                    .padding(1.dp)
            ) {
                RobohashFallbackAsyncImage(
                    robot = "https://$url/favicon.ico",
                    model = "https://$url/favicon.ico",
                    contentDescription = stringResource(id = R.string.relay_icon),
                    colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                    modifier = Modifier
                        .fillMaxSize(1f)
                        .clip(shape = CircleShape)
                        .background(MaterialTheme.colors.background)
                        .clickable(onClick = { uri.openUri("https://$url") })
                )
            }
        }

        if (noteRelays.size > 3 && !expanded) {
            IconButton(
                modifier = Modifier.then(Modifier.size(15.dp)),
                onClick = { expanded = true }
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
            }
        }
    }
}
