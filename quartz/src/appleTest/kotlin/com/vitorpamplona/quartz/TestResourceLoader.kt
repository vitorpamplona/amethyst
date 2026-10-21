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
package com.vitorpamplona.quartz

import com.vitorpamplona.quartz.utils.GZip
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.stringWithContentsOfFile
import platform.posix.getenv
import platform.posix.memcpy

actual class TestResourceLoader {
    actual fun loadDecompressString(file: String): String {
        val data = loadFileData(file)
        return GZip.decompress(data)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun loadString(file: String): String {
        val resourceDir = getenv("TEST_RESOURCES_ROOT")?.toKString()
        val filePath = "$resourceDir/$file"
        return NSString.stringWithContentsOfFile(filePath, encoding = NSUTF8StringEncoding, error = null) as String
    }

    @OptIn(ExperimentalForeignApi::class)
    fun loadFileData(file: String): ByteArray {
        val resourceDir = getenv("TEST_RESOURCES_ROOT")?.toKString()
        val filePath = "$resourceDir/$file"
        return NSData.dataWithContentsOfFile(filePath)?.toByteArray() ?: throw IllegalArgumentException("Resource not found: $filePath")
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val out = ByteArray(length.toInt())
    if (out.isNotEmpty()) {
        out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    }
    return out
}
