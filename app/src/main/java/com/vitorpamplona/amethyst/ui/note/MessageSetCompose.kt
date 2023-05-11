package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vitorpamplona.amethyst.NotificationCache
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.screen.MessageSetCard
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageSetCompose(messageSetCard: MessageSetCard, routeForLastRead: String, accountViewModel: AccountViewModel, navController: NavController) {
    val noteState by messageSetCard.note.live().metadata.observeAsState()
    val note = remember(noteState) { noteState?.note }

    var popupExpanded by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    if (note == null) {
        BlankNote(Modifier)
    } else {
        var isNew by remember { mutableStateOf(false) }

        LaunchedEffect(key1 = messageSetCard.createdAt()) {
            scope.launch(Dispatchers.IO) {
                val newIsNew =
                    messageSetCard.createdAt() > NotificationCache.load(routeForLastRead)

                NotificationCache.markAsRead(routeForLastRead, messageSetCard.createdAt())

                if (newIsNew != isNew) {
                    isNew = newIsNew
                }
            }
        }

        val backgroundColor = if (isNew) {
            MaterialTheme.colors.primary.copy(0.12f).compositeOver(MaterialTheme.colors.background)
        } else {
            MaterialTheme.colors.background
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
                                note,
                                accountViewModel.userProfile()
                            )?.let { navController.navigate(it) }
                        }
                    },
                    onLongClick = { popupExpanded = true }
                )
                .fillMaxWidth()
        }

        Column(columnModifier) {
            Row(Modifier.fillMaxWidth()) {
                Box(modifier = remember { Modifier.width(55.dp).padding(top = 5.dp, end = 5.dp) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_dm),
                        null,
                        modifier = remember { Modifier.size(16.dp).align(Alignment.TopEnd) },
                        tint = MaterialTheme.colors.primary
                    )
                }

                Column(modifier = remember { Modifier.padding(start = 10.dp) }) {
                    NoteCompose(
                        baseNote = messageSetCard.note,
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
