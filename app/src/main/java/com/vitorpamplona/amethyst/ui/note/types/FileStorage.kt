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

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.MediaLocalImage
import com.vitorpamplona.amethyst.commons.richtext.MediaLocalVideo
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.components.ZoomableContentView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.events.FileStorageHeaderEvent
import java.io.File

@Composable
fun FileStorageHeaderDisplay(
    baseNote: Note,
    roundedCorner: Boolean,
    accountViewModel: AccountViewModel,
) {
    val eventHeader = (baseNote.event as? FileStorageHeaderEvent) ?: return
    val dataEventId = eventHeader.dataEventId() ?: return

    LoadNote(baseNoteHex = dataEventId, accountViewModel) { contentNote ->
        if (contentNote != null) {
            ObserverAndRenderNIP95(baseNote, contentNote, roundedCorner, accountViewModel)
        }
    }
}

@Composable
private fun ObserverAndRenderNIP95(
    header: Note,
    content: Note,
    roundedCorner: Boolean,
    accountViewModel: AccountViewModel,
) {
    val eventHeader = (header.event as? FileStorageHeaderEvent) ?: return

    val appContext = LocalContext.current.applicationContext

    val noteState by content.live().metadata.observeAsState()

    val content by
        remember(noteState) {
            // Creates a new object when the event arrives to force an update of the image.
            val note = noteState?.note
            val uri = header.toNostrUri()
            val localDir = note?.idHex?.let { File(File(appContext.cacheDir, "NIP95"), it) }
            val blurHash = eventHeader.blurhash()
            val dimensions = eventHeader.dimensions()
            val description = eventHeader.alt() ?: eventHeader.content
            val mimeType = eventHeader.mimeType()

            val newContent =
                if (mimeType?.startsWith("image") == true) {
                    MediaLocalImage(
                        localFile = localDir,
                        mimeType = mimeType,
                        description = description,
                        dim = dimensions,
                        blurhash = blurHash,
                        isVerified = true,
                        uri = uri,
                    )
                } else {
                    MediaLocalVideo(
                        localFile = localDir,
                        mimeType = mimeType,
                        description = description,
                        dim = dimensions,
                        isVerified = true,
                        uri = uri,
                        authorName = header.author?.toBestDisplayName(),
                    )
                }

            mutableStateOf<BaseMediaContent?>(newContent)
        }

    Crossfade(targetState = content) {
        if (it != null) {
            SensitivityWarning(note = header, accountViewModel = accountViewModel) {
                ZoomableContentView(
                    content = it,
                    roundedCorner = roundedCorner,
                    accountViewModel = accountViewModel,
                )
            }
        }
    }
}
