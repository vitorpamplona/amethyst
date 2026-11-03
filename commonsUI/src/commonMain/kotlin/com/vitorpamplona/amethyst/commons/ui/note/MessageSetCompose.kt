/*
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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.notifications.MessageSetCard
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.elements.NoteDropDownMenu
import com.vitorpamplona.amethyst.commons.ui.note.elements.TimeAgo
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.rooms.ChatMessagePreviewText
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.launch

/**
 * The direct messages one chat sent the user, as a Messages-list row: the sender, a lock, the time,
 * how many arrived, and the newest one in two lines. Tapping opens the chat, where they are answered.
 * Indented keeps the picture in the author column; the full-width layouts put it in the header and
 * give the message the whole width.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageSetCompose(
    messageSetCard: MessageSetCard,
    routeForLastRead: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val baseNote = messageSetCard.note
    val popupExpanded = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val backgroundColor =
        calculateBackgroundColor(
            createdAt = messageSetCard.createdAt(),
            routeForLastRead = routeForLastRead,
            accountViewModel = accountViewModel,
        )

    val fullWidth = LocalDisplaySettings.current.fullWidthNotes

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .drawBehind { drawRect(backgroundColor.value) }
                .combinedClickable(
                    onClick = { scope.launch { routeFor(baseNote, accountViewModel.account)?.let { nav.nav(it) } } },
                    onLongClick = { popupExpanded.value = true },
                ).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (!fullWidth) {
            SenderPicture(messageSetCard, 55.dp, accountViewModel, nav)
            Spacer(Modifier.width(10.dp))
        }

        Column(Modifier.weight(1f)) {
            Row(
                modifier = Modifier.heightIn(min = if (fullWidth) 35.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (fullWidth) {
                    SenderPicture(messageSetCard, 35.dp, accountViewModel, nav)
                    Spacer(Modifier.width(10.dp))
                }
                baseNote.author?.let { UsernameDisplay(it, Modifier.weight(1f, fill = false), accountViewModel = accountViewModel) }
                Icon(
                    symbol = MaterialSymbols.Lock,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 4.dp).size(14.dp),
                    tint = MaterialTheme.colorScheme.placeholderText,
                )
                TimeAgo(baseNote)
                Spacer(Modifier.weight(1f))
                if (messageSetCard.messages.size > 1) {
                    CountPill(messageSetCard.messages.size)
                }
            }

            Spacer(Modifier.size(if (fullWidth) 6.dp else 2.dp))

            MessageBody(baseNote, backgroundColor, accountViewModel, nav)
        }
    }

    if (popupExpanded.value) {
        NoteDropDownMenu(baseNote, { popupExpanded.value = false }, null, accountViewModel, nav)
    }
}

/**
 * The newest message, written by someone else, so it renders like a note's text: translated,
 * rich and expandable. Until it decrypts (or when it can't) the Messages-list status line stands in.
 */
@Composable
private fun MessageBody(
    message: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadDecryptedContentOrNull(message, accountViewModel) { body ->
        if (!body.isNullOrBlank()) {
            val tags = remember(message) { message.event?.tags?.toImmutableListOfLists() ?: EmptyTagList }
            TranslatableRichTextViewer(
                content = body,
                canPreview = true,
                quotesLeft = 1,
                modifier = Modifier.fillMaxWidth(),
                tags = tags,
                backgroundColor = backgroundColor,
                id = message.idHex,
                callbackUri = remember(message) { message.toNostrUri() },
                accountViewModel = accountViewModel,
                nav = nav,
            )
        } else {
            ChatMessagePreviewText(message, accountViewModel) { text ->
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.placeholderText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SenderPicture(
    card: MessageSetCard,
    size: Dp,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val author = card.note.author
    if (author != null) {
        UserPicture(author, size, accountViewModel = accountViewModel, nav = nav)
    } else {
        DisplayBlankAuthor(size, accountViewModel = accountViewModel)
    }
}

@Composable
private fun CountPill(count: Int) {
    Box(
        modifier =
            Modifier
                .padding(start = 8.dp)
                .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
