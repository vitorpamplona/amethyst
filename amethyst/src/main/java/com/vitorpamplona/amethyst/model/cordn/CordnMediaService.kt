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
package com.vitorpamplona.amethyst.model.cordn

import android.content.Context
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.uploads.blossom.BlossomUploader
import com.vitorpamplona.quartz.cordn.appEncryptedMedia.CordnBlobUpload
import com.vitorpamplona.quartz.cordn.appEncryptedMedia.CordnEncryptedMedia
import com.vitorpamplona.quartz.cordn.appEncryptedMedia.CordnMediaAttachment
import com.vitorpamplona.quartz.cordn.appEncryptedMedia.CordnMediaEncryption
import com.vitorpamplona.quartz.cordn.appEncryptedMedia.CordnMediaTag
import com.vitorpamplona.quartz.mls.group.MlsGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.ByteArrayInputStream

/**
 * Sending and fetching cordn attachments.
 *
 * ## What each party sees
 *
 * | | sees |
 * | --- | --- |
 * | the blob host | opaque bytes, their size, and who uploaded them |
 * | the coordinator | a sealed payload; not the URL, not the type, not the name |
 * | the group | everything, because they hold the epoch's exporter |
 *
 * Getting that split right is most of this file. The `imeta` descriptor —
 * URL, MIME type, filename, plaintext hash, nonce — rides **inside** the MLS
 * envelope, so it is as private as the message it belongs to.
 *
 * ## Three deliberate choices on the upload
 *
 * 1. **`application/octet-stream`, always.** The real MIME type goes in the
 *    encrypted descriptor. Declaring `image/jpeg` to the host would tell it
 *    what kind of file this is for no benefit to anyone.
 * 2. **No `alt` text, no content warning.** Both are plaintext on a Blossom
 *    upload. An alt string describing a private photo is the photo's caption,
 *    handed to a server that was supposed to see nothing.
 * 3. **`/upload`, never `/media`.** The `/media` endpoint asks the server to
 *    re-encode. Re-encoding ciphertext destroys it, and an account with
 *    "optimize uploads" on would otherwise silently break every attachment.
 *
 * Blossom addresses a blob by the SHA-256 of the bytes it stores — the
 * ciphertext. The `imeta` descriptor carries the hash of the **plaintext**.
 * Two different hashes on purpose: the host needs one to name the blob, the
 * group needs the other to know it got the file that was sent, and neither
 * can be derived from the other.
 */
class CordnMediaService(
    private val account: Account,
) {
    /**
     * Encrypts [bytes] under [group]'s epoch key and uploads the ciphertext.
     *
     * @return the `imeta` tag to put on the message, or null when the account
     *   has no Blossom server configured — there is nowhere to put a file and
     *   saying so beats a failure deeper in.
     */
    suspend fun upload(
        group: MlsGroup,
        bytes: ByteArray,
        mimeType: String,
        filename: String,
        context: Context,
    ): Array<String>? =
        withContext(Dispatchers.IO) {
            val server =
                account.settings.defaultFileServer.baseUrl
                    .ifBlank { return@withContext null }

            val sealed = CordnMediaEncryption.encrypt(bytes, CordnMediaEncryption.fileKey(group), mimeType, filename)
            CordnMediaTag.build(sealed, put(sealed, server, context))
        }

    /**
     * Uploads the ciphertext and returns the URL the host gave it.
     *
     * Every privacy decision is in [CordnBlobUpload]; this forwards it. Read
     * that KDoc before changing an argument here — the wrong constant does not
     * fail, it just tells a server something.
     */
    private suspend fun put(
        sealed: CordnEncryptedMedia,
        server: String,
        context: Context,
    ): String {
        val blob = CordnBlobUpload.of(sealed)

        val result =
            BlossomUploader().upload(
                inputStream = ByteArrayInputStream(blob.bytes),
                hash = blob.hash,
                length = blob.length,
                baseFileName = blob.baseFileName,
                contentType = blob.contentType,
                alt = blob.alt,
                sensitiveContent = blob.sensitiveContent,
                serverBaseUrl = server,
                okHttpClient = Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForUploads,
                httpAuth = { hash, size, alt -> account.createBlossomUploadAuth(hash, size, alt) },
                context = context,
                useMediaEndpoint = blob.useMediaEndpoint,
            )

        return result.url ?: throw IllegalStateException("the blob server returned no URL")
    }

    /**
     * Fetches [attachment] and opens it with [group]'s epoch key.
     *
     * Throws if the bytes do not authenticate. That is the right outcome and
     * not a rare one: a blob host can serve anything it likes for a URL, and
     * the AEAD tag plus the plaintext hash are the only reasons to believe
     * what came back is what was sent.
     */
    suspend fun download(
        group: MlsGroup,
        attachment: CordnMediaAttachment,
    ): ByteArray =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(attachment.url)
                    .get()
                    .build()
            val response =
                Amethyst.instance.roleBasedHttpClientBuilder
                    .okHttpClientForImage(attachment.url)
                    .newCall(request)
                    .execute()

            val body =
                response.use {
                    check(it.isSuccessful) { "the blob server answered ${it.code}" }
                    it.body.bytes()
                }

            CordnMediaEncryption.decrypt(
                ciphertext = body,
                fileKey = CordnMediaEncryption.fileKey(group),
                nonce = attachment.nonceBytes,
                plaintextHash = attachment.hashBytes,
                mimeType = attachment.mimeType,
                filename = attachment.filename,
            )
        }
}
