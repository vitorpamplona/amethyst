package com.vitorpamplona.amethyst.ui.note

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomStart
import androidx.compose.ui.Alignment.Companion.TopEnd
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.ParticipantListBuilder
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.screen.equalImmutableLists
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ChannelHeader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.CheckIfUrlIsOnline
import com.vitorpamplona.amethyst.ui.screen.loggedIn.EndedFlag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.LiveFlag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.OfflineFlag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ScheduledFlag
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.newItemBackgroundColor
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.CommunityDefinitionEvent
import com.vitorpamplona.quartz.events.LiveActivitiesEvent
import com.vitorpamplona.quartz.events.LiveActivitiesEvent.Companion.STATUS_ENDED
import com.vitorpamplona.quartz.events.LiveActivitiesEvent.Companion.STATUS_LIVE
import com.vitorpamplona.quartz.events.LiveActivitiesEvent.Companion.STATUS_PLANNED
import com.vitorpamplona.quartz.events.Participant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelCardCompose(
    baseNote: Note,
    routeForLastRead: String? = null,
    modifier: Modifier = Modifier,
    parentBackgroundColor: MutableState<Color>? = null,
    forceEventKind: Int?,
    showHidden: Boolean = false,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val hasEvent by baseNote.live().hasEvent.observeAsState(baseNote.event != null)

    Crossfade(targetState = hasEvent) {
        if (it) {
            if (forceEventKind == null || baseNote.event?.kind() == forceEventKind) {
                CheckHiddenChannelCardCompose(
                    baseNote,
                    routeForLastRead,
                    modifier,
                    parentBackgroundColor,
                    showHidden,
                    accountViewModel,
                    nav
                )
            }
        } else {
            LongPressToQuickAction(baseNote = baseNote, accountViewModel = accountViewModel) { showPopup ->
                BlankNote(
                    remember {
                        modifier.combinedClickable(
                            onClick = { },
                            onLongClick = showPopup
                        )
                    },
                    false
                )
            }
        }
    }
}

@Composable
fun CheckHiddenChannelCardCompose(
    note: Note,
    routeForLastRead: String? = null,
    modifier: Modifier = Modifier,
    parentBackgroundColor: MutableState<Color>? = null,
    showHidden: Boolean,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    if (showHidden) {
        val state by remember {
            mutableStateOf(
                AccountViewModel.NoteComposeReportState()
            )
        }

        RenderChannelCardReportState(
            state = state,
            note = note,
            routeForLastRead = routeForLastRead,
            modifier = modifier,
            parentBackgroundColor = parentBackgroundColor,
            accountViewModel = accountViewModel,
            nav = nav
        )
    } else {
        val isHidden by accountViewModel.account.liveHiddenUsers.map {
            note.isHiddenFor(it)
        }.distinctUntilChanged().observeAsState(accountViewModel.isNoteHidden(note))

        Crossfade(targetState = isHidden) {
            if (!it) {
                LoadedChannelCardCompose(
                    note,
                    routeForLastRead,
                    modifier,
                    parentBackgroundColor,
                    accountViewModel,
                    nav
                )
            }
        }
    }
}

@Composable
fun LoadedChannelCardCompose(
    note: Note,
    routeForLastRead: String? = null,
    modifier: Modifier = Modifier,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    var state by remember {
        mutableStateOf(
            AccountViewModel.NoteComposeReportState()
        )
    }

    val scope = rememberCoroutineScope()

    WatchForReports(note, accountViewModel) { newState ->
        if (state != newState) {
            scope.launch(Dispatchers.Main) {
                state = newState
            }
        }
    }

    Crossfade(targetState = state) {
        RenderChannelCardReportState(
            it,
            note,
            routeForLastRead,
            modifier,
            parentBackgroundColor,
            accountViewModel,
            nav
        )
    }
}

