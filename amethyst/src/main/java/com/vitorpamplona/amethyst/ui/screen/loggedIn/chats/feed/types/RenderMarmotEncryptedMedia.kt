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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.EncryptedMediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.EncryptedMediaUrlVideo
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.components.ZoomableContentView
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.marmot.mip04EncryptedMedia.Mip04Cipher
import com.vitorpamplona.quartz.marmot.mip04EncryptedMedia.Mip04MediaMeta
import com.vitorpamplona.quartz.marmot.mip04EncryptedMedia.toMip04MediaMeta
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import kotlinx.collections.immutable.persistentListOf

/**
 * Check if a Note's event has MIP-04 encrypted media imeta tags.
 */
fun hasMip04Media(event: Event?): Boolean {
    if (event == null) return false
    val imetas = event.imetas()
    return imetas.any { it.toMip04MediaMeta() != null }
}

/**
 * Renders MIP-04 encrypted media in a Marmot group chat message.
 *
 * Parses the imeta tags from the event, derives the decryption cipher
 * from the MLS exporter secret, registers it in the encryption key cache
 * so the [EncryptedBlobInterceptor] can decrypt on download, and displays
 * the media using the standard [ZoomableContentView].
 */
@Composable
fun RenderMarmotEncryptedMedia(
    note: Note,
    bgColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = note.event ?: return
    val imetas = remember(event) { event.imetas() }
    val mip04Meta = remember(imetas) { imetas.firstNotNullOfOrNull { it.toMip04MediaMeta() } }

    if (mip04Meta == null) {
        RenderDecryptionError(note, bgColor, accountViewModel, nav)
        return
    }

    // Find the nostrGroupId via the reverse index in MarmotGroupList
    val nostrGroupId = remember(note) { findGroupIdForNote(note, accountViewModel) }
    val exporterSecret =
        remember(nostrGroupId) {
            nostrGroupId?.let { accountViewModel.marmotMediaExporterSecret(it) }
        }

    if (exporterSecret == null) {
        RenderDecryptionError(note, bgColor, accountViewModel, nav)
        return
    }

    RenderMip04Content(note, mip04Meta, exporterSecret, bgColor, accountViewModel, nav)
}

@Composable
private fun RenderMip04Content(
    note: Note,
    meta: Mip04MediaMeta,
    exporterSecret: ByteArray,
    bgColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Register the MIP-04 cipher in the encryption key cache so the
    // EncryptedBlobInterceptor can decrypt the downloaded blob.
    val cipher =
        remember(meta) {
            Mip04Cipher(
                exporterSecret = exporterSecret,
                nonce = meta.nonceBytes,
                originalFileHash = meta.originalFileHashBytes,
                mimeType = meta.mimeType,
                filename = meta.filename,
            )
        }
    Amethyst.instance.keyCache.add(meta.url, cipher, meta.mimeType)

    val description = note.event?.alt()
    val isImage = meta.mimeType.startsWith("image/") || RichTextParser.isImageUrl(meta.url)
    val dim = meta.dimensions?.let { DimensionTag.parse(it) }

    val content by remember(meta) {
        mutableStateOf<BaseMediaContent>(
            if (isImage) {
                EncryptedMediaUrlImage(
                    url = meta.url,
                    description = description,
                    hash = meta.originalFileHash,
                    blurhash = meta.blurhash,
                    dim = dim,
                    uri = note.toNostrUri(),
                    mimeType = meta.mimeType,
                    encryptionAlgo = meta.version,
                    encryptionKey = exporterSecret,
                    encryptionNonce = meta.nonceBytes,
                )
            } else {
                EncryptedMediaUrlVideo(
                    url = meta.url,
                    description = description,
                    hash = meta.originalFileHash,
                    blurhash = meta.blurhash,
                    dim = dim,
                    uri = note.toNostrUri(),
                    authorName = note.author?.toBestDisplayName(),
                    mimeType = meta.mimeType,
                    encryptionAlgo = meta.version,
                    encryptionKey = exporterSecret,
                    encryptionNonce = meta.nonceBytes,
                )
            },
        )
    }

    ZoomableContentView(
        content,
        persistentListOf(content),
        roundedCorner = true,
        contentScale = ContentScale.FillWidth,
        accountViewModel,
    )
}

@Composable
private fun RenderDecryptionError(
    note: Note,
    bgColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    TranslatableRichTextViewer(
        content = stringRes(id = R.string.could_not_decrypt_the_message),
        canPreview = true,
        quotesLeft = 0,
        modifier = Modifier,
        tags = EmptyTagList,
        backgroundColor = bgColor,
        id = note.idHex,
        callbackUri = note.toNostrUri(),
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

/**
 * Finds the Marmot group ID for a note via the reverse index
 * maintained by [MarmotGroupList].
 */
private fun findGroupIdForNote(
    note: Note,
    accountViewModel: AccountViewModel,
): String? = accountViewModel.account.marmotGroupList.groupIdForNote(note.idHex)
