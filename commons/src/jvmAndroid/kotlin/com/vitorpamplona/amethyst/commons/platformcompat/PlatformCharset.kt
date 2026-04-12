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
package com.vitorpamplona.amethyst.commons.platformcompat

actual class PlatformCharset(
    val javaCharset: java.nio.charset.Charset,
) {
    actual fun name(): String = javaCharset.name()

    actual companion object {
        actual fun forName(charsetName: String): PlatformCharset =
            PlatformCharset(
                java.nio.charset.Charset
                    .forName(charsetName),
            )
    }
}

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
actual fun decodeBytes(
    bytes: ByteArray,
    offset: Int,
    length: Int,
    charset: PlatformCharset,
): String = java.lang.String(bytes, offset, length, charset.javaCharset) as String
