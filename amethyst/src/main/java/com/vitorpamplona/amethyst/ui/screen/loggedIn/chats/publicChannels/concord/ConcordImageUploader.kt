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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord

import android.content.Context
import android.net.Uri
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.uploads.blossom.BlossomUploader
import com.vitorpamplona.amethyst.ui.actions.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerType
import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.ciphers.AESGCM
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

/**
 * Authors a CORD-02 §6 encrypted community image: AES-256-GCM-encrypts the plaintext under a fresh
 * random key/nonce (same scheme as NIP-17 DM encrypted media), uploads the *ciphertext* as an opaque
 * blob to the account's Blossom server, and returns the [ImagePointer] to seal in the community
 * metadata — the exact inverse of [rememberConcordImageModel]'s read path. Mirrors Armada's
 * `encryptImageBlob` + Blossom upload in `concord-v2/lib/image.ts`.
 */
class ConcordImageUploader(
    private val account: Account,
) {
    suspend fun uploadEncrypted(
        plaintext: ByteArray,
        context: Context,
    ): ImagePointer {
        val serverBaseUrl =
            account.blossomServers
                .getBlossomServersList()
                ?.servers()
                ?.firstOrNull()
                ?: DEFAULT_MEDIA_SERVERS.first { it.type == ServerType.Blossom }.baseUrl

        val cipher = AESGCM()
        val ciphertext = cipher.encrypt(plaintext)

        val result =
            withContext(Dispatchers.IO) {
                BlossomUploader().upload(
                    // The blob is content-addressed by the SHA-256 of the *uploaded* (encrypted) bytes;
                    // the pointer's own hash below is over the *plaintext* for integrity on read.
                    inputStream = ByteArrayInputStream(ciphertext),
                    hash = sha256(ciphertext).toHexKey(),
                    length = ciphertext.size.toLong(),
                    baseFileName = "concord-image",
                    contentType = "application/octet-stream",
                    alt = "Encrypted Concord community image",
                    sensitiveContent = null,
                    serverBaseUrl = serverBaseUrl,
                    okHttpClient = Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForUploads,
                    httpAuth = account::createBlossomUploadAuth,
                    context = context,
                )
            }

        val url = result.url ?: throw IllegalStateException("Blossom upload returned no URL")
        return ImagePointer(
            url = url,
            key = cipher.keyBytes.toHexKey(),
            nonce = cipher.nonce.toHexKey(),
            hash = sha256(plaintext).toHexKey(),
        )
    }

    /** Reads the picked [uri]'s bytes then [uploadEncrypted]s them. */
    suspend fun uploadEncrypted(
        uri: Uri,
        context: Context,
    ): ImagePointer {
        val bytes =
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } ?: throw IllegalStateException("Could not read the selected image")
        return uploadEncrypted(bytes, context)
    }
}
