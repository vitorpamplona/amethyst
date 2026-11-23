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

import android.net.Uri
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Temp files the uploader wrote already sanitized, which the metadata strip before an upload sends
 * as they are instead of remuxing them a second time: a Studio trim strips the metadata and moves
 * the index to the front in the same pass.
 *
 * A file counts only through a `file://` uri, at the path it was marked at, and only while it still
 * has the length and modification time it had then. Any other uri, or a marked file changed since,
 * is stripped as usual. The marks live in memory: one lost with the process costs an extra strip,
 * never a skipped one.
 */
internal class SanitizedTempFiles {
    private class Stamp(
        val length: Long,
        val modified: Long,
    )

    private val marks = ConcurrentHashMap<String, Stamp>()

    /** Records [file], as it is now, as sanitized. */
    fun mark(file: File) {
        marks[file.absolutePath] = Stamp(file.length(), file.lastModified())
    }

    /** Drops the mark of the file at [uri], if it has one. */
    fun forget(uri: Uri) {
        fileOf(uri)?.let { marks.remove(it.absolutePath) }
    }

    /** Whether [uri] is a marked file that has not changed since it was marked. */
    fun isSanitized(uri: Uri): Boolean {
        val file = fileOf(uri) ?: return false
        val stamp = marks[file.absolutePath] ?: return false
        return file.isFile && file.length() == stamp.length && file.lastModified() == stamp.modified
    }

    private fun fileOf(uri: Uri): File? = if (uri.scheme == "file") uri.path?.let(::File) else null
}
