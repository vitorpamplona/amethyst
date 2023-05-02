package com.vitorpamplona.amethyst.ui.note

import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.get
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.accompanist.flowlayout.FlowRow
import com.vitorpamplona.amethyst.NotificationCache
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.*
import com.vitorpamplona.amethyst.ui.components.*
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ChannelHeader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ReportNoteDialog
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.Following
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.net.URL
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

@OptIn(ExperimentalTime::class)
@Composable
fun NoteCompose(
    baseNote: Note,
    routeForLastRead: String? = null,
    modifier: Modifier = Modifier,
    isBoostedNote: Boolean = false,
    isQuotedNote: Boolean = false,
    unPackReply: Boolean = true,
    makeItShort: Boolean = false,
    addMarginTop: Boolean = true,
    parentBackgroundColor: Color? = null,
    accountViewModel: AccountViewModel,
    navController: NavController
) {
    val (value, elapsed) = measureTimedValue {
        NoteComposeInner(
            baseNote,
            routeForLastRead,
            modifier,
            isBoostedNote,
            isQuotedNote,
            unPackReply,
            makeItShort,
            addMarginTop,
            parentBackgroundColor,
            accountViewModel,
            navController
        )
    }

    Log.d("Time", "Note Compose in $elapsed for ${baseNote.idHex} ${baseNote.event?.kind()} ${baseNote.event?.content()?.split("\n")?.get(0)?.take(100)}")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteComposeInner(
    baseNote: Note,
    routeForLastRead: String? = null,
    modifier: Modifier = Modifier,
    isBoostedNote: Boolean = false,
    isQuotedNote: Boolean = false,
    unPackReply: Boolean = true,
    makeItShort: Boolean = false,
    addMarginTop: Boolean = true,
    parentBackgroundColor: Color? = null,
    accountViewModel: AccountViewModel,
    navController: NavController
) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return
    val loggedIn = account.userProfile()

    val noteState by baseNote.live().metadata.observeAsState()
    val note = noteState?.note

    val noteReportsState by baseNote.live().reports.observeAsState()
    val noteForReports = noteReportsState?.note ?: return

    var popupExpanded by remember { mutableStateOf(false) }
    var showHiddenNote by remember { mutableStateOf(false) }

    val context = LocalContext.current.applicationContext

    var moreActionsExpanded by remember { mutableStateOf(false) }

    var isAcceptable by remember { mutableStateOf(true) }
    var canPreview by remember { mutableStateOf(true) }

    LaunchedEffect(key1 = noteReportsState) {
        withContext(Dispatchers.IO) {
            canPreview = note?.author === loggedIn ||
                (note?.author?.let { loggedIn.isFollowingCached(it) } ?: true) ||
                !noteForReports.hasAnyReports()

            isAcceptable = account.isAcceptable(noteForReports)
        }
    }

    val noteEvent = note?.event
    val baseChannel = note?.channel()

    if (noteEvent == null) {
        BlankNote(
            modifier.combinedClickable(
                onClick = { },
                onLongClick = { popupExpanded = true }
            ),
            isBoostedNote
        )
    } else if (!isAcceptable && !showHiddenNote) {
        if (!account.isHidden(noteForReports.author!!)) {
            HiddenNote(
                account.getRelevantReports(noteForReports),
                loggedIn,
                modifier,
                isBoostedNote,
                navController,
                onClick = { showHiddenNote = true }
            )
        }
    } else if ((noteEvent is ChannelCreateEvent || noteEvent is ChannelMetadataEvent) && baseChannel != null) {
        ChannelHeader(baseChannel = baseChannel, account = account, navController = navController)
    } else if (noteEvent is BadgeDefinitionEvent) {
        BadgeDisplay(baseNote = note)
    } else if (noteEvent is FileHeaderEvent) {
        FileHeaderDisplay(note)
    } else if (noteEvent is FileStorageHeaderEvent) {
        FileStorageHeaderDisplay(note)
    } else {
        var isNew by remember { mutableStateOf<Boolean>(false) }

        LaunchedEffect(key1 = routeForLastRead) {
            withContext(Dispatchers.IO) {
                routeForLastRead?.let {
                    val lastTime = NotificationCache.load(it)

                    val createdAt = note.createdAt()
                    if (createdAt != null) {
                        NotificationCache.markAsRead(it, createdAt)
                        isNew = createdAt > lastTime
                    }
                }
            }
        }

        val backgroundColor = if (isNew) {
            val newColor = MaterialTheme.colors.primary.copy(0.12f)
            if (parentBackgroundColor != null) {
                newColor.compositeOver(parentBackgroundColor)
            } else {
                newColor.compositeOver(MaterialTheme.colors.background)
            }
        } else {
            parentBackgroundColor ?: MaterialTheme.colors.background
        }

        Column(
            modifier = modifier
                .combinedClickable(
                    onClick = {
                        if (noteEvent is ChannelMessageEvent) {
                            baseChannel?.let {
                                navController.navigate("Channel/${it.idHex}")
                            }
                        } else if (noteEvent is PrivateDmEvent) {
                            val replyAuthorBase =
                                (note.event as? PrivateDmEvent)
                                    ?.recipientPubKey()
                                    ?.let { LocalCache.getOrCreateUser(it) }

                            var userToComposeOn = note.author!!

                            if (replyAuthorBase != null) {
                                if (note.author == accountViewModel.userProfile()) {
                                    userToComposeOn = replyAuthorBase
                                }
                            }

                            navController.navigate("Room/${userToComposeOn.pubkeyHex}")
                        } else {
                            navController.navigate("Note/${note.idHex}")
                        }
                    },
                    onLongClick = { popupExpanded = true }
                )
                .background(backgroundColor)
        ) {
            Row(
                modifier = Modifier
                    .padding(
                        start = if (!isBoostedNote) 12.dp else 0.dp,
                        end = if (!isBoostedNote) 12.dp else 0.dp,
                        top = if (addMarginTop) 10.dp else 0.dp
                    )
            ) {
                if (!isBoostedNote && !isQuotedNote) {
                    Column(Modifier.width(55.dp)) {
                        // Draws the boosted picture outside the boosted card.
                        Box(
                            modifier = Modifier
                                .width(55.dp)
                                .padding(0.dp)
                        ) {
                            NoteAuthorPicture(note, navController, loggedIn, 55.dp)

                            if (noteEvent is RepostEvent) {
                                note.replyTo?.lastOrNull()?.let {
                                    Box(
                                        Modifier
                                            .width(30.dp)
                                            .height(30.dp)
                                            .align(Alignment.BottomEnd)
                                    ) {
                                        NoteAuthorPicture(
                                            it,
                                            navController,
                                            loggedIn,
                                            35.dp,
                                            pictureModifier = Modifier.border(2.dp, MaterialTheme.colors.background, CircleShape)
                                        )
                                    }
                                }
                            }

                            // boosted picture
                            if (noteEvent is ChannelMessageEvent && baseChannel != null) {
                                val channelState by baseChannel.live.observeAsState()
                                val channel = channelState?.channel

                                if (channel != null) {
                                    Box(
                                        Modifier
                                            .width(30.dp)
                                            .height(30.dp)
                                            .align(Alignment.BottomEnd)
                                    ) {
                                        RobohashAsyncImageProxy(
                                            robot = channel.idHex,
                                            model = ResizeImage(channel.profilePicture(), 30.dp),
                                            contentDescription = stringResource(R.string.group_picture),
                                            modifier = Modifier
                                                .width(30.dp)
                                                .height(30.dp)
                                                .clip(shape = CircleShape)
                                                .background(MaterialTheme.colors.background)
                                                .border(
                                                    2.dp,
                                                    MaterialTheme.colors.background,
                                                    CircleShape
                                                )
                                        )
                                    }
                                }
                            }
                        }

                        if (noteEvent is RepostEvent) {
                            baseNote.replyTo?.lastOrNull()?.let {
                                RelayBadges(it)
                            }
                        } else {
                            RelayBadges(baseNote)
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(start = if (!isBoostedNote && !isQuotedNote) 10.dp else 0.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isQuotedNote) {
                            NoteAuthorPicture(note, navController, loggedIn, 25.dp)
                            Spacer(Modifier.padding(horizontal = 5.dp))
                            NoteUsernameDisplay(note, Modifier.weight(1f))
                        } else {
                            NoteUsernameDisplay(note, Modifier.weight(1f))
                        }

                        if (noteEvent is RepostEvent) {
                            Text(
                                "  ${stringResource(id = R.string.boosted)}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )
                        } else {
                            DisplayFollowingHashtagsInPost(noteEvent, account, navController)
                        }

                        Text(
                            timeAgo(note.createdAt(), context = context),
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                            maxLines = 1
                        )

                        IconButton(
                            modifier = Modifier.size(24.dp),
                            onClick = { moreActionsExpanded = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )

                            NoteDropDownMenu(baseNote, moreActionsExpanded, { moreActionsExpanded = false }, accountViewModel)
                        }
                    }

                    if (note.author != null && !makeItShort && !isQuotedNote) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ObserveDisplayNip05Status(note.author!!, Modifier.weight(1f))

                            val baseReward = noteEvent.getReward()
                            if (baseReward != null) {
                                DisplayReward(baseReward, baseNote, account, navController)
                            }

                            val pow = noteEvent.getPoWRank()
                            if (pow > 20) {
                                DisplayPoW(pow)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    if (!makeItShort && noteEvent is TextNoteEvent && (note.replyTo != null || noteEvent.mentions().isNotEmpty())) {
                        val replyingDirectlyTo = note.replyTo?.lastOrNull()
                        if (replyingDirectlyTo != null && unPackReply) {
                            NoteCompose(
                                baseNote = replyingDirectlyTo,
                                isQuotedNote = true,
                                modifier = Modifier
                                    .padding(top = 5.dp)
                                    .fillMaxWidth()
                                    .clip(shape = RoundedCornerShape(15.dp))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                                        RoundedCornerShape(15.dp)
                                    ),
                                unPackReply = false,
                                makeItShort = true,
                                parentBackgroundColor = MaterialTheme.colors.onSurface.copy(alpha = 0.05f).compositeOver(backgroundColor),
                                accountViewModel = accountViewModel,
                                navController = navController
                            )
                        } else {
                            ReplyInformation(note.replyTo, noteEvent.mentions(), account, navController)
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                    } else if (!makeItShort && noteEvent is ChannelMessageEvent && (note.replyTo != null || noteEvent.mentions().isNotEmpty())) {
                        val sortedMentions = noteEvent.mentions()
                            .mapNotNull { LocalCache.checkGetOrCreateUser(it) }
                            .toSet()
                            .sortedBy { loggedIn.isFollowingCached(it) }

                        note.channel()?.let {
                            ReplyInformationChannel(note.replyTo, sortedMentions, it, navController)
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                    }

                    if (noteEvent is ReactionEvent || noteEvent is RepostEvent) {
                        note.replyTo?.lastOrNull()?.let {
                            NoteCompose(
                                it,
                                modifier = Modifier,
                                isBoostedNote = true,
                                unPackReply = false,
                                parentBackgroundColor = backgroundColor,
                                accountViewModel = accountViewModel,
                                navController = navController
                            )
                        }

                        // Reposts have trash in their contents.
                        if (noteEvent is ReactionEvent) {
                            val refactorReactionText =
                                if (noteEvent.content == "+") "❤" else noteEvent.content

                            Text(
                                text = refactorReactionText
                            )
                        }
                    } else if (noteEvent is ReportEvent) {
                        val reportType = (noteEvent.reportedPost() + noteEvent.reportedAuthor()).map {
                            when (it.reportType) {
                                ReportEvent.ReportType.EXPLICIT -> stringResource(R.string.explicit_content)
                                ReportEvent.ReportType.NUDITY -> stringResource(R.string.nudity)
                                ReportEvent.ReportType.PROFANITY -> stringResource(R.string.profanity_hateful_speech)
                                ReportEvent.ReportType.SPAM -> stringResource(R.string.spam)
                                ReportEvent.ReportType.IMPERSONATION -> stringResource(R.string.impersonation)
                                ReportEvent.ReportType.ILLEGAL -> stringResource(R.string.illegal_behavior)
                            }
                        }.toSet().joinToString(", ")

                        Text(
                            text = reportType
                        )

                        Divider(
                            modifier = Modifier.padding(top = 40.dp),
                            thickness = 0.25.dp
                        )
                    } else if (noteEvent is LongTextNoteEvent) {
                        LongFormHeader(noteEvent, note, loggedIn)

                        ReactionsRow(note, accountViewModel, navController)

                        Divider(
                            modifier = Modifier.padding(top = 10.dp),
                            thickness = 0.25.dp
                        )
                    } else if (noteEvent is BadgeAwardEvent && !note.replyTo.isNullOrEmpty()) {
                        Text(text = stringResource(R.string.award_granted_to))

                        FlowRow(modifier = Modifier.padding(top = 5.dp)) {
                            noteEvent.awardees()
                                .map { LocalCache.getOrCreateUser(it) }
                                .forEach {
                                    UserPicture(
                                        user = it,
                                        navController = navController,
                                        userAccount = loggedIn,
                                        size = 35.dp
                                    )
                                }
                        }

                        note.replyTo?.firstOrNull()?.let {
                            NoteCompose(
                                it,
                                modifier = Modifier,
                                isBoostedNote = false,
                                isQuotedNote = true,
                                unPackReply = false,
                                parentBackgroundColor = backgroundColor,
                                accountViewModel = accountViewModel,
                                navController = navController
                            )
                        }

                        ReactionsRow(note, accountViewModel, navController)

                        Divider(
                            modifier = Modifier.padding(top = 10.dp),
                            thickness = 0.25.dp
                        )
                    } else if (noteEvent is PrivateDmEvent &&
                        noteEvent.recipientPubKey() != loggedIn.pubkeyHex &&
                        note.author !== loggedIn
                    ) {
                        val recipient = noteEvent.recipientPubKey()?.let { LocalCache.checkGetOrCreateUser(it) }

                        TranslatableRichTextViewer(
                            stringResource(
                                id = R.string.private_conversation_notification,
                                "@${note.author?.pubkeyNpub()}",
                                "@${recipient?.pubkeyNpub()}"
                            ),
                            canPreview = !makeItShort,
                            Modifier.fillMaxWidth(),
                            noteEvent.tags(),
                            backgroundColor,
                            accountViewModel,
                            navController
                        )

                        if (!makeItShort) {
                            ReactionsRow(note, accountViewModel, navController)
                        }

                        Divider(
                            modifier = Modifier.padding(top = 10.dp),
                            thickness = 0.25.dp
                        )
                    } else if (noteEvent is HighlightEvent) {
                        DisplayHighlight(
                            noteEvent.quote(),
                            noteEvent.author(),
                            noteEvent.inUrl(),
                            makeItShort,
                            canPreview,
                            backgroundColor,
                            accountViewModel,
                            navController
                        )

                        if (!makeItShort) {
                            ReactionsRow(note, accountViewModel, navController)
                        }

                        Divider(
                            modifier = Modifier.padding(top = 10.dp),
                            thickness = 0.25.dp
                        )
                    } else {
                        val eventContent = accountViewModel.decrypt(note)

                        if (eventContent != null) {
                            if (makeItShort && note.author == loggedIn) {
                                Text(
                                    text = eventContent,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                TranslatableRichTextViewer(
                                    eventContent,
                                    canPreview = canPreview && !makeItShort,
                                    Modifier.fillMaxWidth(),
                                    noteEvent.tags(),
                                    backgroundColor,
                                    accountViewModel,
                                    navController
                                )

                                DisplayUncitedHashtags(noteEvent.hashtags(), eventContent, navController)
                            }

                            if (noteEvent is PollNoteEvent) {
                                PollNote(
                                    note,
                                    canPreview = canPreview && !makeItShort,
                                    backgroundColor,
                                    accountViewModel,
                                    navController
                                )
                            }
                        }

                        if (!makeItShort) {
                            ReactionsRow(note, accountViewModel, navController)
                        }

                        Divider(
                            modifier = Modifier.padding(top = 10.dp),
                            thickness = 0.25.dp
                        )
                    }

                    NoteQuickActionMenu(note, popupExpanded, { popupExpanded = false }, accountViewModel)
                }
            }
        }
    }
}

@Composable
fun DisplayHighlight(
    highlight: String,
    authorHex: String?,
    url: String?,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: Color,
    accountViewModel: AccountViewModel,
    navController: NavController
) {
    val quote = highlight.split("\n").map { "> *${it.removeSuffix(" ")}*" }.joinToString("\n")

    if (quote != null) {
        TranslatableRichTextViewer(
            quote,
            canPreview = canPreview && !makeItShort,
            Modifier.fillMaxWidth(),
            emptyList(),
            backgroundColor,
            accountViewModel,
            navController
        )
    }

    FlowRow() {
        authorHex?.let { authorHex ->
            val userBase = LocalCache.checkGetOrCreateUser(authorHex)

            if (userBase != null) {
                val userState by userBase.live().metadata.observeAsState()
                val user = userState?.user

                if (user != null) {
                    CreateClickableText(
                        user.toBestDisplayName(),
                        "",
                        "User/${user.pubkeyHex}",
                        navController
                    )
                }
            }
        }

        url?.let { url ->
            val validatedUrl = try {
                URL(url)
            } catch (e: Exception) {
                Log.w("Note Compose", "Invalid URI: $url")
                null
            }

            validatedUrl?.host?.let { host ->
                Text("on ")
                ClickableUrl(urlText = host, url = url)
            }
        }
    }
}

@Composable
fun DisplayFollowingHashtagsInPost(
    noteEvent: EventInterface,
    account: Account,
    navController: NavController
) {
    var firstTag by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(key1 = noteEvent) {
        withContext(Dispatchers.IO) {
            firstTag = noteEvent.firstIsTaggedHashes(account.followingTagSet())
        }
    }

    Column() {
        Row(verticalAlignment = Alignment.CenterVertically) {
            firstTag?.let {
                ClickableText(
                    text = AnnotatedString(" #$firstTag"),
                    onClick = { navController.navigate("Hashtag/$firstTag") },
                    style = LocalTextStyle.current.copy(
                        color = MaterialTheme.colors.primary.copy(
                            alpha = 0.52f
                        )
                    )
                )
            }
        }
    }
}

@Composable
fun DisplayUncitedHashtags(
    hashtags: List<String>,
    eventContent: String,
    navController: NavController
) {
    if (hashtags.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.padding(top = 5.dp)
        ) {
            hashtags.forEach { hashtag ->
                if (!eventContent.contains(hashtag, true)) {
                    ClickableText(
                        text = AnnotatedString("#$hashtag "),
                        onClick = { navController.navigate("Hashtag/$hashtag") },
                        style = LocalTextStyle.current.copy(
                            color = MaterialTheme.colors.primary.copy(
                                alpha = 0.52f
                            )
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun DisplayPoW(
    pow: Int
) {
    Text(
        "PoW-$pow",
        color = MaterialTheme.colors.primary.copy(
            alpha = 0.52f
        ),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun DisplayReward(
    baseReward: BigDecimal,
    baseNote: Note,
    account: Account,
    navController: NavController
) {
    var popupExpanded by remember { mutableStateOf(false) }

    Column() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { popupExpanded = true }
        ) {
            ClickableText(
                text = AnnotatedString("#bounty"),
                onClick = { navController.navigate("Hashtag/bounty") },
                style = LocalTextStyle.current.copy(
                    color = MaterialTheme.colors.primary.copy(
                        alpha = 0.52f
                    )
                )
            )

            val repliesState by baseNote.live().replies.observeAsState()
            val replyNote = repliesState?.note

            if (replyNote?.hasPledgeBy(account.userProfile()) == true) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = stringResource(R.string.zaps),
                    modifier = Modifier.size(20.dp),
                    tint = BitcoinOrange
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = stringResource(R.string.zaps),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
            }

            var rewardAmount by remember {
                mutableStateOf<BigDecimal?>(
                    baseReward
                )
            }

            LaunchedEffect(key1 = repliesState) {
                withContext(Dispatchers.IO) {
                    replyNote?.pledgedAmountByOthers()?.let {
                        rewardAmount = baseReward.add(it)
                    }
                }
            }

            Text(
                showAmount(rewardAmount),
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
            )
        }

        if (popupExpanded) {
            AddBountyAmountDialog(baseNote, account) {
                popupExpanded = false
            }
        }
    }
}

@Composable
fun BadgeDisplay(baseNote: Note) {
    val background = MaterialTheme.colors.background
    val badgeData = baseNote.event as? BadgeDefinitionEvent ?: return
    var backgroundFromImage by remember { mutableStateOf(background) }

    Row(
        modifier = Modifier
            .padding(10.dp)
            .clip(shape = CutCornerShape(20, 20, 20, 20))
            .border(
                5.dp,
                MaterialTheme.colors.primary.copy(alpha = 0.32f),
                CutCornerShape(20)
            )
            .background(backgroundFromImage)
    ) {
        Column {
            badgeData.image()?.let {
                AsyncImage(
                    model = it,
                    contentDescription = stringResource(
                        R.string.badge_award_image_for,
                        it
                    ),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                    onSuccess = {
                        val backgroundColor = it.result.drawable.toBitmap(200, 200).copy(Bitmap.Config.ARGB_8888, false).get(0, 199)
                        backgroundFromImage = Color(backgroundColor)
                    }
                )
            }

            badgeData.name()?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp),
                    color = if (backgroundFromImage.luminance() > 0.5) lightColors().onBackground else darkColors().onBackground
                )
            }

            badgeData.description()?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.caption,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    color = Color.Gray,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun FileHeaderDisplay(note: Note) {
    val event = (note.event as? FileHeaderEvent) ?: return
    val fullUrl = event.url() ?: return

    var content by remember { mutableStateOf<ZoomableContent?>(null) }

    LaunchedEffect(key1 = event.id) {
        withContext(Dispatchers.IO) {
            val blurHash = event.blurhash()
            val hash = event.hash()
            val dimensions = event.dimensions()
            val description = event.content
            val removedParamsFromUrl = fullUrl.split("?")[0].lowercase()
            val isImage = imageExtensions.any { removedParamsFromUrl.endsWith(it) }
            val uri = "nostr:" + note.toNEvent()
            content = if (isImage) {
                ZoomableUrlImage(fullUrl, description, hash, blurHash, dimensions, uri)
            } else {
                ZoomableUrlVideo(fullUrl, description, hash, uri)
            }
        }
    }

    content?.let {
        ZoomableContentView(content = it, listOf(it))
    }
}

@Composable
fun FileStorageHeaderDisplay(baseNote: Note) {
    val appContext = LocalContext.current.applicationContext
    val eventHeader = (baseNote.event as? FileStorageHeaderEvent) ?: return

    val fileNote = eventHeader.dataEventId()?.let { LocalCache.checkGetOrCreateNote(it) } ?: return

    val noteState by fileNote.live().metadata.observeAsState()
    val note = noteState?.note

    val eventBytes = (note?.event as? FileStorageEvent)

    var content by remember { mutableStateOf<ZoomableContent?>(null) }

    LaunchedEffect(key1 = eventHeader.id, key2 = noteState) {
        withContext(Dispatchers.IO) {
            val uri = "nostr:" + baseNote.toNEvent()
            val localDir = File(File(appContext.externalCacheDir, "NIP95"), fileNote.idHex)
            val bytes = eventBytes?.decode()
            val blurHash = eventHeader.blurhash()
            val dimensions = eventHeader.dimensions()
            val description = eventHeader.content
            val mimeType = eventHeader.mimeType()

            content = if (mimeType?.startsWith("image") == true) {
                ZoomableLocalImage(
                    localFile = localDir,
                    mimeType = mimeType,
                    description = description,
                    blurhash = blurHash,
                    dim = dimensions,
                    isVerified = true,
                    uri = uri
                )
            } else {
                if (bytes != null) {
                    ZoomableLocalVideo(
                        localFile = localDir,
                        mimeType = mimeType,
                        description = description,
                        dim = dimensions,
                        isVerified = true,
                        uri = uri
                    )
                } else {
                    null
                }
            }
        }
    }

    content?.let {
        ZoomableContentView(content = it, listOf(it))
    }
}

@Composable
private fun LongFormHeader(noteEvent: LongTextNoteEvent, note: Note, loggedIn: User) {
    Row(
        modifier = Modifier
            .clip(shape = RoundedCornerShape(15.dp))
            .border(
                1.dp,
                MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                RoundedCornerShape(15.dp)
            )
    ) {
        Column {
            noteEvent.image()?.let {
                AsyncImage(
                    model = it,
                    contentDescription = stringResource(
                        R.string.preview_card_image_for,
                        it
                    ),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )
            } ?: Box() {
                note.author?.info?.banner?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = stringResource(
                            R.string.preview_card_image_for,
                            it
                        ),
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth()
                    )
                } ?: Image(
                    painter = painterResource(R.drawable.profile_banner),
                    contentDescription = stringResource(R.string.profile_banner),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )

                Box(
                    Modifier
                        .width(75.dp)
                        .height(75.dp)
                        .padding(10.dp)
                        .align(Alignment.BottomStart)
                ) {
                    NoteAuthorPicture(baseNote = note, baseUserAccount = loggedIn, size = 55.dp)
                }
            }

            noteEvent.title()?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, top = 10.dp)
                )
            }

            noteEvent.summary()?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    color = Color.Gray,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
                ?: Text(
                    text = noteEvent.content.take(200),
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    color = Color.Gray,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
        }
    }
}

@Composable
private fun RelayBadges(baseNote: Note) {
    val noteRelaysState by baseNote.live().relays.observeAsState()
    val noteRelays = noteRelaysState?.note ?: return

    var expanded by remember { mutableStateOf(false) }
    var showShowMore by remember { mutableStateOf(false) }
    var lazyRelayList by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(key1 = noteRelaysState, key2 = expanded) {
        withContext(Dispatchers.IO) {
            val relayList = noteRelays.relays.map {
                it.removePrefix("wss://").removePrefix("ws://")
            }

            val relaysToDisplay = if (expanded) relayList else relayList.take(3)
            val shouldListChange = lazyRelayList.size < 3 || lazyRelayList.size != relayList.size

            if (shouldListChange) {
                lazyRelayList = relaysToDisplay
            }

            val nextShowMore = relayList.size > 3 && !expanded
            if (nextShowMore != showShowMore) {
                // only triggers recomposition when actually different
                showShowMore = nextShowMore
            }
        }
    }

    Spacer(Modifier.height(10.dp))

    VerticalRelayPanelWithFlow(lazyRelayList)

    if (showShowMore) {
        ShowMoreRelaysButton {
            expanded = true
        }
    }
}

@Composable
@Stable
private fun VerticalRelayPanelWithFlow(
    relays: List<String>
) {
    // FlowRow Seems to be a lot faster than LazyVerticalGrid
    FlowRow() {
        relays.forEach { url ->
            RelayIconCompose(url)
        }
    }
}

@Composable
@Stable
private fun RelayIconCompose(url: String) {
    val uri = LocalUriHandler.current

    Box(
        Modifier
            .padding(1.dp)
            .size(15.dp)
    ) {
        RobohashFallbackAsyncImage(
            robot = "https://$url/favicon.ico",
            robotSize = 15.dp,
            model = "https://$url/favicon.ico",
            contentDescription = stringResource(R.string.relay_icon),
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
            modifier = Modifier
                .width(13.dp)
                .height(13.dp)
                .clip(shape = CircleShape)
                .background(MaterialTheme.colors.background)
                .clickable(onClick = { uri.openUri("https://$url") })
        )
    }
}

@Composable
private fun ShowMoreRelaysButton(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(25.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Top
    ) {
        IconButton(
            modifier = Modifier.then(Modifier.size(24.dp)),
            onClick = onClick
        ) {
            Icon(
                imageVector = Icons.Default.ExpandMore,
                null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
            )
        }
    }
}

@Composable
fun NoteAuthorPicture(
    note: Note,
    navController: NavController,
    userAccount: User,
    size: Dp,
    pictureModifier: Modifier = Modifier
) {
    NoteAuthorPicture(note, userAccount, size, pictureModifier) {
        navController.navigate("User/${it.pubkeyHex}")
    }
}

@Composable
fun NoteAuthorPicture(
    baseNote: Note,
    baseUserAccount: User,
    size: Dp,
    modifier: Modifier = Modifier,
    onClick: ((User) -> Unit)? = null
) {
    val noteState by baseNote.live().metadata.observeAsState()
    val note = noteState?.note ?: return

    val author = note.author

    Box(
        Modifier
            .width(size)
            .height(size)
    ) {
        if (author == null) {
            RobohashAsyncImage(
                robot = "authornotfound",
                robotSize = size,
                contentDescription = stringResource(R.string.unknown_author),
                modifier = modifier
                    .width(size)
                    .height(size)
                    .clip(shape = CircleShape)
                    .background(MaterialTheme.colors.background)
            )
        } else {
            UserPicture(author, baseUserAccount, size, modifier, onClick)
        }
    }
}

@Composable
fun UserPicture(
    user: User,
    navController: NavController,
    userAccount: User,
    size: Dp,
    pictureModifier: Modifier = Modifier
) {
    UserPicture(user, userAccount, size, pictureModifier) {
        navController.navigate("User/${it.pubkeyHex}")
    }
}

@Composable
fun UserPicture(
    baseUser: User,
    baseUserAccount: User,
    size: Dp,
    modifier: Modifier = Modifier,
    onClick: ((User) -> Unit)? = null,
    onLongClick: ((User) -> Unit)? = null
) {
    val userState by baseUser.live().metadata.observeAsState()
    val user = userState?.user ?: return

    val accountState by baseUserAccount.live().follows.observeAsState()
    val accountUser = accountState?.user ?: return

    val showFollowingMark = accountUser.isFollowingCached(user) || user == accountUser

    UserPicture(
        userHex = user.pubkeyHex,
        userPicture = user.profilePicture(),
        showFollowingMark = showFollowingMark,
        size = size,
        modifier = modifier,
        onClick = onClick?.let { { it(user) } },
        onLongClick = onLongClick?.let { { it(user) } }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserPicture(
    userHex: String,
    userPicture: String?,
    showFollowingMark: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    Box(
        Modifier
            .width(size)
            .height(size)
    ) {
        RobohashAsyncImageProxy(
            robot = userHex,
            model = ResizeImage(userPicture, size),
            contentDescription = stringResource(id = R.string.profile_image),
            modifier = modifier
                .width(size)
                .height(size)
                .clip(shape = CircleShape)
                .background(MaterialTheme.colors.background)
                .run {
                    if (onClick != null && onLongClick != null) {
                        this.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    } else if (onClick != null) {
                        this.clickable(onClick = onClick)
                    } else {
                        this
                    }
                }

        )

        if (showFollowingMark) {
            Box(
                Modifier
                    .width(size.div(3.5f))
                    .height(size.div(3.5f))
                    .align(Alignment.TopEnd),
                contentAlignment = Alignment.Center
            ) {
                // Background for the transparent checkmark
                Box(
                    Modifier
                        .clip(CircleShape)
                        .fillMaxSize(0.6f)
                        .align(Alignment.Center)
                        .background(MaterialTheme.colors.background)
                )

                Icon(
                    painter = painterResource(R.drawable.ic_verified),
                    stringResource(id = R.string.following),
                    modifier = Modifier.fillMaxSize(),
                    tint = Following
                )
            }
        }
    }
}

data class DropDownParams(
    val isFollowingAuthor: Boolean,
    val isPrivateBookmarkNote: Boolean,
    val isPublicBookmarkNote: Boolean,
    val isLoggedUser: Boolean
)

@Composable
fun NoteDropDownMenu(note: Note, popupExpanded: Boolean, onDismiss: () -> Unit, accountViewModel: AccountViewModel) {
    val clipboardManager = LocalClipboardManager.current
    val appContext = LocalContext.current.applicationContext
    val actContext = LocalContext.current
    var reportDialogShowing by remember { mutableStateOf(false) }

    var state by remember {
        mutableStateOf<DropDownParams>(
            DropDownParams(false, false, false, false)
        )
    }

    LaunchedEffect(key1 = note) {
        withContext(Dispatchers.IO) {
            state = DropDownParams(
                accountViewModel.isFollowing(note.author),
                accountViewModel.isInPrivateBookmarks(note),
                accountViewModel.isInPublicBookmarks(note),
                accountViewModel.isLoggedUser(note.author)
            )
        }
    }

    DropdownMenu(
        expanded = popupExpanded,
        onDismissRequest = onDismiss
    ) {
        if (!state.isFollowingAuthor) {
            DropdownMenuItem(onClick = {
                accountViewModel.follow(
                    note.author ?: return@DropdownMenuItem
                ); onDismiss()
            }) {
                Text(stringResource(R.string.follow))
            }
            Divider()
        }
        DropdownMenuItem(onClick = { clipboardManager.setText(AnnotatedString(accountViewModel.decrypt(note) ?: "")); onDismiss() }) {
            Text(stringResource(R.string.copy_text))
        }
        DropdownMenuItem(onClick = { clipboardManager.setText(AnnotatedString("nostr:${note.author?.pubkeyNpub()}")); onDismiss() }) {
            Text(stringResource(R.string.copy_user_pubkey))
        }
        DropdownMenuItem(onClick = { clipboardManager.setText(AnnotatedString("nostr:" + note.toNEvent())); onDismiss() }) {
            Text(stringResource(R.string.copy_note_id))
        }
        DropdownMenuItem(onClick = {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    externalLinkForNote(note)
                )
                putExtra(Intent.EXTRA_TITLE, actContext.getString(R.string.quick_action_share_browser_link))
            }

            val shareIntent = Intent.createChooser(sendIntent, appContext.getString(R.string.quick_action_share))
            ContextCompat.startActivity(actContext, shareIntent, null)
            onDismiss()
        }) {
            Text(stringResource(R.string.quick_action_share))
        }
        Divider()
        if (state.isPrivateBookmarkNote) {
            DropdownMenuItem(onClick = { accountViewModel.removePrivateBookmark(note); onDismiss() }) {
                Text(stringResource(R.string.remove_from_private_bookmarks))
            }
        } else {
            DropdownMenuItem(onClick = { accountViewModel.addPrivateBookmark(note); onDismiss() }) {
                Text(stringResource(R.string.add_to_private_bookmarks))
            }
        }
        if (state.isPublicBookmarkNote) {
            DropdownMenuItem(onClick = { accountViewModel.removePublicBookmark(note); onDismiss() }) {
                Text(stringResource(R.string.remove_from_public_bookmarks))
            }
        } else {
            DropdownMenuItem(onClick = { accountViewModel.addPublicBookmark(note); onDismiss() }) {
                Text(stringResource(R.string.add_to_public_bookmarks))
            }
        }
        Divider()
        DropdownMenuItem(onClick = { accountViewModel.broadcast(note); onDismiss() }) {
            Text(stringResource(R.string.broadcast))
        }
        Divider()
        if (state.isLoggedUser) {
            DropdownMenuItem(onClick = { accountViewModel.delete(note); onDismiss() }) {
                Text(stringResource(R.string.request_deletion))
            }
        } else {
            DropdownMenuItem(onClick = { reportDialogShowing = true }) {
                Text("Block / Report")
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
