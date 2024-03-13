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

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.components.CreateClickableTextWithEmoji
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.lessImportantLink
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun ReplyInformationChannel(
    replyTo: ImmutableList<Note>?,
    mentions: ImmutableList<String>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    var sortedMentions by remember { mutableStateOf<ImmutableList<User>>(persistentListOf()) }

    LaunchedEffect(Unit) {
        accountViewModel.loadMentions(mentions) { newSortedMentions ->
            if (newSortedMentions != sortedMentions) {
                sortedMentions = newSortedMentions
            }
        }
    }

    if (sortedMentions.isNotEmpty()) {
        ReplyInformationChannel(
            replyTo,
            sortedMentions,
            onUserTagClick = { nav("User/${it.pubkeyHex}") },
        )
        Spacer(modifier = StdVertSpacer)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReplyInformationChannel(
    replyTo: ImmutableList<Note>?,
    mentions: ImmutableList<User>?,
    prefix: String = "",
    onUserTagClick: (User) -> Unit,
) {
    FlowRow {
        if (mentions != null && mentions.isNotEmpty()) {
            if (replyTo != null && replyTo.isNotEmpty()) {
                Text(
                    stringResource(id = R.string.replying_to),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.placeholderText,
                )

                mentions.forEachIndexed { idx, user ->
                    ReplyInfoMention(user, prefix, onUserTagClick)

                    if (idx < mentions.size - 2) {
                        Text(
                            ", ",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    } else if (idx < mentions.size - 1) {
                        Text(
                            " ${stringResource(id = R.string.and)} ",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyInfoMention(
    user: User,
    prefix: String,
    onUserTagClick: (User) -> Unit,
) {
    val innerUserState by user.live().userMetadataInfo.observeAsState()

    CreateClickableTextWithEmoji(
        clickablePart = "$prefix${innerUserState?.bestName()}",
        tags = innerUserState?.tags,
        style =
            LocalTextStyle.current.copy(
                color = MaterialTheme.colorScheme.lessImportantLink,
                fontSize = 13.sp,
            ),
        onClick = { onUserTagClick(user) },
    )
}
