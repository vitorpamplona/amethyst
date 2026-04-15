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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.video.hls

import android.content.Context
import android.net.Uri
import com.davotoula.lightcompressor.hls.HlsContentTypes
import com.davotoula.lightcompressor.hls.HlsUploadHelper
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.uploads.MediaUploadResult
import com.vitorpamplona.amethyst.service.uploads.hls.HlsBlobUploaderFactory
import com.vitorpamplona.amethyst.service.uploads.hls.HlsVideoEventTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * Production wiring for [HlsPublishOrchestrator]. Binds the upload closure to
 * [HlsUploadHelper.run], the uploader factory to [HlsBlobUploaderFactory], and the
 * signAndPublish closure to the account's signer + outbox publish path.
 *
 * The Uri is read via [uriProvider] on each publish invocation so the orchestrator can be built
 * once (at VM load) before the user actually picks a video.
 */
fun createProductionHlsPublishOrchestrator(
    state: MutableStateFlow<HlsPublishState>,
    account: Account,
    context: Context,
    uriProvider: () -> Uri?,
): HlsPublishOrchestrator =
    HlsPublishOrchestrator(
        _state = state,
        runUpload = { config, listener, uploadFile ->
            val uri = uriProvider() ?: error("No video picked")
            HlsUploadHelper.run<MediaUploadResult>(
                context = context,
                uri = uri,
                config = config,
                listener = listener,
                uploader = uploadFile,
            )
        },
        buildUploader = { server ->
            HlsBlobUploaderFactory.create(server, account, context)
        },
        uploadMaster = { uploader, masterPlaylist ->
            val masterFile = File.createTempFile("hls-master-", ".m3u8", context.cacheDir)
            try {
                masterFile.writeText(masterPlaylist)
                uploader.upload(masterFile, HlsContentTypes.HLS_PLAYLIST)
            } finally {
                masterFile.delete()
            }
        },
        signAndPublish = { template ->
            val inner =
                when (template) {
                    is HlsVideoEventTemplate.Horizontal -> template.template
                    is HlsVideoEventTemplate.Vertical -> template.template
                }
            val signed = account.signer.sign(inner)
            account.sendAutomatic(signed)
            signed.id
        },
    )
