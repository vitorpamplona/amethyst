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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.marmotGroup.send

import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.marmot.mip04EncryptedMedia.buildMip04IMetaTag
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Sends uploaded MIP-04 encrypted media as Marmot group messages.
 * Each upload result becomes a separate kind:9 message with an imeta tag.
 */
class MarmotFileSender(
    val nostrGroupId: HexKey,
    val accountViewModel: AccountViewModel,
) {
    suspend fun send(uploads: List<Mip04UploadResult>) {
        for (upload in uploads) {
            val imeta =
                buildMip04IMetaTag(
                    url = upload.url,
                    mimeType = upload.mimeType,
                    filename = upload.filename,
                    originalFileHash = upload.originalFileHash,
                    nonce = upload.nonce,
                    dimensions = upload.dimensions,
                    blurhash = upload.blurhash,
                    thumbhash = upload.thumbhash,
                )

            accountViewModel.sendMarmotGroupMediaMessage(
                nostrGroupId = nostrGroupId,
                url = upload.url,
                imeta = imeta,
                caption = upload.caption,
            )
        }
    }
}
