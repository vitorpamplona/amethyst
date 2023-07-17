package com.vitorpamplona.amethyst.ui.note

import android.util.Log
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.LnZapEvent
import com.vitorpamplona.amethyst.service.model.LnZapRequestEvent
import com.vitorpamplona.amethyst.ui.actions.ImmutableListOfLists
import com.vitorpamplona.amethyst.ui.components.ImageUrlType
import com.vitorpamplona.amethyst.ui.components.InLineIconRenderer
import com.vitorpamplona.amethyst.ui.components.RobohashAsyncImageProxy
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.screen.CombinedZap
import com.vitorpamplona.amethyst.ui.screen.MultiSetCard
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.showAmountAxis
import com.vitorpamplona.amethyst.ui.theme.NotificationIconModifier
import com.vitorpamplona.amethyst.ui.theme.NotificationIconModifierSmaller
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size18dp
import com.vitorpamplona.amethyst.ui.theme.Size19dp
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.amethyst.ui.theme.Size35Modifier
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.StdStartPadding
import com.vitorpamplona.amethyst.ui.theme.WidthAuthorPictureModifier
import com.vitorpamplona.amethyst.ui.theme.WidthAuthorPictureModifierWithPadding
import com.vitorpamplona.amethyst.ui.theme.bitcoinColor
import com.vitorpamplona.amethyst.ui.theme.newItemBackgroundColor
import com.vitorpamplona.amethyst.ui.theme.overPictureBackground
import com.vitorpamplona.amethyst.ui.theme.profile35dpModifier
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

