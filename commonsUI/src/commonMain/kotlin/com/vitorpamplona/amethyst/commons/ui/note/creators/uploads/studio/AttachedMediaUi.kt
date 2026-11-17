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
package com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.studio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.media_studio_done
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.ui.note.creators.messagefield.MediaBlocks
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * The media studio over a composer with an [AttachedMediaState]: right after a pick (Cancel undoes
 * it, Add puts the files in the text) and when a file in the text is tapped (edits apply as they're
 * made, Done closes). Put it anywhere in the composer's screen.
 */
@Composable
fun AttachedMediaStudioDialogs(
    state: AttachedMediaState,
    isUploading: Boolean,
    accountViewModel: AccountViewModel,
) {
    val defaultServer = state.server ?: accountViewModel.account.settings.defaultFileServer

    state.picking?.let { picked ->
        MediaStudioDialog(
            media = picked,
            defaultServer = defaultServer,
            isUploading = false,
            onAdd = { server ->
                state.attachPicked(server)
                accountViewModel.account.settings.changeDefaultFileServer(server)
            },
            onRemove = state::removePicked,
            onCancel = { state.picking = null },
            accountViewModel = accountViewModel,
        )
    }

    state.reopened?.let { item ->
        val batch = remember(item) { MultiOrchestrator.of(state.inOrder()) }
        MediaStudioDialog(
            media = batch,
            defaultServer = defaultServer,
            isUploading = isUploading,
            onAdd = { server ->
                state.server = server
                state.reopened = null
            },
            onRemove = { removed ->
                batch.remove(removed)
                state.remove(removed)
                if (batch.size() == 0) state.reopened = null
            },
            onCancel = { state.reopened = null },
            accountViewModel = accountViewModel,
            initial = item,
            confirmLabel = Res.string.media_studio_done,
            canCancel = false,
        )
    }
}

/** The cards a composer's text field draws for [state]'s files: pass to `MessageField` or `rememberMediaMarkerField`. */
@Composable
fun rememberAttachedMediaBlocks(
    state: AttachedMediaState,
    /** Draws each video's first frame to be sent, where the platform can decode one. */
    uploader: MediaUploader? = null,
    isUploading: () -> Boolean,
): MediaBlocks =
    remember(state, uploader) {
        MediaBlocks(
            aspectRatio = { id -> state.items[id]?.sentAspectRatio },
            // Sound and documents have no picture to show: a row with the name is enough.
            fixedHeight = { id ->
                state.items[id]
                    ?.media
                    ?.takeIf { it.isImage() != true && it.isVideo() != true }
                    ?.let { FILE_CARD_HEIGHT }
            },
        ) { id, selected, modifier ->
            val item = state.items[id]
            if (item != null) {
                AttachedMediaCard(
                    item = item,
                    uploader = uploader,
                    selected = selected,
                    locked = isUploading(),
                    onOpen = { state.reopened = item },
                    onRemove = { state.remove(item) },
                    modifier = modifier,
                )
            } else {
                MissingMediaCard(onRemove = { state.removeMarker(id) }, modifier = modifier)
            }
        }
    }
