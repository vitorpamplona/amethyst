package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.accompanist.flowlayout.FlowRow
import com.vitorpamplona.amethyst.NotificationCache
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.LnZapRequestEvent
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.screen.MultiSetCard
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MultiSetCompose(multiSetCard: MultiSetCard, routeForLastRead: String, accountViewModel: AccountViewModel, navController: NavController) {
    val noteState by multiSetCard.note.live().metadata.observeAsState()
    val note = noteState?.note

    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    var popupExpanded by remember { mutableStateOf(false) }

    if (note == null) {
        BlankNote(Modifier, false)
    } else {
        var isNew by remember { mutableStateOf<Boolean>(false) }

        LaunchedEffect(key1 = multiSetCard.createdAt()) {
            withContext(Dispatchers.IO) {
                isNew = multiSetCard.createdAt > NotificationCache.load(routeForLastRead)

                NotificationCache.markAsRead(routeForLastRead, multiSetCard.createdAt)
            }
        }

        val backgroundColor = if (isNew) {
            MaterialTheme.colors.primary.copy(0.12f).compositeOver(MaterialTheme.colors.background)
        } else {
            MaterialTheme.colors.background
        }

        Column(
            modifier = Modifier
                .background(backgroundColor)
                .combinedClickable(
                    onClick = {
                        routeFor(note, account.userProfile())?.let { navController.navigate(it) }
                    },
                    onLongClick = { popupExpanded = true }
                )
        ) {
            Row(
                modifier = Modifier
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = 10.dp
                    )
            ) {
                Column(Modifier.fillMaxWidth()) {
                    if (multiSetCard.zapEvents.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth()) {
                            // Draws the like picture outside the boosted card.
                            Box(
                                modifier = Modifier
                                    .width(55.dp)
                                    .padding(0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = "Zaps",
                                    tint = BitcoinOrange,
                                    modifier = Modifier
                                        .size(25.dp)
                                        .align(Alignment.TopEnd)
                                )
                            }

                            AuthorGalleryZaps(multiSetCard.zapEvents, navController, account, accountViewModel)
                        }
                    }

                    if (multiSetCard.boostEvents.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .width(55.dp)
                                    .padding(end = 4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_retweeted),
                                    null,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .align(Alignment.TopEnd),
                                    tint = Color.Unspecified
                                )
                            }

                            AuthorGallery(multiSetCard.boostEvents, navController, account, accountViewModel)
                        }
                    }

                    if (multiSetCard.likeEvents.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .width(55.dp)
                                    .padding(end = 5.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_liked),
                                    null,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .align(Alignment.TopEnd),
                                    tint = Color.Unspecified
                                )
                            }

                            AuthorGallery(multiSetCard.likeEvents, navController, account, accountViewModel)
                        }
                    }

                    Row(Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.width(65.dp))

                        NoteCompose(
                            baseNote = multiSetCard.note,
                            routeForLastRead = null,
                            modifier = Modifier.padding(top = 5.dp),
                            isBoostedNote = true,
                            parentBackgroundColor = backgroundColor,
                            accountViewModel = accountViewModel,
                            navController = navController
                        )

                        NoteDropDownMenu(note, popupExpanded, { popupExpanded = false }, accountViewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun AuthorGalleryZaps(
    authorNotes: Map<Note, Note>,
    navController: NavController,
    account: Account,
    accountViewModel: AccountViewModel
) {
    val accountState by account.userProfile().live().follows.observeAsState()
    val accountUser = accountState?.user ?: return

    Column(modifier = Modifier.padding(start = 10.dp)) {
        FlowRow() {
            authorNotes.forEach {
                AuthorPictureAndComment(it.key, navController, accountUser, accountViewModel)
            }
        }
    }
}

@Composable
private fun AuthorPictureAndComment(
    zapRequest: Note,
    navController: NavController,
    accountUser: User,
    accountViewModel: AccountViewModel
) {
    val author = zapRequest.author ?: return

    var content by remember { mutableStateOf<Pair<User, String?>>(Pair(author, null)) }

    LaunchedEffect(key1 = zapRequest.idHex) {
        withContext(Dispatchers.IO) {
            (zapRequest.event as? LnZapRequestEvent)?.let {
                val decryptedContent = accountViewModel.decryptZap(zapRequest)
                if (decryptedContent != null) {
                    val author = LocalCache.getOrCreateUser(decryptedContent.pubKey)
                    content = Pair(author, decryptedContent.content)
                } else {
                    if (!zapRequest.event?.content().isNullOrBlank()) {
                        content = Pair(author, zapRequest.event?.content())
                    }
                }
            }
        }
    }

    AuthorPictureAndComment(content.first, content.second, navController, accountUser, accountViewModel)
}

@Composable
private fun AuthorPictureAndComment(
    author: User,
    comment: String?,
    navController: NavController,
    accountUser: User,
    accountViewModel: AccountViewModel
) {
    val modifier = if (!comment.isNullOrBlank()) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
    }

    Row(
        modifier = modifier.clickable {
            navController.navigate("User/${author.pubkeyHex}")
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        FastNoteAuthorPicture(
            author = author,
            userAccount = accountUser,
            size = 35.dp
        )

        if (!comment.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(5.dp))
            TranslatableRichTextViewer(
                content = comment,
                canPreview = true,
                tags = null,
                modifier = Modifier.weight(1f),
                backgroundColor = MaterialTheme.colors.background,
                accountViewModel = accountViewModel,
                navController = navController
            )
        }
    }
}

@Composable
fun AuthorGallery(
    authorNotes: Collection<Note>,
    navController: NavController,
    account: Account,
    accountViewModel: AccountViewModel
) {
    val accountState by account.userProfile().live().follows.observeAsState()
    val accountUser = accountState?.user ?: return

    Column(modifier = Modifier.padding(start = 10.dp)) {
        FlowRow() {
            authorNotes.forEach {
                val author = it.author
                if (author != null) {
                    AuthorPictureAndComment(author, null, navController, accountUser, accountViewModel)
                }
            }
        }
    }
}

@Composable
fun FastNoteAuthorPicture(
    author: User,
    userAccount: User,
    size: Dp,
    pictureModifier: Modifier = Modifier
) {
    val userState by author.live().metadata.observeAsState()
    val user = userState?.user ?: return

    val showFollowingMark = userAccount.isFollowingCached(user) || user === userAccount

    UserPicture(
        userHex = user.pubkeyHex,
        userPicture = user.profilePicture(),
        showFollowingMark = showFollowingMark,
        size = size,
        modifier = pictureModifier
    )
}
