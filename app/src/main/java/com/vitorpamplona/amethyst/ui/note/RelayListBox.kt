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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.ShowMoreRelaysButtonBoxModifer
import com.vitorpamplona.amethyst.ui.theme.Size17Modifier
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.noteComposeRelayBox
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.coroutines.flow.map

@Composable
fun RelayBadges(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val expanded = remember { mutableStateOf(false) }

    CrossfadeIfEnabled(expanded.value, modifier = noteComposeRelayBox, label = "RelayBadges", accountViewModel = accountViewModel) {
        if (it) {
            RenderAllRelayList(baseNote, Modifier.fillMaxWidth(), accountViewModel = accountViewModel, nav = nav)
        } else {
            Column {
                RenderClosedRelayList(baseNote, Modifier.fillMaxWidth(), accountViewModel = accountViewModel, nav = nav)
                ShouldShowExpandButton(baseNote, accountViewModel) { ShowMoreRelaysButton { expanded.value = true } }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RenderAllRelayList(
    baseNote: Note,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteRelays by baseNote
        .flow()
        .relays.stateFlow
        .collectAsStateWithLifecycle()

    FlowRow(modifier, verticalArrangement = verticalArrangement) {
        noteRelays.note.relays.forEach { RenderRelay(it, accountViewModel, nav) }
    }
}

@Composable
fun RenderClosedRelayList(
    baseNote: Note,
    modifier: Modifier = Modifier,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    Row(modifier, verticalAlignment = verticalAlignment) {
        WatchAndRenderRelay(baseNote, 0, accountViewModel, nav)
        WatchAndRenderRelay(baseNote, 1, accountViewModel, nav)
        WatchAndRenderRelay(baseNote, 2, accountViewModel, nav)
    }
}

@Composable
fun WatchAndRenderRelay(
    baseNote: Note,
    relayIndex: Int,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteRelays by baseNote
        .flow()
        .relays.stateFlow
        .map {
            it.note.relays.getOrNull(relayIndex)
        }.collectAsStateWithLifecycle(
            baseNote.relays.getOrNull(relayIndex),
        )

    CrossfadeIfEnabled(targetState = noteRelays, label = "RenderRelay", modifier = Size17Modifier, accountViewModel = accountViewModel) {
        if (it != null) {
            RenderRelay(it, accountViewModel, nav)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Preview
@Composable
fun RelayIconLayoutPreview() {
    Column(modifier = Modifier.width(55.dp)) {
        Spacer(StdVertSpacer)

        // FlowRow Seems to be a lot faster than LazyVerticalGrid
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp, end = 1.dp),
        ) {
            FlowRow {
                Box(
                    modifier = Modifier.size(17.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(13.dp)
                            .clip(shape = CircleShape)
                            .background(Color.Black),
                    )
                }
                Box(
                    modifier = Modifier.size(17.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(13.dp)
                            .clip(shape = CircleShape)
                            .background(Color.Black),
                    )
                }
                Box(
                    modifier = Modifier.size(17.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(13.dp)
                            .clip(shape = CircleShape)
                            .background(Color.Black),
                    )
                }

                Box(
                    modifier = Modifier.size(17.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(13.dp)
                            .clip(shape = CircleShape)
                            .background(Color.Black),
                    )
                }
                Box(
                    modifier = Modifier.size(17.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(13.dp)
                            .clip(shape = CircleShape)
                            .background(Color.Black),
                    )
                }
                Box(
                    modifier = Modifier.size(17.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(13.dp)
                            .clip(shape = CircleShape)
                            .background(Color.Black),
                    )
                }
            }
        }

        ShowMoreRelaysButton { }
    }
}

@Composable
private fun ShowMoreRelaysButton(onClick: () -> Unit) {
    Row(
        modifier = ShowMoreRelaysButtonBoxModifer,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Top,
    ) {
        IconButton(
            onClick = onClick,
        ) {
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = stringResource(id = R.string.expand_relay_list),
                tint = MaterialTheme.colorScheme.placeholderText,
            )
        }
    }
}
