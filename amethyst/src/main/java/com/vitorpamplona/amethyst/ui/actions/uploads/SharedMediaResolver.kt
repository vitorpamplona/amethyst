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
package com.vitorpamplona.amethyst.ui.actions.uploads

import android.content.Context
import androidx.core.net.toUri
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves a shared content-URI string (from an Android SEND intent) into a
 * [SelectedMedia] by reading its MIME type off the main thread. Returns null for
 * a null/blank URI. Shared by the share-to-compose pre-fill paths (DM chatroom,
 * group DM, …) so the URI→MIME→SelectedMedia logic lives in one place.
 */
suspend fun resolveSharedMedia(
    context: Context,
    uriString: String?,
): SelectedMedia? =
    uriString?.ifBlank { null }?.toUri()?.let { uri ->
        withContext(Dispatchers.IO) {
            SelectedMedia(uri, context.contentResolver.getType(uri))
        }
    }

/**
 * Batch form for SEND_MULTIPLE shares: resolves every URI in one trip to the IO dispatcher
 * instead of one context switch per file. Blank entries are dropped, so an empty list in means
 * an empty list out and the composer stays closed.
 */
suspend fun resolveSharedMedia(
    context: Context,
    uriStrings: List<String>,
): ImmutableList<SelectedMedia> {
    val uris = uriStrings.mapNotNull { it.ifBlank { null }?.toUri() }
    if (uris.isEmpty()) return persistentListOf()

    return withContext(Dispatchers.IO) {
        uris.map { SelectedMedia(it, context.contentResolver.getType(it)) }.toImmutableList()
    }
}
