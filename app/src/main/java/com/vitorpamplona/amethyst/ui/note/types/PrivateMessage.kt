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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.routeFor
import com.vitorpamplona.amethyst.ui.note.LoadDecryptedContent
import com.vitorpamplona.amethyst.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ChatroomHeader
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.events.ChatroomKeyable
import com.vitorpamplona.quartz.events.EmptyTagList
import com.vitorpamplona.quartz.events.PrivateDmEvent
import com.vitorpamplona.quartz.events.toImmutableListOfLists
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable
fun RenderPrivateMessage(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteEvent = note.event as? PrivateDmEvent ?: return

    val userRoom by
        remember(note) {
            derivedStateOf {
                (note.event as? ChatroomKeyable)?.chatroomKey(accountViewModel.userProfile().pubkeyHex)
            }
        }

    userRoom?.let {
        if (it.users.size > 1 || (it.users.size == 1 && note.author == accountViewModel.account.userProfile())) {
            ChatroomHeader(it, MaterialTheme.colorScheme.replyModifier.padding(10.dp), accountViewModel) {
                routeFor(note, accountViewModel.userProfile())?.let {
                    nav(it)
                }
            }
            Spacer(modifier = StdVertSpacer)
        }
    }

    val withMe = remember { noteEvent.with(accountViewModel.userProfile().pubkeyHex) }
    if (withMe) {
        LoadDecryptedContent(note, accountViewModel) { eventContent ->
            val modifier = remember(note.event?.id()) { Modifier.fillMaxWidth() }
            val isAuthorTheLoggedUser =
                remember(note.event?.id()) { accountViewModel.isLoggedUser(note.author) }

            val tags =
                remember(note) { note.event?.tags()?.toImmutableListOfLists() ?: EmptyTagList }

            if (makeItShort && isAuthorTheLoggedUser) {
                Text(
                    text = eventContent,
                    color = MaterialTheme.colorScheme.placeholderText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                SensitivityWarning(
                    note = note,
                    accountViewModel = accountViewModel,
                ) {
                    TranslatableRichTextViewer(
                        content = eventContent,
                        canPreview = canPreview && !makeItShort,
                        quotesLeft = quotesLeft,
                        modifier = modifier,
                        tags = tags,
                        backgroundColor = backgroundColor,
                        id = note.idHex,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }

                if (noteEvent.hasHashtags()) {
                    val hashtags =
                        remember(note.event?.id()) {
                            note.event?.hashtags()?.toImmutableList() ?: persistentListOf()
                        }
                    DisplayUncitedHashtags(hashtags, eventContent, nav)
                }
            }
        }
    } else {
        val recipient = noteEvent.recipientPubKeyBytes()?.toNpub() ?: "Someone"

        TranslatableRichTextViewer(
            stringResource(
                id = R.string.private_conversation_notification,
                "@${note.author?.pubkeyNpub()}",
                "@$recipient",
            ),
            canPreview = !makeItShort,
            quotesLeft = 0,
            modifier = Modifier.fillMaxWidth(),
            tags = EmptyTagList,
            backgroundColor = backgroundColor,
            id = note.idHex,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}
