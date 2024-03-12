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
package com.vitorpamplona.amethyst.ui.note.elements

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.CreateClickableTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.navigation.routeFor
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.nip05
import com.vitorpamplona.quartz.events.BaseTextNoteEvent

@Composable
fun ShowForkInformation(
    noteEvent: BaseTextNoteEvent,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val forkedAddress = remember(noteEvent) { noteEvent.forkFromAddress() }
    val forkedEvent = remember(noteEvent) { noteEvent.forkFromVersion() }
    if (forkedAddress != null) {
        LoadAddressableNote(
            aTag = forkedAddress,
            accountViewModel = accountViewModel,
        ) { addressableNote ->
            if (addressableNote != null) {
                ForkInformationRowLightColor(addressableNote, modifier, accountViewModel, nav)
            }
        }
    } else if (forkedEvent != null) {
        LoadNote(forkedEvent, accountViewModel = accountViewModel) { event ->
            if (event != null) {
                ForkInformationRowLightColor(event, modifier, accountViewModel, nav)
            }
        }
    }
}

@Composable
fun ForkInformationRowLightColor(
    originalVersion: Note,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteState by originalVersion.live().metadata.observeAsState()
    val note = noteState?.note ?: return
    val author = note.author ?: return
    val route = remember(note) { routeFor(note, accountViewModel.userProfile()) }

    if (route != null) {
        Row(modifier) {
            ClickableText(
                text =
                    buildAnnotatedString {
                        append(stringResource(id = R.string.forked_from))
                        append(" ")
                    },
                onClick = { nav(route) },
                style =
                    LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.nip05,
                        fontSize = Font14SP,
                    ),
                maxLines = 1,
                overflow = TextOverflow.Visible,
            )

            val userState by author.live().metadata.observeAsState()
            val userDisplayName = remember(userState) { userState?.user?.toBestDisplayName() }
            val userTags =
                remember(userState) { userState?.user?.info?.tags }

            if (userDisplayName != null) {
                CreateClickableTextWithEmoji(
                    clickablePart = userDisplayName,
                    maxLines = 1,
                    route = route,
                    overrideColor = MaterialTheme.colorScheme.nip05,
                    fontSize = Font14SP,
                    nav = nav,
                    tags = userTags,
                )
            }
        }
    }
}

@Composable
fun ForkInformationRow(
    originalVersion: Note,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteState by originalVersion.live().metadata.observeAsState()
    val note = noteState?.note ?: return
    val route = remember(note) { routeFor(note, accountViewModel.userProfile()) }

    if (route != null) {
        Row(modifier) {
            val author = note.author ?: return
            val meta by author.live().userMetadataInfo.observeAsState(author.info)

            Text(stringResource(id = R.string.forked_from))
            Spacer(modifier = StdHorzSpacer)

            val userMetadata by author.live().userMetadataInfo.observeAsState()

            CreateClickableTextWithEmoji(
                clickablePart = remember(meta) { meta?.bestName() ?: author.pubkeyDisplayHex() },
                maxLines = 1,
                route = route,
                nav = nav,
                tags = userMetadata?.tags,
            )
        }
    }
}