@Composable
fun RenderChannelCardReportState(
    state: AccountViewModel.NoteComposeReportState,
    note: Note,
    routeForLastRead: String? = null,
    modifier: Modifier = Modifier,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    var showReportedNote by remember { mutableStateOf(false) }

    Crossfade(targetState = !state.isAcceptable && !showReportedNote) { showHiddenNote ->
        if (showHiddenNote) {
            HiddenNote(
                state.relevantReports,
                state.isHiddenAuthor,
                accountViewModel,
                modifier,
                false,
                nav,
                onClick = { showReportedNote = true }
            )
        } else {
            NormalChannelCard(
                note,
                routeForLastRead,
                modifier,
                parentBackgroundColor,
                accountViewModel,
                nav
            )
        }
    }
}

@Composable
fun NormalChannelCard(
    baseNote: Note,
    routeForLastRead: String? = null,
    modifier: Modifier = Modifier,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    LongPressToQuickAction(baseNote = baseNote, accountViewModel = accountViewModel) { showPopup ->
        CheckNewAndRenderChannelCard(
            baseNote,
            routeForLastRead,
            modifier,
            parentBackgroundColor,
            accountViewModel,
            showPopup,
            nav
        )
    }
}

@Composable
private fun CheckNewAndRenderChannelCard(
    baseNote: Note,
    routeForLastRead: String? = null,
    modifier: Modifier = Modifier,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    showPopup: () -> Unit,
    nav: (String) -> Unit
) {
    val newItemColor = MaterialTheme.colorScheme.newItemBackgroundColor
    val defaultBackgroundColor = MaterialTheme.colorScheme.background
    val backgroundColor = remember {
        mutableStateOf<Color>(
            parentBackgroundColor?.value ?: defaultBackgroundColor
        )
    }

    LaunchedEffect(key1 = routeForLastRead, key2 = parentBackgroundColor?.value) {
        routeForLastRead?.let {
            accountViewModel.loadAndMarkAsRead(routeForLastRead, baseNote.createdAt()) { isNew ->
                val newBackgroundColor = if (isNew) {
                    if (parentBackgroundColor != null) {
                        newItemColor.compositeOver(parentBackgroundColor.value)
                    } else {
                        newItemColor.compositeOver(defaultBackgroundColor)
                    }
                } else {
                    parentBackgroundColor?.value ?: defaultBackgroundColor
                }

                if (newBackgroundColor != backgroundColor.value) {
                    launch(Dispatchers.Main) {
                        backgroundColor.value = newBackgroundColor
                    }
                }
            }
        } ?: run {
            val newBackgroundColor = parentBackgroundColor?.value ?: defaultBackgroundColor
            if (newBackgroundColor != backgroundColor.value) {
                launch(Dispatchers.Main) {
                    backgroundColor.value = newBackgroundColor
                }
            }
        }
    }

    ClickableNote(
        baseNote = baseNote,
        backgroundColor = backgroundColor,
        modifier = modifier,
        accountViewModel = accountViewModel,
        showPopup = showPopup,
        nav = nav
    ) {
        InnerChannelCardWithReactions(
            baseNote = baseNote,
            accountViewModel = accountViewModel,
            nav = nav
        )
    }
}

@Composable
fun InnerChannelCardWithReactions(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    Column(StdPadding) {
        SensitivityWarning(
            note = baseNote,
            accountViewModel = accountViewModel
        ) {
            RenderNoteRow(
                baseNote,
                accountViewModel,
                nav
            )
        }
    }

    Divider(
        thickness = DividerThickness
    )
}

@Composable
private fun RenderNoteRow(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    when (remember { baseNote.event }) {
        is LiveActivitiesEvent -> {
            RenderLiveActivityThumb(baseNote, accountViewModel, nav)
        }
        is CommunityDefinitionEvent -> {
            RenderCommunitiesThumb(baseNote, accountViewModel, nav)
        }
        is ChannelCreateEvent -> {
            RenderChannelThumb(baseNote, accountViewModel, nav)
        }
    }
}

@Immutable
data class LiveActivityCard(
    val name: String,
    val cover: String?,
    val media: String?,
    val subject: String?,
    val content: String?,
    val participants: ImmutableList<Participant>,
    val status: String?,
    val starts: Long?
)

