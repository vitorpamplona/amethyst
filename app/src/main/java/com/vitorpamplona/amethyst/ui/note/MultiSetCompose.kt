package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.NotificationCache
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.UserMetadata
import com.vitorpamplona.amethyst.service.model.LnZapEvent
import com.vitorpamplona.amethyst.service.model.LnZapRequestEvent
import com.vitorpamplona.amethyst.ui.actions.ImmutableListOfLists
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.screen.CombinedZap
import com.vitorpamplona.amethyst.ui.screen.MultiSetCard
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.showAmountAxis
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.newItemBackgroundColor
import com.vitorpamplona.amethyst.ui.theme.overPictureBackground
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MultiSetCompose(multiSetCard: MultiSetCard, routeForLastRead: String, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val baseNote = remember { multiSetCard.note }

    var popupExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val defaultBackgroundColor = MaterialTheme.colors.background
    val backgroundColor = remember { mutableStateOf<Color>(defaultBackgroundColor) }
    val newItemColor = MaterialTheme.colors.newItemBackgroundColor

    LaunchedEffect(key1 = multiSetCard) {
        launch(Dispatchers.IO) {
            val isNew = multiSetCard.maxCreatedAt > NotificationCache.load(routeForLastRead)

            NotificationCache.markAsRead(routeForLastRead, multiSetCard.maxCreatedAt)

            val newBackgroundColor = if (isNew) {
                newItemColor.compositeOver(defaultBackgroundColor)
            } else {
                defaultBackgroundColor
            }

            if (backgroundColor.value != newBackgroundColor) {
                backgroundColor.value = newBackgroundColor
            }
        }
    }

    val columnModifier = remember(backgroundColor.value) {
        Modifier
            .drawBehind {
                drawRect(backgroundColor.value)
            }
            .padding(
                start = 12.dp,
                end = 12.dp,
                top = 10.dp
            )
            .combinedClickable(
                onClick = {
                    scope.launch {
                        routeFor(baseNote, accountViewModel.userProfile())?.let { nav(it) }
                    }
                },
                onLongClick = { popupExpanded = true }
            )
            .fillMaxWidth()
    }

    Column(modifier = columnModifier) {
        Galeries(multiSetCard, backgroundColor, nav, accountViewModel)

        Row(remember { Modifier.fillMaxWidth() }) {
            Spacer(modifier = remember { Modifier.width(65.dp) })

            NoteCompose(
                baseNote = baseNote,
                routeForLastRead = null,
                modifier = remember { Modifier.padding(top = 5.dp) },
                isBoostedNote = true,
                parentBackgroundColor = backgroundColor,
                accountViewModel = accountViewModel,
                nav = nav
            )

            NoteDropDownMenu(baseNote, popupExpanded, { popupExpanded = false }, accountViewModel)
        }
    }
}

@Composable
private fun Galeries(
    multiSetCard: MultiSetCard,
    backgroundColor: MutableState<Color>,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel
) {
    val zapEvents by remember { derivedStateOf { multiSetCard.zapEvents } }
    val boostEvents by remember { derivedStateOf { multiSetCard.boostEvents } }
    val likeEvents by remember { derivedStateOf { multiSetCard.likeEvents } }

    val hasZapEvents by remember { derivedStateOf { multiSetCard.zapEvents.isNotEmpty() } }
    val hasBoostEvents by remember { derivedStateOf { multiSetCard.boostEvents.isNotEmpty() } }
    val hasLikeEvents by remember { derivedStateOf { multiSetCard.likeEvents.isNotEmpty() } }

    if (hasZapEvents) {
        RenderZapGallery(zapEvents, backgroundColor, nav, accountViewModel)
    }

    if (hasBoostEvents) {
        RenderBoostGallery(boostEvents, backgroundColor, nav, accountViewModel)
    }

    if (hasLikeEvents) {
        RenderLikeGallery(likeEvents, backgroundColor, nav, accountViewModel)
    }
}

@Composable
fun RenderLikeGallery(
    likeEvents: ImmutableList<Note>,
    backgroundColor: MutableState<Color>,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel
) {
    Row(remember { Modifier.fillMaxWidth() }) {
        Box(
            modifier = remember {
                Modifier
                    .width(55.dp)
                    .padding(end = 5.dp)
            }
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_liked),
                null,
                modifier = remember {
                    Modifier
                        .size(16.dp)
                        .align(Alignment.TopEnd)
                },
                tint = Color.Unspecified
            )
        }

        AuthorGallery(likeEvents, backgroundColor, nav, accountViewModel)
    }
}

@Composable
fun RenderZapGallery(
    zapEvents: ImmutableList<CombinedZap>,
    backgroundColor: MutableState<Color>,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel
) {
    Row(remember { Modifier.fillMaxWidth() }) {
        Box(
            modifier = remember {
                Modifier
                    .width(55.dp)
                    .padding(0.dp)
            }
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = "Zaps",
                tint = BitcoinOrange,
                modifier = remember {
                    Modifier
                        .size(25.dp)
                        .align(Alignment.TopEnd)
                }
            )
        }

        AuthorGalleryZaps(zapEvents, backgroundColor, nav, accountViewModel)
    }
}

