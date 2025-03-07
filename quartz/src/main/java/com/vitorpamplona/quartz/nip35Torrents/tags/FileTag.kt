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
package com.vitorpamplona.quartz.nip35Torrents.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip46RemoteSigner.getOrNull

@Immutable
class FileTag(
    val fileName: String,
    val bytes: Long?,
) {
    fun toTagArray() = assemble(fileName, bytes)

    companion object {
        const val TAG_NAME = "file"
        const val TAG_SIZE = 2

        @JvmStatic
        fun parse(tag: Array<String>): FileTag? {
            if (tag.size < TAG_SIZE || tag[0] != TAG_NAME) return null
            return FileTag(tag[1], tag.getOrNull(2)?.toLongOrNull())
        }

        @JvmStatic
        fun parseBytes(tag: Array<String>): Long? {
            if (tag.size < TAG_SIZE || tag[0] != TAG_NAME) return null
            return tag.getOrNull(2)?.toLongOrNull()
        }

        @JvmStatic
        fun assemble(
            name: String,
            bytes: Long?,
        ) = arrayOf(TAG_NAME, name, bytes.toString())
    }
}
