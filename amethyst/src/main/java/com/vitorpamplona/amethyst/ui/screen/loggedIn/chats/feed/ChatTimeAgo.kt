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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.expiration_date_label
import com.vitorpamplona.amethyst.commons.resources.now
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.note.timeAgoShort
import com.vitorpamplona.amethyst.ui.note.timeAheadNoDot
import com.vitorpamplona.amethyst.ui.theme.Font12SP
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip40Expiration.expiration
import org.jetbrains.compose.resources.stringResource

@Composable
fun ChatTimeAgo(baseNote: Note) {
    val nowStr = stringResource(Res.string.now)
    val time = remember(baseNote) { timeAgoShort(baseNote.createdAt() ?: 0L, nowStr) }

    Text(
        text = time,
        color = MaterialTheme.colorScheme.placeholderText,
        fontSize = Font12SP,
        maxLines = 1,
    )
}

@Composable
fun ChatExpiration(note: Note) {
    val event = note.event
    if (event != null) {
        val expires = remember(event) { event.expiration() }
        if (expires != null) {
            ChatDisplayExpiration(expires)
        }
    }
}

@Composable
fun ChatDisplayExpiration(expirationDate: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = StdHorzSpacer)
        Icon(
            imageVector = Icons.Outlined.Timer,
            contentDescription = stringResource(Res.string.expiration_date_label),
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.placeholderText,
        )
        val context = LocalContext.current
        Text(
            text = timeAheadNoDot(expirationDate, context),
            color = MaterialTheme.colorScheme.placeholderText,
            fontSize = Font12SP,
            maxLines = 1,
        )
    }
}
