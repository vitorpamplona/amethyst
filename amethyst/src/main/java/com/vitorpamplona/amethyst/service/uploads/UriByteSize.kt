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

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.vitorpamplona.quartz.utils.Log
import java.io.File

private const val TAG = "UriByteSize"

/**
 * The length of [uri] in bytes, or null when it can't be told without reading the content.
 *
 * A `file://` uri (every temp the app writes itself: trims, crops, compressed outputs) has no
 * provider to ask, so it is the file's own length. Anything else is the provider's
 * [OpenableColumns.SIZE], then the descriptor's `statSize`. Never reads the stream; a caller that
 * needs an answer regardless counts the bytes itself.
 */
internal fun Context.uriByteSize(uri: Uri): Long? {
    if (uri.scheme == ContentResolver.SCHEME_FILE) {
        return uri.path
            ?.let(::File)
            ?.takeIf { it.isFile }
            ?.length()
    }
    return contentResolver.querySize(uri) ?: contentResolver.statSize(uri)
}

/** The provider's size for [uri], or null when it has no row, no SIZE column or no value in it. */
private fun ContentResolver.querySize(uri: Uri): Long? =
    try {
        query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            val idx = it.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && it.moveToFirst() && !it.isNull(idx)) it.getLong(idx) else null
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to query the size of $uri", e)
        null
    }

/** The size of the file behind [uri]'s descriptor, or null when it is a pipe or socket (-1) or can't be opened. */
private fun ContentResolver.statSize(uri: Uri): Long? =
    try {
        openFileDescriptor(uri, "r")?.use { fd -> fd.statSize.takeIf { it >= 0 } }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to stat $uri", e)
        null
    }
