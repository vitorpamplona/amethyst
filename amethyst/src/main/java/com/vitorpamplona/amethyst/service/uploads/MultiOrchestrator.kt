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
package com.vitorpamplona.amethyst.service.uploads

import android.content.Context
import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMediaProcessing
import com.vitorpamplona.quartz.nip17Dm.files.encryption.NostrCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class MultiOrchestrator(
    uris: List<SelectedMedia>,
) {
    private var list: List<SelectedMediaProcessing> = uris.map { SelectedMediaProcessing(it) }

    @Stable
    class Result(
        val allGood: Boolean,
        val successful: List<UploadingState.Finished>,
        val errors: List<UploadingState.Error>,
    )

    fun first() = list.first()

    suspend fun upload(
        scope: CoroutineScope,
        alt: String?,
        contentWarningReason: String?,
        mediaQuality: CompressorQuality,
        server: ServerName,
        account: Account,
        context: Context,
    ): Result {
        val jobs =
            list.map { item ->
                scope.launch(Dispatchers.IO) {
                    item.orchestrator.upload(
                        item.media.uri,
                        item.media.mimeType,
                        alt,
                        contentWarningReason,
                        mediaQuality,
                        server,
                        account,
                        context,
                    )
                }
            }

        jobs.joinAll()

        return computeFinalResults()
    }

    suspend fun uploadEncrypted(
        scope: CoroutineScope,
        alt: String?,
        contentWarningReason: String?,
        mediaQuality: CompressorQuality,
        cipher: NostrCipher,
        server: ServerName,
        account: Account,
        context: Context,
    ): Result {
        val jobs =
            list.map { item ->
                scope.launch(Dispatchers.IO) {
                    item.orchestrator.uploadEncrypted(
                        item.media.uri,
                        item.media.mimeType,
                        alt,
                        contentWarningReason,
                        mediaQuality,
                        cipher,
                        server,
                        account,
                        context,
                    )
                }
            }

        jobs.joinAll()

        return computeFinalResults()
    }

    private fun computeFinalResults(): Result {
        val resultsByState =
            list.map {
                it.orchestrator.progressState.value
            }

        val finished = resultsByState.filterIsInstance<UploadingState.Finished>()
        val errors = resultsByState.filterIsInstance<UploadingState.Error>()

        return Result(finished.size == list.size, finished, errors)
    }

    fun remove(selected: SelectedMediaProcessing) {
        list = list.filter { it != selected }
    }

    fun size() = list.size

    fun get(index: Int) = list[index]
}
