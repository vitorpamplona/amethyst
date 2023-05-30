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
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.NotificationCache
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.screen.BadgeCard
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.newItemBackgroundColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BadgeCompose(likeSetCard: BadgeCard, isInnerNote: Boolean = false, routeForLastRead: String, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val noteState by likeSetCard.note.live().metadata.observeAsState()
    val note = noteState?.note

    val context = LocalContext.current.applicationContext

    var popupExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (note == null) {
        BlankNote(Modifier, isInnerNote)
    } else {
        var isNew by remember { mutableStateOf<Boolean>(false) }

        LaunchedEffect(key1 = likeSetCard) {
            scope.launch(Dispatchers.IO) {
                isNew = likeSetCard.createdAt() > NotificationCache.load(routeForLastRead)

                NotificationCache.markAsRead(routeForLastRead, likeSetCard.createdAt())
            }
        }

        val backgroundColor = if (isNew) {
            MaterialTheme.colors.newItemBackgroundColor.compositeOver(MaterialTheme.colors.background)
        } else {
            MaterialTheme.colors.background
        }

        Column(
            modifier = Modifier
                .background(backgroundColor)
                .combinedClickable(
                    onClick = {
                        scope.launch {
                            routeFor(
                                note,
                                accountViewModel.userProfile()
                            )?.let { nav(it) }
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
                            .padding(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MilitaryTech,
                            null,
                            modifier = Modifier
                                .size(25.dp)
                                .align(Alignment.TopEnd),
                            tint = MaterialTheme.colors.primary
                        )
                    }
                }

                Column(modifier = Modifier.padding(start = if (!isInnerNote) 10.dp else 0.dp)) {
                    Row() {
                        Text(
                            stringResource(R.string.new_badge_award_notif),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(bottom = 5.dp)
                                .weight(1f)
                        )

                        Text(
                            timeAgo(note.createdAt(), context = context),
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                            maxLines = 1
                        )

                        IconButton(
                            modifier = Modifier.then(Modifier.size(24.dp)),
                            onClick = { popupExpanded = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )

                            NoteDropDownMenu(note, popupExpanded, { popupExpanded = false }, accountViewModel)
                        }
                    }

                    note.replyTo?.firstOrNull()?.let {
                        NoteCompose(
                            baseNote = it,
                            routeForLastRead = null,
                            isBoostedNote = true,
                            parentBackgroundColor = backgroundColor,
                            accountViewModel = accountViewModel,
                            nav = nav
                        )
                    }

                    Divider(
                        modifier = Modifier.padding(top = 10.dp),
                        thickness = 0.25.dp
                    )
                }
            }
        }
    }
}
