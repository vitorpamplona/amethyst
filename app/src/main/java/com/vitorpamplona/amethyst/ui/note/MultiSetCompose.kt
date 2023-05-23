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
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.vitorpamplona.amethyst.model.UserState
import com.vitorpamplona.amethyst.service.model.LnZapRequestEvent
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.screen.MultiSetCard
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MultiSetCompose(multiSetCard: MultiSetCard, routeForLastRead: String, accountViewModel: AccountViewModel, navController: NavController) {
    val baseNote = remember { multiSetCard.note }

    val noteState by baseNote.live().metadata.observeAsState()
    val note = remember(noteState) { noteState?.note } ?: return

    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = remember(accountState) { accountState?.account } ?: return

    var popupExpanded by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    if (note.event == null) {
        BlankNote(Modifier, false)
    } else {
        var isNew by remember { mutableStateOf(false) }

        LaunchedEffect(key1 = multiSetCard.createdAt()) {
            scope.launch(Dispatchers.IO) {
                val newIsNew = multiSetCard.createdAt > NotificationCache.load(routeForLastRead)

                NotificationCache.markAsRead(routeForLastRead, multiSetCard.createdAt)

                if (newIsNew != isNew) {
                    isNew = newIsNew
                }
            }
        }

        val primaryColor = MaterialTheme.colors.primary.copy(0.12f)
        val defaultBackgroundColor = MaterialTheme.colors.background

        val backgroundColor = remember(isNew) {
            if (isNew) {
                primaryColor.compositeOver(defaultBackgroundColor)
            } else {
                defaultBackgroundColor
            }
        }

        val columnModifier = remember(isNew) {
            Modifier
                .background(backgroundColor)
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = 10.dp
                )
                .combinedClickable(
                    onClick = {
                        scope.launch {
                            routeFor(
                                baseNote,
                                account.userProfile()
                            )?.let { navController.navigate(it) }
                        }
                    },
                    onLongClick = { popupExpanded = true }
                )
                .fillMaxWidth()
        }

        val zapEvents = remember { multiSetCard.zapEvents }
        val boostEvents = remember { multiSetCard.boostEvents }
        val likeEvents = remember { multiSetCard.likeEvents }

        Column(modifier = columnModifier) {
            if (zapEvents.isNotEmpty()) {
                RenderZapGallery(zapEvents, navController, account, accountViewModel)
            }

            if (boostEvents.isNotEmpty()) {
                RenderBoostGallery(boostEvents, navController, account, accountViewModel)
            }

            if (likeEvents.isNotEmpty()) {
                RenderLikeGallery(likeEvents, navController, account, accountViewModel)
            }

            Row(Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(65.dp))

                NoteCompose(
                    baseNote = baseNote,
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

@Composable
private fun RenderLikeGallery(
    likeEvents: List<Note>,
    navController: NavController,
    account: Account,
    accountViewModel: AccountViewModel
) {
    Row(Modifier.fillMaxWidth()) {
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

        AuthorGallery(likeEvents, navController, account, accountViewModel)
    }
}

@Composable
private fun RenderZapGallery(
    zapEvents: Map<Note, Note>,
    navController: NavController,
    account: Account,
    accountViewModel: AccountViewModel
) {
    Row(Modifier.fillMaxWidth()) {
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

        AuthorGalleryZaps(zapEvents, navController, account, accountViewModel)
    }
}

@Composable
private fun RenderBoostGallery(
    boostEvents: List<Note>,
    navController: NavController,
    account: Account,
    accountViewModel: AccountViewModel
) {
    Row(Modifier.fillMaxWidth()) {
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
                        .size(18.dp)
                        .align(Alignment.TopEnd)
                },
                tint = Color.Unspecified
            )
        }

        AuthorGallery(boostEvents, navController, account, accountViewModel)
    }
}

@Composable
fun AuthorGalleryZaps(
    authorNotes: Map<Note, Note>,
    navController: NavController,
    account: Account,
    accountViewModel: AccountViewModel
) {
    val accountState = account.userProfile().live().follows.observeAsState()

    val listToRender = remember {
        authorNotes.keys.take(50)
    }

    Column(modifier = Modifier.padding(start = 10.dp)) {
        FlowRow() {
            listToRender.forEach {
                AuthorPictureAndComment(it, navController, accountState, accountViewModel)
            }
        }
    }
}

@Composable
private fun AuthorPictureAndComment(
    zapRequest: Note,
    navController: NavController,
    accountUser: State<UserState?>,
    accountViewModel: AccountViewModel
) {
    val author = zapRequest.author ?: return

    var content by remember { mutableStateOf<Pair<User, String?>>(Pair(author, null)) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(key1 = zapRequest.idHex) {
        scope.launch(Dispatchers.IO) {
            (zapRequest.event as? LnZapRequestEvent)?.let {
                val decryptedContent = accountViewModel.decryptZap(zapRequest)
                if (decryptedContent != null) {
                    val newAuthor = LocalCache.getOrCreateUser(decryptedContent.pubKey)
                    content = Pair(newAuthor, decryptedContent.content)
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
    accountUser: State<UserState?>,
    accountViewModel: AccountViewModel
) {
    val modifier = remember(comment) {
        if (!comment.isNullOrBlank()) {
            Modifier
                .fillMaxWidth()
                .clickable {
                    navController.navigate("User/${author.pubkeyHex}")
                }
        } else {
            Modifier.clickable {
                navController.navigate("User/${author.pubkeyHex}")
            }
        }
    }

    Row(
        modifier = modifier,
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
    val accountState = account.userProfile().live().follows.observeAsState()
    val listToRender = remember {
        Pair(
            authorNotes.take(50).mapNotNull { it.author },
            authorNotes.size
        )
    }

    Column(modifier = Modifier.padding(start = 10.dp)) {
        FlowRow() {
            listToRender.first.forEach { author ->
                AuthorPictureAndComment(author, null, navController, accountState, accountViewModel)
            }

            if (listToRender.second > 50) {
                Text(" and ${listToRender.second - 50} others")
            }
        }
    }
}

@Composable
fun FastNoteAuthorPicture(
    author: User,
    userAccount: State<UserState?>,
    size: Dp,
    pictureModifier: Modifier = Modifier
) {
    val userState by author.live().metadata.observeAsState()
    val profilePicture = remember(userState) {
        userState?.user?.profilePicture()
    }

    val authorPubKey = remember {
        author.pubkeyHex
    }

    val showFollowingMark = remember(userAccount.value) {
        userAccount.value?.user?.isFollowingCached(author) == true || (author === userAccount.value?.user)
    }

    UserPicture(
        userHex = authorPubKey,
        userPicture = profilePicture,
        showFollowingMark = showFollowingMark,
        size = size,
        modifier = pictureModifier
    )
}
