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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.sno_object_details
import com.vitorpamplona.amethyst.commons.resources.sno_object_title
import com.vitorpamplona.amethyst.commons.resources.sno_object_unreadable
import com.vitorpamplona.amethyst.commons.sno.ui.SnoThumbnail
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoPayload
import org.jetbrains.compose.resources.stringResource

private val THUMBNAIL_SIZE = 96.dp

/**
 * A Simple Nostr Object in a feed or a thread (DECK-0003 kind 33331).
 *
 * The object is the event, so there is nothing to fetch and nothing to expand:
 * the card draws what it already has. Tapping opens it where it can be turned.
 */
@Composable
fun SnoObjectCard(
    payload: SnoPayload,
    eventId: String,
    onClick: (() -> Unit)? = null,
) {
    val modifier = MaterialTheme.colorScheme.replyModifier.padding(10.dp)

    Row(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SnoThumbnail(
            payload = payload,
            eventId = eventId,
            size = THUMBNAIL_SIZE,
            contentDescription = payload.name.ifBlank { null },
        )

        Spacer(Modifier.width(12.dp))

        Column {
            Text(
                text = payload.name.ifBlank { stringResource(Res.string.sno_object_title) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(Res.string.sno_object_details, payload.vertexCount, payload.faceCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 1,
            )
        }
    }
}

/**
 * What a client shows instead of an object it may not draw.
 *
 * DECK-0003 §3.1: a client that receives an event whose content fails §1.9 MUST
 * NOT render it and SHOULD say why rather than failing silently — hence the
 * rule number, which is the whole reason the parser hands one back.
 */
@Composable
fun SnoObjectUnreadableCard(rule: String) {
    Column(MaterialTheme.colorScheme.replyModifier.padding(10.dp)) {
        Text(
            text = stringResource(Res.string.sno_object_unreadable, rule),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.placeholderText,
        )
    }
}
