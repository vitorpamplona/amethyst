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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.playback.composable.VideoView
import com.vitorpamplona.amethyst.service.playback.composable.WaveformData
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hasHashtags
import com.vitorpamplona.quartz.nip14Subject.subject
import com.vitorpamplona.quartz.nipA0VoiceMessages.BaseVoiceEvent

@Composable
fun RenderVoiceTrack(
    note: Note,
    contentScale: ContentScale,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? BaseVoiceEvent ?: return

    AudioVoiceHeader(noteEvent, note, contentScale, accountViewModel, nav)
}

@Composable
fun AudioVoiceHeader(
    noteEvent: BaseVoiceEvent,
    note: Note,
    contentScale: ContentScale,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val waveform =
        remember(noteEvent) {
            noteEvent
                .iMetaTags()
                .firstOrNull()
                ?.waveform
                ?.let { WaveformData(it) }
        }
    val media =
        remember(noteEvent) {
            noteEvent.content.ifBlank { null } ?: noteEvent.iMetaTags().firstOrNull()?.url
        }

    if (media == null) return

    Column(modifier = Modifier.fillMaxWidth().padding(top = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VideoView(
                videoUri = media,
                mimeType = null,
                waveform = waveform,
                title = noteEvent.subject(),
                authorName = note.author?.toBestDisplayName(),
                roundedCorner = true,
                contentScale = contentScale,
                accountViewModel = accountViewModel,
                nostrUriCallback = note.toNostrUri(),
            )
        }

        val callbackUri = remember(note) { note.toNostrUri() }

        if (noteEvent.hasHashtags()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DisplayUncitedHashtags(noteEvent, callbackUri, accountViewModel, nav)
            }
        }
    }
}
