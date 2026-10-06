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
package com.vitorpamplona.amethyst.commons.ui.components

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUri
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalFoundationApi::class)
actual fun Modifier.imageContentReceiver(onImage: (MediaUri, String?) -> Unit): Modifier =
    composed {
        val appContext = LocalContext.current.applicationContext
        val scope = rememberCoroutineScope()
        contentReceiver(
            object : ReceiveContentListener {
                override fun onReceive(transferableContent: TransferableContent): TransferableContent? {
                    if (!transferableContent.hasMediaType(MediaType.Image)) {
                        return transferableContent
                    }
                    val mimeType =
                        transferableContent.clipEntry.clipData.description
                            .getMimeType(0)
                    val received = mutableListOf<Uri>()
                    val remaining =
                        transferableContent.consume { item ->
                            item.uri?.let { received.add(it) } != null
                        }
                    // A keyboard's read grant is revoked once the InputContentInfo that Compose
                    // keeps in transferableContent's extras is garbage collected, which can be long
                    // before the user presses upload. Copy the files while this coroutine still
                    // references transferableContent, so the upload reads our own copy.
                    scope.launch {
                        received.forEach { uri ->
                            val local = withContext(Dispatchers.IO) { copyToCache(appContext, uri, mimeType, transferableContent) }
                            onImage(local ?: uri, mimeType)
                        }
                    }
                    return remaining
                }
            },
        )
    }

private const val RECEIVED_DIR = "received_content"

/**
 * Copies [uri] into the app cache, or returns null to fall back to the original address.
 * [keepGrantAlive] is unused: taking it makes the calling coroutine capture it, which keeps the
 * keyboard's grant alive until the copy is done.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun copyToCache(
    context: Context,
    uri: Uri,
    mimeType: String?,
    @Suppress("UNUSED_PARAMETER") keepGrantAlive: TransferableContent,
): Uri? =
    try {
        val dir = File(context.cacheDir, RECEIVED_DIR).apply { mkdirs() }
        deleteStaleFiles(dir)
        val extension = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "bin"
        val file = File.createTempFile("received-", ".$extension", dir)
        val copied =
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
        if (copied == null) {
            file.delete()
            null
        } else {
            file.toUri()
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.w("ImageContentReceiver", "Could not copy received content $uri", e)
        null
    }

/** Received files are only needed until the upload finishes; drop those left from earlier days. */
private fun deleteStaleFiles(dir: File) {
    val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
    dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
}
