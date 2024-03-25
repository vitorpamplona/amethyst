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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.components.InLineIconRenderer
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.authorRouteFor
import com.vitorpamplona.amethyst.ui.navigation.routeFor
import com.vitorpamplona.amethyst.ui.note.elements.NoteDropDownMenu
import com.vitorpamplona.amethyst.ui.screen.CombinedZap
import com.vitorpamplona.amethyst.ui.screen.MultiSetCard
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.HalfTopPadding
import com.vitorpamplona.amethyst.ui.theme.NotificationIconModifier
import com.vitorpamplona.amethyst.ui.theme.NotificationIconModifierSmaller
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size19dp
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.amethyst.ui.theme.Size35Modifier
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.StdStartPadding
import com.vitorpamplona.amethyst.ui.theme.WidthAuthorPictureModifier
import com.vitorpamplona.amethyst.ui.theme.WidthAuthorPictureModifierWithPadding
import com.vitorpamplona.amethyst.ui.theme.bitcoinColor
import com.vitorpamplona.amethyst.ui.theme.overPictureBackground
import com.vitorpamplona.amethyst.ui.theme.profile35dpModifier
import com.vitorpamplona.quartz.encoders.Nip30CustomEmoji
import com.vitorpamplona.quartz.events.EmptyTagList
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalFoundationApi::class, ExperimentalTime::class)
@Composable
fun MultiSetCompose(
    multiSetCard: MultiSetCard,
    routeForLastRead: String,
    showHidden: Boolean = false,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val baseNote = remember { multiSetCard.note }

    val popupExpanded = remember { mutableStateOf(false) }
    val enablePopup = remember { { popupExpanded.value = true } }

    val scope = rememberCoroutineScope()

    val backgroundColor =
        calculateBackgroundColor(
            createdAt = multiSetCard.maxCreatedAt,
            routeForLastRead = routeForLastRead,
            accountViewModel = accountViewModel,
        )

    val columnModifier =
        remember(backgroundColor.value) {
            Modifier.fillMaxWidth()
                .background(backgroundColor.value)
                .combinedClickable(
                    onClick = {
                        scope.launch { routeFor(baseNote, accountViewModel.userProfile())?.let { nav(it) } }
                    },
                    onLongClick = enablePopup,
                )
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = 10.dp,
                )
        }

    Column(modifier = columnModifier) {
        Galeries(multiSetCard, backgroundColor, accountViewModel, nav)

        Row(Modifier.fillMaxWidth()) {
            Spacer(modifier = WidthAuthorPictureModifierWithPadding)

            NoteCompose(
                baseNote = baseNote,
                routeForLastRead = null,
                modifier = HalfTopPadding,
                isBoostedNote = true,
                isHiddenFeed = showHidden,
                quotesLeft = 1,
                parentBackgroundColor = backgroundColor,
                accountViewModel = accountViewModel,
                nav = nav,
            )

            NoteDropDownMenu(baseNote, popupExpanded, null, accountViewModel, nav)
        }
    }
}

@Composable
private fun Galeries(
    multiSetCard: MultiSetCard,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val hasZapEvents by remember { derivedStateOf { multiSetCard.zapEvents.isNotEmpty() } }
    val hasBoostEvents by remember { derivedStateOf { multiSetCard.boostEvents.isNotEmpty() } }
    val hasLikeEvents by remember { derivedStateOf { multiSetCard.likeEvents.isNotEmpty() } }

    if (hasZapEvents) {
        var zapEvents by
            remember(multiSetCard.zapEvents) {
                mutableStateOf(
                    accountViewModel.cachedDecryptAmountMessageInGroup(multiSetCard.zapEvents),
                )
            }

        LaunchedEffect(key1 = Unit) {
            accountViewModel.decryptAmountMessageInGroup(multiSetCard.zapEvents) { zapEvents = it }
        }

        RenderZapGallery(zapEvents, backgroundColor, nav, accountViewModel)
    }

    if (hasBoostEvents) {
        RenderBoostGallery(multiSetCard.boostEvents, nav, accountViewModel)
    }

    if (hasLikeEvents) {
        multiSetCard.likeEventsByType.forEach {
            RenderLikeGallery(it.key, it.value, nav, accountViewModel)
        }
    }
}

