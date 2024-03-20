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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.navigation.routeFor
import com.vitorpamplona.amethyst.ui.note.elements.NoteDropDownMenu
import com.vitorpamplona.amethyst.ui.note.types.BadgeDisplay
import com.vitorpamplona.amethyst.ui.screen.BadgeCard
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Size15Modifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BadgeCompose(
    likeSetCard: BadgeCard,
    isInnerNote: Boolean = false,
    routeForLastRead: String,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteState by likeSetCard.note.live().metadata.observeAsState()
    val note = noteState?.note

    val context = LocalContext.current.applicationContext

    val popupExpanded = remember { mutableStateOf(false) }
    val enablePopup = remember { { popupExpanded.value = true } }

    val scope = rememberCoroutineScope()

    if (note == null) {
        BlankNote(Modifier)
    } else {
        val backgroundColor =
            calculateBackgroundColor(
                createdAt = likeSetCard.createdAt(),
                routeForLastRead = routeForLastRead,
                accountViewModel = accountViewModel,
            )

        Column(
            modifier =
                Modifier.background(backgroundColor.value)
                    .combinedClickable(
                        onClick = {
                            scope.launch {
                                routeFor(
                                    note,
                                    accountViewModel.userProfile(),
                                )
                                    ?.let { nav(it) }
                            }
                        },
                        onLongClick = enablePopup,
                    ),
        ) {
            Row(
                modifier =
                    Modifier.padding(
                        start = if (!isInnerNote) 12.dp else 0.dp,
                        end = if (!isInnerNote) 12.dp else 0.dp,
                        top = 10.dp,
                    ),
            ) {
                // Draws the like picture outside the boosted card.
                if (!isInnerNote) {
                    Box(
                        modifier = Modifier.width(55.dp).padding(0.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MilitaryTech,
                            null,
                            modifier = Modifier.size(25.dp).align(Alignment.TopEnd),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Column(modifier = Modifier.padding(start = if (!isInnerNote) 10.dp else 0.dp)) {
                    Row {
                        Text(
                            stringResource(R.string.new_badge_award_notif),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 5.dp).weight(1f),
                        )

                        Text(
                            timeAgo(note.createdAt(), context = context),
                            color = MaterialTheme.colorScheme.placeholderText,
                            maxLines = 1,
                        )

                        IconButton(
                            modifier = Modifier.then(Modifier.size(24.dp)),
                            onClick = enablePopup,
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(id = R.string.more_options),
                                modifier = Size15Modifier,
                                tint = MaterialTheme.colorScheme.placeholderText,
                            )

                            NoteDropDownMenu(note, popupExpanded, null, accountViewModel, nav)
                        }
                    }

                    note.replyTo?.firstOrNull()?.let {
                        BadgeDisplay(baseNote = it)
                    }
                }
            }
        }
    }
}
