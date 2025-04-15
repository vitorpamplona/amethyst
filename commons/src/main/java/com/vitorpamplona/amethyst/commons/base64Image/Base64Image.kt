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
package com.vitorpamplona.amethyst.commons.base64Image

import android.R.attr.data
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import java.util.Base64
import java.util.regex.Pattern

class Base64Image {
    companion object {
        val pattern = Pattern.compile("data:image/(${RichTextParser.Companion.imageExtensions.joinToString(separator = "|") { it } });base64,([a-zA-Z0-9+/]+={0,2})")

        fun isBase64(content: String): Boolean {
            val matcher = pattern.matcher(content)
            return matcher.find()
        }

        fun toBitmap(content: String): Bitmap {
            val matcher = pattern.matcher(data.toString())

            if (matcher.find()) {
                val base64String = matcher.group(2)

                val byteArray = Base64.getDecoder().decode(base64String)
                return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
            }

            throw Exception("Unable to convert base64 to image $content")
        }
    }
}
