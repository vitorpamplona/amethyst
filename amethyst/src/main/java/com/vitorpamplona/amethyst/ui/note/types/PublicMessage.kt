/**
 * Copyright (c) 2025 Vitor Pamplona
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

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.components.RenderUserAsClickableText
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav.nav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.HalfHalfTopPadding
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.experimental.publicMessages.PublicMessageEvent
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hasHashtags
import com.vitorpamplona.quartz.nip02FollowList.toImmutableListOfLists

@Composable
fun RenderPublicMessage(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? PublicMessageEvent ?: return
    val content = remember(noteEvent) { noteEvent.peopleAndContent() }

    if (makeItShort && accountViewModel.isLoggedUser(note.author)) {
        Text(
            text = content,
            color = MaterialTheme.colorScheme.placeholderText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        val callbackUri = remember(note) { note.toNostrUri() }

        SensitivityWarning(
            note = note,
            accountViewModel = accountViewModel,
        ) {
            TranslatableRichTextViewer(
                content = content,
                canPreview = canPreview && !makeItShort,
                quotesLeft = quotesLeft,
                modifier = Modifier.fillMaxWidth(),
                tags = remember(noteEvent) { noteEvent.tags.toImmutableListOfLists() },
                backgroundColor = backgroundColor,
                id = note.idHex,
                callbackUri = callbackUri,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        if (noteEvent.hasHashtags()) {
            DisplayUncitedHashtags(noteEvent, noteEvent.content, callbackUri, accountViewModel, nav)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayUncitedUsers(
    event: PublicMessageEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    @Suppress("ProduceStateDoesNotAssignValue")
    val uncitedUsers by produceState(initialValue = emptyList<User>()) {
        val users = event.groupKeySetWithoutOwner() - event.citedUsers()
        if (users.isNotEmpty()) {
            val newUsers = accountViewModel.loadUsersSync(users.toList())

            if (newUsers.isNotEmpty()) {
                value = newUsers
            }
        }
    }

    if (uncitedUsers.isNotEmpty()) {
        FlowRow(HalfHalfTopPadding) {
            uncitedUsers.forEach { user ->
                RenderUserAsClickableText(user, "", accountViewModel, nav)
            }
        }
    }
}
