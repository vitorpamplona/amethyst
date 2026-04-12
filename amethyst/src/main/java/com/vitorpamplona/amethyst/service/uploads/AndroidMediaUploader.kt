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
package com.vitorpamplona.amethyst.service.uploads

import android.content.Context
import android.net.Uri
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.upload.IMediaUploader
import com.vitorpamplona.amethyst.commons.upload.MediaUploadException
import com.vitorpamplona.amethyst.commons.upload.MediaUploadResult
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.uploads.blossom.BlossomUploader
import com.vitorpamplona.amethyst.service.uploads.nip96.Nip96Uploader
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerType
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import kotlin.coroutines.cancellation.CancellationException

/**
 * Android implementation of [IMediaUploader] that wraps the existing upload pipeline:
 * MetadataStripper → MediaCompressor → Nip96/Blossom upload.
 *
 * Used by metadata ViewModels (BookmarkGroupMetadata, FollowPackMetadata,
 * PeopleListMetadata, ChannelMetadata, NewUserMetadata) to upload profile
 * pictures and banners through a platform-abstracted interface.
 */
class AndroidMediaUploader(
    private val context: Context,
    private val account: Account,
) : IMediaUploader {
    override suspend fun uploadMedia(
        mediaUri: String,
        mimeType: String?,
        onProgress: ((Float) -> Unit)?,
    ): MediaUploadResult {
        val uri = Uri.parse(mediaUri)

        // Step 1: Strip location metadata if user setting enabled
        val strippedUri =
            if (account.settings.stripLocationOnUpload) {
                val result = MetadataStripper.strip(uri, mimeType, context.applicationContext)
                if (!result.stripped) {
                    throw MediaUploadException(
                        title = stringRes(context, R.string.metadata_strip_failed_title),
                        message = stringRes(context, R.string.metadata_strip_failed_upload_cancelled),
                    )
                }
                result.uri
            } else {
                uri
            }

        // Step 2: Compress media
        val compResult =
            MediaCompressor().compress(
                strippedUri,
                mimeType,
                CompressorQuality.MEDIUM,
                context.applicationContext,
            )

        // Step 3: Upload to the user's configured file server
        try {
            val server = account.settings.defaultFileServer
            val result =
                when (server.type) {
                    ServerType.NIP96 -> {
                        Nip96Uploader().upload(
                            uri = compResult.uri,
                            contentType = compResult.contentType,
                            size = compResult.size,
                            alt = null,
                            sensitiveContent = null,
                            serverBaseUrl = server.baseUrl,
                            okHttpClient = Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForUploads,
                            onProgress = { percent -> onProgress?.invoke(percent) },
                            httpAuth = account::createHTTPAuthorization,
                            context = context,
                        )
                    }

                    ServerType.Blossom -> {
                        BlossomUploader().upload(
                            uri = compResult.uri,
                            contentType = compResult.contentType,
                            size = compResult.size,
                            alt = null,
                            sensitiveContent = null,
                            serverBaseUrl = server.baseUrl,
                            okHttpClient = Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForUploads,
                            httpAuth = account::createBlossomUploadAuth,
                            context = context,
                        )
                    }

                    ServerType.NIP95 -> {
                        throw MediaUploadException(
                            title = stringRes(context, R.string.failed_to_upload_media_no_details),
                            message = "NIP-95 uploads are not supported through IMediaUploader. Use UploadOrchestrator directly.",
                        )
                    }
                }

            if (result.url != null) {
                return MediaUploadResult(
                    url = result.url,
                    sha256 = result.sha256,
                    size = result.size,
                    type = result.type,
                )
            } else {
                throw MediaUploadException(
                    title = stringRes(context, R.string.failed_to_upload_media_no_details),
                    message = stringRes(context, R.string.server_did_not_provide_a_url_after_uploading),
                )
            }
        } catch (e: MediaUploadException) {
            throw e
        } catch (_: SignerExceptions.ReadOnlyException) {
            throw MediaUploadException(
                title = stringRes(context, R.string.failed_to_upload_media_no_details),
                message = stringRes(context, R.string.login_with_a_private_key_to_be_able_to_upload),
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw MediaUploadException(
                title = stringRes(context, R.string.failed_to_upload_media_no_details),
                message = e.message ?: e.javaClass.simpleName,
                cause = e,
            )
        }
    }
}