@Composable
fun RenderLiveActivityThumb(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val noteEvent = baseNote.event as? LiveActivitiesEvent ?: return

    val card by baseNote.live().metadata.map {
        val noteEvent = it.note.event as? LiveActivitiesEvent

        LiveActivityCard(
            name = noteEvent?.dTag() ?: "",
            cover = noteEvent?.image()?.ifBlank { null },
            media = noteEvent?.streaming(),
            subject = noteEvent?.title()?.ifBlank { null },
            content = noteEvent?.summary(),
            participants = noteEvent?.participants()?.toImmutableList() ?: persistentListOf(),
            status = noteEvent?.status(),
            starts = noteEvent?.starts()
        )
    }.distinctUntilChanged().observeAsState(
        LiveActivityCard(
            name = noteEvent.dTag(),
            cover = noteEvent.image()?.ifBlank { null },
            media = noteEvent.streaming(),
            subject = noteEvent.title()?.ifBlank { null },
            content = noteEvent.summary(),
            participants = noteEvent.participants().toImmutableList(),
            status = noteEvent.status(),
            starts = noteEvent.starts()
        )
    )

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = TopEnd,
            modifier = Modifier
                .aspectRatio(ratio = 16f / 9f)
                .fillMaxWidth()
        ) {
            card.cover?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(QuoteBorder)
                )
            } ?: run {
                baseNote.author?.let {
                    DisplayAuthorBanner(it)
                }
            }

            Box(Modifier.padding(10.dp)) {
                Crossfade(targetState = card.status, label = "RenderLiveActivityThumb") {
                    when (it) {
                        STATUS_LIVE -> {
                            val url = card.media
                            if (url.isNullOrBlank()) {
                                LiveFlag()
                            } else {
                                CheckIfUrlIsOnline(url, accountViewModel) { isOnline ->
                                    if (isOnline) {
                                        LiveFlag()
                                    } else {
                                        OfflineFlag()
                                    }
                                }
                            }
                        }
                        STATUS_ENDED -> {
                            EndedFlag()
                        }
                        STATUS_PLANNED -> {
                            ScheduledFlag(card.starts)
                        }
                        else -> {
                            EndedFlag()
                        }
                    }
                }
            }

            LoadParticipants(card.participants, baseNote, accountViewModel) { participantUsers ->
                Box(
                    Modifier
                        .padding(10.dp)
                        .align(BottomStart)
                ) {
                    if (participantUsers.isNotEmpty()) {
                        Gallery(participantUsers, accountViewModel)
                    }
                }
            }
        }

        Spacer(modifier = DoubleVertSpacer)

        ChannelHeader(
            channelHex = remember { baseNote.idHex },
            showVideo = false,
            showBottomDiviser = false,
            showFlag = false,
            sendToChannel = true,
            modifier = remember {
                Modifier.padding(start = 0.dp, end = 0.dp, top = 5.dp, bottom = 5.dp)
            },
            accountViewModel = accountViewModel,
            nav = nav
        )
    }
}

@Immutable
data class CommunityCard(
    val name: String,
    val description: String?,
    val cover: String?,
    val moderators: ImmutableList<Participant>
)

