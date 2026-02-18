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

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.darwin.NSObject
import platform.darwin.NSObjectMeta

actual class TestResourceLoader {
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    actual fun loadString(file: String): String {
        // Split the filename and extension (e.g., "data.json" -> "data", "json")
        val basename = file.substringBeforeLast(".")
        val extension = file.substringAfterLast(".", "")

        // Locate the file in the main application bundle
        val path =
            NSBundle.mainBundle.pathForResource(basename, ofType = extension)
                ?: throw IllegalArgumentException("Resource not found: $file")

        // Read the file content as a UTF-8 string
        return NSString
            .stringWithContentsOfFile(
                path = path,
                encoding = NSUTF8StringEncoding,
                error = null,
            ).toString()
    }

    private class BundleMarker : NSObject() {
        companion object : NSObjectMeta()
    }
}
