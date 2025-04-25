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

import android.content.Context
import android.util.Log
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.LifecycleOwner
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNote
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserInfo
import com.vitorpamplona.amethyst.service.tts.TextToSpeechHelper
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.StdButtonSizeModifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip02FollowList.ImmutableListOfLists

@Composable
fun NoteUsernameDisplay(
    baseNote: Note,
    weight: Modifier = Modifier,
    textColor: Color = Color.Unspecified,
    accountViewModel: AccountViewModel,
) {
    WatchAuthor(baseNote) {
        UsernameDisplay(it, weight, textColor = textColor, accountViewModel = accountViewModel)
    }
}

@Composable
fun WatchAuthor(
    baseNote: Note,
    inner: @Composable (User) -> Unit,
) {
    val noteAuthor = baseNote.author
    if (noteAuthor != null) {
        inner(noteAuthor)
    } else {
        val authorState by observeNote(baseNote)
        authorState?.note?.author?.let {
            inner(it)
        }
    }
}

@Composable
fun WatchAuthorWithBlank(
    baseNote: Note,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    inner: @Composable (User?) -> Unit,
) {
    val noteAuthor = baseNote.author
    if (noteAuthor != null) {
        inner(noteAuthor)
    } else {
        val authorState by observeNote(baseNote)
        CrossfadeIfEnabled(targetState = authorState?.note?.author, modifier = modifier, label = "WatchAuthorWithBlank", accountViewModel = accountViewModel) { newAuthor ->
            inner(newAuthor)
        }
    }
}

@Composable
fun UsernameDisplay(
    baseUser: User,
    weight: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Bold,
    textColor: Color = Color.Unspecified,
    accountViewModel: AccountViewModel,
) {
    val userMetadata by observeUserInfo(baseUser)

    CrossfadeIfEnabled(targetState = userMetadata, modifier = weight, label = "UsernameDisplay", accountViewModel = accountViewModel) {
        val name = it?.bestName()
        if (name != null) {
            UserDisplay(name, it.tags, weight, fontWeight, textColor)
        } else {
            NPubDisplay(baseUser, weight, fontWeight, textColor)
        }
    }
}

@Composable
private fun NPubDisplay(
    user: User,
    modifier: Modifier,
    fontWeight: FontWeight = FontWeight.Bold,
    textColor: Color = Color.Unspecified,
) {
    Text(
        text = remember { user.pubkeyDisplayHex() },
        fontWeight = fontWeight,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = textColor,
    )
}

@Composable
private fun UserDisplay(
    bestDisplayName: String,
    tags: ImmutableListOfLists<String>?,
    modifier: Modifier,
    fontWeight: FontWeight = FontWeight.Bold,
    textColor: Color = Color.Unspecified,
) {
    CreateTextWithEmoji(
        text = bestDisplayName,
        tags = tags,
        fontWeight = fontWeight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
        color = textColor,
    )
}

@Composable
fun DrawPlayName(name: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    IconButton(onClick = { speak(name, context, lifecycleOwner) }, modifier = StdButtonSizeModifier) {
        PlayIcon(
            modifier = StdButtonSizeModifier,
            tint = MaterialTheme.colorScheme.placeholderText,
        )
    }
}

private fun speak(
    message: String,
    context: Context,
    owner: LifecycleOwner,
) {
    TextToSpeechHelper
        .getInstance(context)
        .registerLifecycle(owner)
        .speak(message)
        .highlight()
        .onDone { Log.d("TextToSpeak", "speak: done") }
        .onError { Log.d("TextToSpeak", "speak error: $it") }
}
