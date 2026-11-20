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
package com.vitorpamplona.amethyst.commons.ui.actions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.vitorpamplona.amethyst.commons.model.VideoPostKind
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.post
import com.vitorpamplona.amethyst.commons.service.upload.ui.StrippingFailureDialog
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.studio.MediaStudioDialog
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.collections.immutable.ImmutableList

/**
 * A picture or video post (NIP-68 / NIP-71): the picked files in the media studio, each with its
 * own quality, alt text, content warning and metadata strip, plus the post's caption. Post uploads
 * them and publishes; a file that fails stays in the studio with its reason, and Post sends only
 * what failed. Opened by the Pictures, Shorts, Videos and Longs feeds and their share targets.
 */
@Composable
fun NewMediaView(
    uris: ImmutableList<SelectedMedia>,
    onClose: () -> Unit,
    postViewModel: NewMediaModel,
    accountViewModel: AccountViewModel,
    @Suppress("UNUSED_PARAMETER") nav: INav,
    videoKind: VideoPostKind = VideoPostKind.AUTO,
    initialCaption: String = "",
) {
    val account = accountViewModel.account

    LaunchedEffect(uris, videoKind, initialCaption) {
        postViewModel.load(account, uris, videoKind, initialCaption)
    }

    StrippingFailureDialog(postViewModel.strippingFailureConfirmation)

    val media = postViewModel.multiOrchestrator ?: return
    MediaStudioDialog(
        media = media,
        defaultServer = postViewModel.selectedServer ?: account.settings.defaultFileServer,
        isUploading = postViewModel.isUploadingImage,
        onAdd = { server ->
            postViewModel.selectedServer = server
            // A failure shows on the file, in the studio, with its reason: no toast on top.
            postViewModel.upload(accountViewModel.mediaUploader, accountViewModel, onClose) { _, _ -> }
            account.settings.changeDefaultFileServer(server)
        },
        onRemove = { removed ->
            postViewModel.deleteMediaToUpload(removed)
            if (postViewModel.multiOrchestrator == null) onClose()
        },
        onCancel = {
            postViewModel.cancelModel()
            onClose()
        },
        accountViewModel = accountViewModel,
        confirmLabel = Res.string.post,
        caption = postViewModel.caption,
        onCaptionChange = { postViewModel.caption = it },
    )
}