@Composable
fun RenderLikeGallery(
    reactionType: String,
    likeEvents: ImmutableList<Note>,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel,
) {
    if (likeEvents.isNotEmpty()) {
        Row(Modifier.fillMaxWidth()) {
            Box(
                modifier = NotificationIconModifier,
            ) {
                val modifier = remember { Modifier.align(Alignment.TopEnd) }

                if (reactionType.startsWith(":")) {
                    val noStartColon = reactionType.removePrefix(":")
                    val url = noStartColon.substringAfter(":")

                    val renderable =
                        persistentListOf(Nip30CustomEmoji.ImageUrlType(url))

                    InLineIconRenderer(
                        renderable,
                        style = SpanStyle(color = Color.White),
                        maxLines = 1,
                        modifier = modifier,
                    )
                } else {
                    when (val shortReaction = reactionType) {
                        "+" -> LikedIcon(modifier.size(Size19dp))
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
    zapEvents: ImmutableList<ZapAmountCommentNotification>,
    backgroundColor: MutableState<Color>,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel,
) {
    Row(Modifier.fillMaxWidth()) {
        Box(
            modifier = WidthAuthorPictureModifier,
        ) {
            ZappedIcon(
                modifier = remember { Modifier.size(Size25dp).align(Alignment.TopEnd) },
            )
        }

        AuthorGalleryZaps(zapEvents, backgroundColor, nav, accountViewModel)
    }
}

@Composable
fun RenderBoostGallery(
    boostEvents: ImmutableList<Note>,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = NotificationIconModifierSmaller,
        ) {
            RepostedIcon(
                modifier = remember { Modifier.size(Size20dp).align(Alignment.TopEnd) },
            )
        }

        AuthorGallery(boostEvents, nav, accountViewModel)
    }
}

@Composable
fun RenderBoostGallery(
    noteToGetBoostEvents: NoteState,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = NotificationIconModifierSmaller,
        ) {
            RepostedIcon(
                modifier = remember { Modifier.size(Size20dp).align(Alignment.TopEnd) },
            )
        }

        AuthorGallery(noteToGetBoostEvents, nav, accountViewModel)
    }
}

@Composable
fun MapZaps(
    zaps: ImmutableList<CombinedZap>,
    accountViewModel: AccountViewModel,
    content: @Composable (ImmutableList<ZapAmountCommentNotification>) -> Unit,
) {
    var zapEvents by
        remember(zaps) {
            mutableStateOf<ImmutableList<ZapAmountCommentNotification>>(persistentListOf())
        }

    LaunchedEffect(key1 = zaps) {
        accountViewModel.decryptAmountMessageInGroup(zaps) { zapEvents = it }
    }

    content(zapEvents)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthorGalleryZaps(
    authorNotes: ImmutableList<ZapAmountCommentNotification>,
    backgroundColor: MutableState<Color>,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel,
) {
    Column(modifier = StdStartPadding) {
        FlowRow { authorNotes.forEach { RenderState(it, backgroundColor, accountViewModel, nav) } }
    }
}

@Immutable
data class ZapAmountCommentNotification(
    val user: User?,
    val comment: String?,
    val amount: String?,
)

@Composable
private fun ParseAuthorCommentAndAmount(
    zapRequest: Note,
    zapEvent: Note?,
    accountViewModel: AccountViewModel,
    onReady: @Composable (MutableState<ZapAmountCommentNotification>) -> Unit,
) {
    val content =
        remember {
            mutableStateOf(
                ZapAmountCommentNotification(
                    user = zapRequest.author,
                    comment = null,
                    amount = null,
                ),
            )
        }

    LaunchedEffect(key1 = zapRequest.idHex, key2 = zapEvent?.idHex) {
        accountViewModel.decryptAmountMessage(zapRequest, zapEvent) { newState ->
            if (newState != null) {
                content.value = newState
            }
        }
    }

    onReady(content)
}

fun click(
    content: ZapAmountCommentNotification,
    nav: (String) -> Unit,
) {
    content.user?.let { nav(routeFor(it)) }
}

@Composable
private fun RenderState(
    content: ZapAmountCommentNotification,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    Row(
        modifier = Modifier.clickable { click(content, nav) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DisplayAuthorCommentAndAmount(
            authorComment = content,
            backgroundColor = backgroundColor,
            nav = nav,
            accountViewModel = accountViewModel,
        )
    }
}

val amountBoxModifier = Modifier.size(Size35dp).clip(shape = CircleShape)

val textBoxModifier = Modifier.padding(start = 5.dp).fillMaxWidth()

val bottomPadding1dp = Modifier.padding(bottom = 1.dp)

val commentTextSize = 12.sp

@Composable
private fun DisplayAuthorCommentAndAmount(
    authorComment: ZapAmountCommentNotification,
    backgroundColor: MutableState<Color>,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel,
) {
    Box(modifier = Size35Modifier, contentAlignment = Alignment.BottomCenter) {
        WatchUserMetadataAndFollowsAndRenderUserProfilePictureOrDefaultAuthor(
            authorComment.user,
            accountViewModel,
        )
        authorComment.amount?.let { CrossfadeToDisplayAmount(it) }
    }

    authorComment.comment?.let {
        CrossfadeToDisplayComment(it, backgroundColor, nav, accountViewModel)
    }
}

@Composable
fun CrossfadeToDisplayAmount(amount: String) {
    Box(
        modifier = amountBoxModifier,
        contentAlignment = Alignment.BottomCenter,
    ) {
        val backgroundColor = MaterialTheme.colorScheme.overPictureBackground
        Box(
            modifier = remember { Modifier.width(Size35dp).background(backgroundColor) },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                text = amount,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.bitcoinColor,
                fontSize = commentTextSize,
                modifier = bottomPadding1dp,
            )
        }
    }
}

@Composable
fun CrossfadeToDisplayComment(
    comment: String,
    backgroundColor: MutableState<Color>,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel,
) {
    TranslatableRichTextViewer(
        content = comment,
        canPreview = true,
        quotesLeft = 1,
        tags = EmptyTagList,
        modifier = textBoxModifier,
        backgroundColor = backgroundColor,
        id = comment,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthorGallery(
    authorNotes: ImmutableList<Note>,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel,
) {
    Column(modifier = StdStartPadding) {
        FlowRow { authorNotes.forEach { note -> BoxedAuthor(note, nav, accountViewModel) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthorGallery(
    noteToGetBoostEvents: NoteState,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel,
) {
    Column(modifier = StdStartPadding) {
        FlowRow {
            noteToGetBoostEvents.note.boosts.forEach { note -> BoxedAuthor(note, nav, accountViewModel) }
        }
    }
}

@Composable
private fun BoxedAuthor(
    note: Note,
    nav: (String) -> Unit,
    accountViewModel: AccountViewModel,
) {
    Box(modifier = Size35Modifier.clickable(onClick = { nav(authorRouteFor(note)) })) {
        WatchAuthorWithBlank(note, Size35Modifier) { author ->
            WatchUserMetadataAndFollowsAndRenderUserProfilePictureOrDefaultAuthor(
                author,
                accountViewModel,
            )
        }
    }
}

@Composable
fun WatchUserMetadataAndFollowsAndRenderUserProfilePictureOrDefaultAuthor(
    author: User?,
    accountViewModel: AccountViewModel,
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
    accountViewModel: AccountViewModel,
) {
    WatchUserMetadata(author) { baseUserPicture ->
        // Crossfade(targetState = baseUserPicture) { userPicture ->
        RobohashFallbackAsyncImage(
            robot = author.pubkeyHex,
            model = baseUserPicture,
            contentDescription = stringResource(id = R.string.profile_image),
            modifier = MaterialTheme.colorScheme.profile35dpModifier,
            contentScale = ContentScale.Crop,
            loadProfilePicture = accountViewModel.settings.showProfilePictures.value,
        )
        // }
    }

    WatchUserFollows(author.pubkeyHex, accountViewModel) { isFollowing ->
        // Crossfade(targetState = isFollowing) {
        if (isFollowing) {
            Box(modifier = Size35Modifier, contentAlignment = Alignment.TopEnd) {
                FollowingIcon(Size10dp)
            }
        }
        // }
    }
}

@Composable
private fun WatchUserMetadata(
    author: User,
    onNewMetadata: @Composable (String?) -> Unit,
) {
    val userProfile by author.live().profilePictureChanges.observeAsState(author.profilePicture())

    onNewMetadata(userProfile)
}
