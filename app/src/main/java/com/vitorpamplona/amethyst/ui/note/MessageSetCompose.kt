package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.NotificationCache
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import com.vitorpamplona.amethyst.ui.screen.MessageSetCard
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageSetCompose(messageSetCard: MessageSetCard, isInnerNote: Boolean = false, routeForLastRead: String, accountViewModel: AccountViewModel, navController: NavController) {
    val noteState by messageSetCard.note.live().metadata.observeAsState()
    val note = noteState?.note

    val noteEvent = note?.event
    var popupExpanded by remember { mutableStateOf(false) }

    if (note == null) {
        BlankNote(Modifier, isInnerNote)
    } else {
        var isNew by remember { mutableStateOf<Boolean>(false) }

        LaunchedEffect(key1 = messageSetCard.createdAt()) {
            withContext(Dispatchers.IO) {
                isNew =
                    messageSetCard.createdAt() > NotificationCache.load(routeForLastRead)

                NotificationCache.markAsRead(routeForLastRead, messageSetCard.createdAt())
            }
        }

        val backgroundColor = if (isNew) {
            MaterialTheme.colors.primary.copy(0.12f).compositeOver(MaterialTheme.colors.background)
        } else {
            MaterialTheme.colors.background
        }

        Column(
            modifier = Modifier.background(backgroundColor).combinedClickable(
                onClick = {
                    if (noteEvent is ChannelMessageEvent) {
                        note.channel()?.let {
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
                        navController.navigate("Note/${note.idHex}") {
                            launchSingleTop = true
                        }
                    }
                },
                onLongClick = { popupExpanded = true }
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(
                        start = if (!isInnerNote) 12.dp else 0.dp,
                        end = if (!isInnerNote) 12.dp else 0.dp,
                        top = 10.dp
                    )
            ) {
                // Draws the like picture outside the boosted card.
                if (!isInnerNote) {
                    Box(
                        modifier = Modifier
                            .width(55.dp)
                            .padding(top = 5.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_dm),
                            null,
                            modifier = Modifier.size(16.dp).align(Alignment.TopEnd),
                            tint = MaterialTheme.colors.primary
                        )
                    }
                }

                Column(modifier = Modifier.padding(start = if (!isInnerNote) 10.dp else 0.dp)) {
                    NoteCompose(
                        baseNote = note,
                        routeForLastRead = null,
                        isBoostedNote = true,
                        addMarginTop = false,
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
