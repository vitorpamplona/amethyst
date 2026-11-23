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
package com.vitorpamplona.amethyst.commons.ui

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.ResourceReader

/**
 * Reads each compose-resources file a string comes from once, and every string after that as a
 * slice of it.
 *
 * A string is a range of its locale's packed strings file (`strings.commonMain.cvr` in its `values`
 * folder, 0.3 to 0.75 MB). The default reader opens the file and skips to the range for every string it hasn't
 * read yet, and the file is deflated inside the jar (and the APK), so each skip inflates everything
 * before the string: 330 KB on average, on the thread the UI waits on.
 *
 * Only [readPart] (strings and plurals) is cached; whole-file reads (images, fonts) pass through.
 */
@OptIn(ExperimentalResourceApi::class)
class CachingStringsReader private constructor(
    private val delegate: ResourceReader,
) : ResourceReader {
    private val mutex = Mutex()
    private val files = HashMap<String, ByteArray>()

    override suspend fun read(path: String): ByteArray = delegate.read(path)

    override suspend fun readPart(
        path: String,
        offset: Long,
        size: Long,
    ): ByteArray {
        val file = mutex.withLock { files.getOrPut(path) { delegate.read(path) } }
        return file.copyOfRange(offset.toInt(), (offset + size).toInt())
    }

    override fun getUri(path: String): String = delegate.getUri(path)

    companion object {
        private var last: CachingStringsReader? = null

        /**
         * The caching reader over [delegate], the same one each time it is asked for the same
         * delegate: a locale's strings file is read once however many windows show it.
         * Composition calls it, on the UI thread.
         */
        fun over(delegate: ResourceReader): CachingStringsReader = last?.takeIf { it.delegate === delegate } ?: CachingStringsReader(delegate).also { last = it }
    }
}
