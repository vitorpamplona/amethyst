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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.sno.ui.SnoObjectViewer
import com.vitorpamplona.amethyst.commons.ui.note.SnoObjectCard
import com.vitorpamplona.amethyst.commons.ui.note.SnoObjectUnreadableCard
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoResult
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoShardEvent

private val VIEWER_HEIGHT = 360.dp

/**
 * Entry for a shard: an object someone hid at a place (DECK-0003 §3.2, kind
 * 3330).
 *
 * The same payload and the same rules as a standalone object — only the
 * container differs — so this renders through the same card. What it does not
 * do is open a bag: a shard still sealed in its `kind 33330` bag is ciphertext
 * until its region key is derived, which is the cyberspace protocol rather than
 * a renderer.
 */
@Composable
fun RenderSnoShard(baseNote: Note) {
    val noteEvent = baseNote.event as? SnoShardEvent ?: return
    val parsed = remember(noteEvent) { noteEvent.shard() }

    when (parsed) {
        is SnoResult.Invalid -> SnoObjectUnreadableCard(parsed.rule)
        is SnoResult.Valid -> {
            var turning by remember(noteEvent) { mutableStateOf(false) }

            SnoObjectCard(
                payload = parsed.payload,
                eventId = noteEvent.id,
                onClick = { turning = true },
            )

            if (turning) {
                Dialog(onDismissRequest = { turning = false }) {
                    SnoObjectViewer(
                        payload = parsed.payload,
                        eventId = noteEvent.id,
                        contentDescription = parsed.payload.name.ifBlank { null },
                        modifier = Modifier.fillMaxWidth().height(VIEWER_HEIGHT),
                    )
                }
            }
        }
    }
}