@OptIn(ExperimentalFoundationApi::class, ExperimentalTime::class)
@Composable
fun MultiSetCompose(multiSetCard: MultiSetCard, routeForLastRead: String, showHidden: Boolean = false, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val baseNote = remember { multiSetCard.note }

    val popupExpanded = remember { mutableStateOf(false) }
    val enablePopup = remember {
        { popupExpanded.value = true }
    }

    val scope = rememberCoroutineScope()

    val defaultBackgroundColor = MaterialTheme.colors.background
    val backgroundColor = remember { mutableStateOf<Color>(defaultBackgroundColor) }
    val newItemColor = MaterialTheme.colors.newItemBackgroundColor

    LaunchedEffect(key1 = multiSetCard) {
        launch(Dispatchers.IO) {
            val isNew = multiSetCard.maxCreatedAt > accountViewModel.account.loadLastRead(routeForLastRead)

            accountViewModel.account.markAsRead(routeForLastRead, multiSetCard.maxCreatedAt)

            val newBackgroundColor = if (isNew) {
                newItemColor.compositeOver(defaultBackgroundColor)
            } else {
                defaultBackgroundColor
            }

            if (backgroundColor.value != newBackgroundColor) {
                launch(Dispatchers.Main) {
                    backgroundColor.value = newBackgroundColor
                }
            }
        }
    }

    val columnModifier = remember(backgroundColor.value) {
        Modifier
            .background(backgroundColor.value)
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
                onLongClick = enablePopup
            )
            .fillMaxWidth()
    }

    Column(modifier = columnModifier) {
        val (value, elapsed) = measureTimedValue {
            Galeries(multiSetCard, backgroundColor, accountViewModel, nav)
        }
        Log.d("Rendering Metrics", "All Galeries: ${baseNote.event?.content()?.split("\n")?.getOrNull(0)?.take(15)}.. $elapsed - ")

        Row(remember { Modifier.fillMaxWidth() }) {
            Spacer(modifier = WidthAuthorPictureModifierWithPadding)

            val (value, elapsed) = measureTimedValue {
                NoteCompose(
                    baseNote = baseNote,
                    routeForLastRead = null,
                    modifier = remember { Modifier.padding(top = 5.dp) },
                    isBoostedNote = true,
                    showHidden = showHidden,
                    parentBackgroundColor = backgroundColor,
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            }
            Log.d("Rendering Metrics", "Complete: ${baseNote.event?.content()?.split("\n")?.getOrNull(0)?.take(15)}.. $elapsed")

            NoteDropDownMenu(baseNote, popupExpanded, accountViewModel)
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun Galeries(
    multiSetCard: MultiSetCard,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val zapEvents by remember { derivedStateOf { multiSetCard.zapEvents } }
    val boostEvents by remember { derivedStateOf { multiSetCard.boostEvents } }
    val likeEvents by remember { derivedStateOf { multiSetCard.likeEventsByType } }

    val hasZapEvents by remember { derivedStateOf { multiSetCard.zapEvents.isNotEmpty() } }
    val hasBoostEvents by remember { derivedStateOf { multiSetCard.boostEvents.isNotEmpty() } }
    val hasLikeEvents by remember { derivedStateOf { multiSetCard.likeEvents.isNotEmpty() } }

    if (hasZapEvents) {
        val (value, elapsed) = measureTimedValue {
            RenderZapGallery(zapEvents, backgroundColor, nav, accountViewModel)
        }
        Log.d("Rendering Metrics", "Galeries Zaps:   ${multiSetCard.note.event?.content()?.split("\n")?.getOrNull(0)?.take(15)}.. $elapsed")
    }

    if (hasBoostEvents) {
        val (value, elapsed) = measureTimedValue {
            RenderBoostGallery(boostEvents, nav, accountViewModel)
        }
        Log.d("Rendering Metrics", "Galeries Repost: ${multiSetCard.note.event?.content()?.split("\n")?.getOrNull(0)?.take(15)}.. $elapsed")
    }

    if (hasLikeEvents) {
        val (value, elapsed) = measureTimedValue {
            likeEvents.forEach {
                RenderLikeGallery(it.key, it.value, nav, accountViewModel)
            }
        }
        Log.d("Rendering Metrics", "Galeries Like:   ${multiSetCard.note.event?.content()?.split("\n")?.getOrNull(0)?.take(15)}.. $elapsed")
    }
}

@Composable
fun RenderLikeGallery(
    reactionType: String,
    likeEvents: ImmutableList<Note>,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel
) {
    if (likeEvents.isNotEmpty()) {
        Row(Modifier.fillMaxWidth()) {
            Box(
                modifier = NotificationIconModifier
            ) {
                val modifier = remember {
                    Modifier.align(Alignment.TopEnd)
                }

                if (reactionType.startsWith(":")) {
                    val noStartColon = reactionType.removePrefix(":")
                    val url = noStartColon.substringAfter(":")

                    val renderable = listOf(
                        ImageUrlType(url)
                    ).toImmutableList()

                    InLineIconRenderer(
                        renderable,
                        style = SpanStyle(color = Color.White),
                        maxLines = 1,
                        modifier = modifier
                    )
                } else {
                    when (val shortReaction = reactionType) {
                        "+" -> LikedIcon(Size18dp)
                        "-" -> Text(text = "\uD83D\uDC4E", modifier = modifier)
                        else -> Text(text = shortReaction, modifier = modifier)
                    }
                }
            }

            AuthorGallery(likeEvents, nav, accountViewModel)
        }
    }
}

@Composable
fun RenderZapGallery(
    zapEvents: ImmutableList<CombinedZap>,
    backgroundColor: MutableState<Color>,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel
) {
    Row(Modifier.fillMaxWidth()) {
        Box(
            modifier = WidthAuthorPictureModifier
        ) {
            ZappedIcon(
                modifier = remember {
                    Modifier.size(Size25dp).align(Alignment.TopEnd)
                }
            )
        }

        AuthorGalleryZaps(zapEvents, backgroundColor, nav, accountViewModel)
    }
}

@Composable
fun RenderBoostGallery(
    boostEvents: ImmutableList<Note>,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel
) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = NotificationIconModifierSmaller
        ) {
            RepostedIcon(
                modifier = remember { Modifier.size(Size19dp).align(Alignment.TopEnd) }
            )
        }

        AuthorGallery(boostEvents, nav, accountViewModel)
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
    Column(modifier = StdStartPadding) {
        FlowRow() {
            authorNotes.forEach {
                ParseAuthorCommentAndAmount(it, backgroundColor, nav, accountViewModel)
            }
        }
    }
}

@Composable
private fun ParseAuthorCommentAndAmount(
    zap: CombinedZap,
    backgroundColor: MutableState<Color>,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel
) {
    ParseAuthorCommentAndAmount(zap.request, zap.response, accountViewModel) { state ->
        RenderState(state, backgroundColor, accountViewModel, nav)
    }
}

@Immutable
data class ZapAmountCommentNotification(
    val user: User?,
    val comment: String?,
    val amount: String?
)

@Composable
private fun ParseAuthorCommentAndAmount(
    zapRequest: Note,
    zapEvent: Note?,
    accountViewModel: AccountViewModel,
    onReady: @Composable (MutableState<ZapAmountCommentNotification>) -> Unit
) {
    val content = remember {
        mutableStateOf(
            ZapAmountCommentNotification(
                user = zapRequest.author,
                comment = null,
                amount = null
            )
        )
    }

    LaunchedEffect(key1 = zapRequest.idHex, key2 = zapEvent?.idHex) {
        launch(Dispatchers.IO) {
            (zapRequest.event as? LnZapRequestEvent)?.let {
                val decryptedContent = accountViewModel.decryptZap(zapRequest)
                val amount = (zapEvent?.event as? LnZapEvent)?.amount
                if (decryptedContent != null) {
                    val newAuthor = LocalCache.getOrCreateUser(decryptedContent.pubKey)
                    val newState = ZapAmountCommentNotification(newAuthor, decryptedContent.content.ifBlank { null }, showAmountAxis(amount))

                    launch(Dispatchers.Main) { content.value = newState }
                } else {
                    if (!zapRequest.event?.content().isNullOrBlank() || amount != null) {
                        val newState = ZapAmountCommentNotification(zapRequest.author, zapRequest.event?.content()?.ifBlank { null }, showAmountAxis(amount))
                        launch(Dispatchers.Main) { content.value = newState }
                    }
                }
            }
        }
    }

    onReady(content)
}

@Composable
private fun RenderState(
    content: MutableState<ZapAmountCommentNotification>,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    Row(
        modifier = Modifier.clickable {
            content.value.user?.let {
                nav(routeFor(it))
            }
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        DisplayAuthorCommentAndAmount(
            authorComment = content,
            backgroundColor = backgroundColor,
            nav = nav,
            accountViewModel = accountViewModel
        )
    }
}

val amountBoxModifier = Modifier
    .size(Size35dp)
    .clip(shape = CircleShape)

val textBoxModifier = Modifier
    .padding(start = 5.dp)
    .fillMaxWidth()

val bottomPadding1dp = Modifier.padding(bottom = 1.dp)

val commentTextSize = 12.sp

@Composable
private fun DisplayAuthorCommentAndAmount(
    authorComment: MutableState<ZapAmountCommentNotification>,
    backgroundColor: MutableState<Color>,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel
) {
    Box(modifier = Size35Modifier, contentAlignment = Alignment.BottomCenter) {
        CrossfadeToDisplayPicture(authorComment, accountViewModel)
        CrossfadeToDisplayAmount(authorComment)
    }

    CrossfadeToDisplayComment(authorComment, backgroundColor, nav, accountViewModel)
}

@Composable
fun CrossfadeToDisplayPicture(authorComment: MutableState<ZapAmountCommentNotification>, accountViewModel: AccountViewModel) {
    Crossfade(authorComment.value) {
        WatchUserMetadataAndFollowsAndRenderUserProfilePictureOrDefaultAuthor(it.user, accountViewModel)
    }
}

@Composable
fun CrossfadeToDisplayAmount(authorComment: MutableState<ZapAmountCommentNotification>) {
    Crossfade(authorComment.value, modifier = amountBoxModifier) {
        it.amount?.let {
            Box(
                modifier = amountBoxModifier,
                contentAlignment = Alignment.BottomCenter
            ) {
                val backgroundColor = MaterialTheme.colors.overPictureBackground
                Box(
                    modifier = remember {
                        Modifier
                            .width(Size35dp)
                            .background(backgroundColor)
                    },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        text = it,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.bitcoinColor,
                        fontSize = commentTextSize,
                        modifier = bottomPadding1dp
                    )
                }
            }
        }
    }
}

@Composable
fun CrossfadeToDisplayComment(
    authorComment: MutableState<ZapAmountCommentNotification>,
    backgroundColor: MutableState<Color>,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel
) {
    Crossfade(authorComment.value) {
        it.comment?.let {
            TranslatableRichTextViewer(
                content = it,
                canPreview = true,
                tags = remember { ImmutableListOfLists() },
                modifier = textBoxModifier,
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
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel
) {
    Column(modifier = StdStartPadding) {
        FlowRow() {
            authorNotes.forEach { note ->
                BoxedAuthor(note, nav, accountViewModel)
            }
        }
    }
}

@Composable
private fun BoxedAuthor(
    note: Note,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel
) {
    Box(modifier = Size35Modifier.clickable(onClick = { nav(authorRouteFor(note)) })) {
        WatchNoteAuthor(note) { targetAuthor ->
            Crossfade(targetState = targetAuthor, modifier = Size35Modifier) { author ->
                WatchUserMetadataAndFollowsAndRenderUserProfilePictureOrDefaultAuthor(author, accountViewModel)
            }
        }
    }
}

@Composable
fun WatchUserMetadataAndFollowsAndRenderUserProfilePictureOrDefaultAuthor(
    author: User?,
    accountViewModel: AccountViewModel
) {
    if (author != null) {
        WatchUserMetadataAndFollowsAndRenderUserProfilePicture(author, accountViewModel)
    } else {
        DisplayBlankAuthor(Size35dp)
    }
}

@Composable
fun WatchUserMetadataAndFollowsAndRenderUserProfilePicture(
    author: User,
    accountViewModel: AccountViewModel
) {
    WatchUserMetadata(author) { baseUserPicture ->
        Crossfade(targetState = baseUserPicture) { userPicture ->
            RobohashAsyncImageProxy(
                robot = author.pubkeyHex,
                model = userPicture,
                contentDescription = stringResource(id = R.string.profile_image),
                modifier = MaterialTheme.colors.profile35dpModifier,
                contentScale = ContentScale.Crop
            )
        }
    }

    WatchUserFollows(author.pubkeyHex, accountViewModel) { isFollowing ->
        Crossfade(targetState = isFollowing) {
            if (it) {
                Box(modifier = Size35Modifier, contentAlignment = Alignment.TopEnd) {
                    FollowingIcon(Size10dp)
                }
            }
        }
    }
}

@Composable
private fun WatchNoteAuthor(
    baseNote: Note,
    onContent: @Composable (User?) -> Unit
) {
    val author by baseNote.live().metadata.map {
        it.note.author
    }.observeAsState(baseNote.author)

    onContent(author)
}

@Composable
private fun WatchUserMetadata(
    author: User,
    onNewMetadata: @Composable (String?) -> Unit
) {
    val userProfile by author.live().metadata.map {
        it.user.profilePicture()
    }.distinctUntilChanged().observeAsState(author.profilePicture())

    onNewMetadata(userProfile)
}