@Composable
fun RenderBoostGallery(
    boostEvents: ImmutableList<Note>,
    backgroundColor: MutableState<Color>,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel
) {
    Row(
        modifier = remember {
            Modifier.fillMaxWidth()
        }
    ) {
        Box(
            modifier = remember {
                Modifier
                    .width(55.dp)
                    .padding(end = 4.dp)
            }
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_retweeted),
                null,
                modifier = remember {
                    Modifier
                        .size(19.dp)
                        .align(Alignment.TopEnd)
                },
                tint = Color.Unspecified
            )
        }

        AuthorGallery(boostEvents, backgroundColor, nav, accountViewModel)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthorGalleryZaps(
    authorNotes: ImmutableList<CombinedZap>,
    backgroundColor: MutableState<Color>,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel
) {
    Column(modifier = Modifier.padding(start = 10.dp)) {
        FlowRow() {
            authorNotes.forEach {
                AuthorPictureAndComment(it.request, it.response, backgroundColor, nav, accountViewModel)
            }
        }
    }
}

@Immutable
data class ZapAmountCommentNotification(
    val user: User?,
    val comment: String?,
    val amount: String?
)

@Composable
private fun AuthorPictureAndComment(
    zapRequest: Note,
    zapEvent: Note?,
    backgroundColor: MutableState<Color>,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel
) {
    var content by remember {
        mutableStateOf(
            ZapAmountCommentNotification(
                user = zapRequest.author,
                comment = null,
                amount = null
            )
        )
    }

    LaunchedEffect(key1 = zapRequest.idHex, key2 = zapEvent?.idHex) {
        launch(Dispatchers.Default) {
            (zapRequest.event as? LnZapRequestEvent)?.let {
                val decryptedContent = accountViewModel.decryptZap(zapRequest)
                val amount = (zapEvent?.event as? LnZapEvent)?.amount
                if (decryptedContent != null) {
                    val newAuthor = LocalCache.getOrCreateUser(decryptedContent.pubKey)
                    content = ZapAmountCommentNotification(newAuthor, decryptedContent.content.ifBlank { null }, showAmountAxis(amount))
                } else {
                    if (!zapRequest.event?.content().isNullOrBlank() || amount != null) {
                        content = ZapAmountCommentNotification(zapRequest.author, zapRequest.event?.content()?.ifBlank { null }, showAmountAxis(amount))
                    }
                }
            }
        }
    }

    content.let {
        val route by remember {
            derivedStateOf {
                "User/${it.user?.pubkeyHex}"
            }
        }

        it.user?.let { user ->
            AuthorPictureAndComment(
                author = user,
                comment = it.comment,
                amount = it.amount,
                route = route,
                backgroundColor = backgroundColor,
                nav = nav,
                accountViewModel = accountViewModel
            )
        }
    }
}

@Composable
private fun AuthorPictureAndComment(
    author: User,
    comment: String?,
    amount: String?,
    route: String,
    backgroundColor: MutableState<Color>,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel
) {
    val authorPictureModifier = remember { Modifier }

    val modifier = remember {
        Modifier.clickable {
            nav(route)
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = remember { Modifier.size(35.dp) }, contentAlignment = Alignment.BottomCenter) {
            FastNoteAuthorPicture(
                author = author,
                size = remember { 35.dp },
                accountViewModel = accountViewModel,
                pictureModifier = authorPictureModifier
            )

            amount?.let {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape = CircleShape),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val backgroundColor = MaterialTheme.colors.overPictureBackground
                    Box(
                        modifier = remember {
                            Modifier
                                .fillMaxWidth()
                                .drawBehind { drawRect(backgroundColor) }
                        },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Text(
                            text = it,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colors.secondaryVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 1.dp)
                        )
                    }
                }
            }
        }

        comment?.let {
            Spacer(modifier = Modifier.width(5.dp))
            TranslatableRichTextViewer(
                content = it,
                canPreview = true,
                tags = remember { ImmutableListOfLists() },
                modifier = remember { Modifier.fillMaxWidth() },
                backgroundColor = backgroundColor,
                accountViewModel = accountViewModel,
                nav = nav
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthorGallery(
    authorNotes: ImmutableList<Note>,
    backgroundColor: MutableState<Color>,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel
) {
    Column(modifier = remember { Modifier.padding(start = 10.dp) }) {
        FlowRow() {
            authorNotes.forEach { note ->
                Box(remember { Modifier.size(35.dp) }) {
                    NotePictureAndComment(note, backgroundColor, nav, accountViewModel)
                }
            }
        }
    }
}

@Composable
private fun NotePictureAndComment(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel
) {
    val author by remember(baseNote) {
        derivedStateOf {
            baseNote.author
        }
    }

    val route by remember(baseNote) {
        derivedStateOf {
            "User/${baseNote.author?.pubkeyHex}"
        }
    }

    author?.let { AuthorPictureAndComment(it, null, null, route, backgroundColor, nav, accountViewModel) }
}

@Composable
fun FastNoteAuthorPicture(
    author: User,
    size: Dp,
    pictureModifier: Modifier = Modifier,
    accountViewModel: AccountViewModel
) {
    var profilePicture by remember {
        mutableStateOf(author.info?.picture)
    }

    val authorPubKey = remember {
        author.pubkeyHex
    }

    WatchUserMetadata(author) {
        if (it.picture != profilePicture) {
            profilePicture = it.picture
        }
    }

    UserPicture(
        userHex = authorPubKey,
        userPicture = profilePicture,
        size = size,
        modifier = pictureModifier,
        accountViewModel = accountViewModel
    )
}

@Composable
fun WatchUserMetadata(userBase: User, onMetadataChanges: (UserMetadata) -> Unit) {
    val userState by userBase.live().metadata.observeAsState()
    LaunchedEffect(key1 = userState) {
        launch(Dispatchers.Default) {
            userState?.user?.info?.let {
                onMetadataChanges(it)
            }
        }
    }
}