@Composable
fun RenderCommunitiesThumb(baseNote: Note, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val noteEvent = baseNote.event as? CommunityDefinitionEvent ?: return

    val card by baseNote.live().metadata.map {
        val noteEvent = it.note.event as? CommunityDefinitionEvent

        CommunityCard(
            name = noteEvent?.dTag() ?: "",
            description = noteEvent?.description(),
            cover = noteEvent?.image()?.ifBlank { null },
            moderators = noteEvent?.moderators()?.toImmutableList() ?: persistentListOf()
        )
    }.distinctUntilChanged().observeAsState(
        CommunityCard(
            name = noteEvent.dTag(),
            description = noteEvent.description(),
            cover = noteEvent.image()?.ifBlank { null },
            moderators = noteEvent.moderators().toImmutableList()
        )
    )

    Row(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.3f)
                .aspectRatio(ratio = 1f)
        ) {
            card.cover?.let {
                Box(contentAlignment = BottomStart) {
                    AsyncImage(
                        model = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(QuoteBorder)
                    )
                }
            } ?: run {
                baseNote.author?.let {
                    DisplayAuthorBanner(it)
                }
            }
        }

        Spacer(modifier = DoubleHorzSpacer)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = card.name,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = StdHorzSpacer)
                LikeReaction(baseNote = baseNote, grayTint = MaterialTheme.colorScheme.onSurface, accountViewModel = accountViewModel, nav)
                Spacer(modifier = StdHorzSpacer)
                ZapReaction(baseNote = baseNote, grayTint = MaterialTheme.colorScheme.onSurface, accountViewModel = accountViewModel, nav = nav)
            }

            card.description?.let {
                Spacer(modifier = StdVertSpacer)
                Row() {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.placeholderText,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp
                    )
                }
            }

            LoadModerators(card.moderators, baseNote, accountViewModel) { participantUsers ->
                if (participantUsers.isNotEmpty()) {
                    Spacer(modifier = StdVertSpacer)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Gallery(participantUsers, accountViewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun LoadModerators(
    moderators: ImmutableList<Participant>,
    baseNote: Note,
    accountViewModel: AccountViewModel,
    content: @Composable (ImmutableList<User>) -> Unit
) {
    var participantUsers by remember {
        mutableStateOf<ImmutableList<User>>(
            persistentListOf()
        )
    }

    LaunchedEffect(key1 = moderators) {
        launch(Dispatchers.IO) {
            val hosts = moderators.mapNotNull { part ->
                if (part.key != baseNote.author?.pubkeyHex) {
                    LocalCache.checkGetOrCreateUser(part.key)
                } else {
                    null
                }
            }

            val followingKeySet = accountViewModel.account.liveDiscoveryFollowLists.value?.users
            val allParticipants = ParticipantListBuilder().followsThatParticipateOn(baseNote, followingKeySet).minus(hosts)

            val newParticipantUsers = if (followingKeySet == null) {
                val allFollows = accountViewModel.account.userProfile().cachedFollowingKeySet()
                val followingParticipants = ParticipantListBuilder().followsThatParticipateOn(baseNote, allFollows).minus(hosts)

                (hosts + followingParticipants + (allParticipants - followingParticipants)).toImmutableList()
            } else {
                (hosts + allParticipants).toImmutableList()
            }

            if (!equalImmutableLists(newParticipantUsers, participantUsers)) {
                participantUsers = newParticipantUsers
            }
        }
    }

    content(participantUsers)
}

@Composable
private fun LoadParticipants(
    participants: ImmutableList<Participant>,
    baseNote: Note,
    accountViewModel: AccountViewModel,
    inner: @Composable (ImmutableList<User>) -> Unit
) {
    var participantUsers by remember {
        mutableStateOf<ImmutableList<User>>(
            persistentListOf()
        )
    }

    LaunchedEffect(key1 = participants) {
        launch(Dispatchers.IO) {
            val hosts = participants.mapNotNull { part ->
                if (part.key != baseNote.author?.pubkeyHex) {
                    LocalCache.checkGetOrCreateUser(part.key)
                } else {
                    null
                }
            }

            val hostsAuthor = hosts + (
                baseNote.author?.let {
                    listOf(it)
                } ?: emptyList<User>()
                )

            val followingKeySet = accountViewModel.account.liveDiscoveryFollowLists.value?.users

            val allParticipants = ParticipantListBuilder().followsThatParticipateOn(baseNote, followingKeySet).minus(hostsAuthor)

            val newParticipantUsers = if (followingKeySet == null) {
                val allFollows = accountViewModel.account.userProfile().cachedFollowingKeySet()
                val followingParticipants = ParticipantListBuilder().followsThatParticipateOn(baseNote, allFollows).minus(hostsAuthor)

                (hosts + followingParticipants + (allParticipants - followingParticipants)).toImmutableList()
            } else {
                (hosts + allParticipants).toImmutableList()
            }

            if (!equalImmutableLists(newParticipantUsers, participantUsers)) {
                participantUsers = newParticipantUsers
            }
        }
    }

    inner(participantUsers)
}

@Composable
fun RenderChannelThumb(baseNote: Note, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val noteEvent = baseNote.event as? ChannelCreateEvent ?: return

    LoadChannel(baseChannelHex = baseNote.idHex, accountViewModel) {
        RenderChannelThumb(baseNote = baseNote, channel = it, accountViewModel, nav)
    }
}

@Composable
fun RenderChannelThumb(baseNote: Note, channel: Channel, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val channelUpdates by channel.live.observeAsState()

    val name = remember(channelUpdates) { channelUpdates?.channel?.toBestDisplayName() ?: "" }
    val description = remember(channelUpdates) { channelUpdates?.channel?.summary() }
    val cover by remember(channelUpdates) {
        derivedStateOf {
            channelUpdates?.channel?.profilePicture()?.ifBlank { null }
        }
    }

    var participantUsers by remember(baseNote) {
        mutableStateOf<ImmutableList<User>>(
            persistentListOf()
        )
    }

    LaunchedEffect(key1 = channelUpdates) {
        launch(Dispatchers.IO) {
            val followingKeySet = accountViewModel.account.liveDiscoveryFollowLists.value?.users
            val allParticipants = ParticipantListBuilder().followsThatParticipateOn(baseNote, followingKeySet).toImmutableList()

            val newParticipantUsers = if (followingKeySet == null) {
                val allFollows = accountViewModel.account.userProfile().cachedFollowingKeySet()
                val followingParticipants = ParticipantListBuilder().followsThatParticipateOn(baseNote, allFollows).toList()

                (followingParticipants + (allParticipants - followingParticipants)).toImmutableList()
            } else {
                allParticipants.toImmutableList()
            }

            if (!equalImmutableLists(newParticipantUsers, participantUsers)) {
                participantUsers = newParticipantUsers
            }
        }
    }

    Row(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.3f)
                .aspectRatio(ratio = 1f)
        ) {
            cover?.let {
                Box(contentAlignment = BottomStart) {
                    AsyncImage(
                        model = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(QuoteBorder)
                    )
                }
            } ?: run {
                baseNote.author?.let {
                    DisplayAuthorBanner(it)
                }
            }
        }

        Spacer(modifier = DoubleHorzSpacer)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = StdHorzSpacer)
                LikeReaction(baseNote = baseNote, grayTint = MaterialTheme.colorScheme.onSurface, accountViewModel = accountViewModel, nav)
                Spacer(modifier = StdHorzSpacer)
                ZapReaction(baseNote = baseNote, grayTint = MaterialTheme.colorScheme.onSurface, accountViewModel = accountViewModel, nav = nav)
            }

            description?.let {
                Spacer(modifier = StdVertSpacer)
                Row() {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.placeholderText,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp
                    )
                }
            }

            if (participantUsers.isNotEmpty()) {
                Spacer(modifier = StdVertSpacer)
                Row() {
                    Gallery(participantUsers, accountViewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Gallery(users: ImmutableList<User>, accountViewModel: AccountViewModel) {
    FlowRow(verticalArrangement = Arrangement.Center) {
        users.take(6).forEach {
            ClickableUserPicture(it, Size35dp, accountViewModel)
        }

        if (users.size > 6) {
            Text(
                text = remember(users) { " + " + (showCount(users.size - 6)).toString() },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun DisplayAuthorBanner(author: User) {
    val picture by author.live().metadata.map {
        it.user.info?.banner?.ifBlank { null } ?: it.user.info?.picture?.ifBlank { null }
    }.observeAsState()

    AsyncImage(
        model = picture,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .clip(QuoteBorder)
    )
}
