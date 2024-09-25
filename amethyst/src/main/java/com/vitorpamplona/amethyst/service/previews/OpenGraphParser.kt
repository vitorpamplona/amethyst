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
package com.vitorpamplona.amethyst.service.previews

import com.vitorpamplona.amethyst.commons.preview.MetaTag

class OpenGraphParser {
    class Result(
        val title: String,
        val description: String,
        val image: String,
    )

    companion object {
        val ATTRIBUTE_VALUE_PROPERTY = "property"
        val ATTRIBUTE_VALUE_NAME = "name"
        val ATTRIBUTE_VALUE_ITEMPROP = "itemprop"

        // for <meta itemprop=... to get title
        private val META_X_TITLE =
            arrayOf(
                "og:title",
                "twitter:title",
                "title",
            )

        // for <meta itemprop=... to get description
        private val META_X_DESCRIPTION =
            arrayOf(
                "og:description",
                "twitter:description",
                "description",
            )

        // for <meta itemprop=... to get image
        private val META_X_IMAGE =
            arrayOf(
                "og:image",
                "twitter:image",
                "image",
            )

        private val CONTENT = "content"
    }

    fun extractUrlInfo(metaTags: Sequence<MetaTag>): Result {
        var title: String = ""
        var description: String = ""
        var image: String = ""

        metaTags.forEach {
            when (it.attr(ATTRIBUTE_VALUE_PROPERTY)) {
                in META_X_TITLE ->
                    if (title.isEmpty()) {
                        title = it.attr(CONTENT)
                    }

                in META_X_DESCRIPTION ->
                    if (description.isEmpty()) {
                        description = it.attr(CONTENT)
                    }

                in META_X_IMAGE ->
                    if (image.isEmpty()) {
                        image = it.attr(CONTENT)
                    }
            }

            when (it.attr(ATTRIBUTE_VALUE_NAME)) {
                in META_X_TITLE ->
                    if (title.isEmpty()) {
                        title = it.attr(CONTENT)
                    }

                in META_X_DESCRIPTION ->
                    if (description.isEmpty()) {
                        description = it.attr(CONTENT)
                    }

                in META_X_IMAGE ->
                    if (image.isEmpty()) {
                        image = it.attr(CONTENT)
                    }
            }

            when (it.attr(ATTRIBUTE_VALUE_ITEMPROP)) {
                in META_X_TITLE ->
                    if (title.isEmpty()) {
                        title = it.attr(CONTENT)
                    }

                in META_X_DESCRIPTION ->
                    if (description.isEmpty()) {
                        description = it.attr(CONTENT)
                    }

                in META_X_IMAGE ->
                    if (image.isEmpty()) {
                        image = it.attr(CONTENT)
                    }
            }

            if (title.isNotEmpty() && description.isNotEmpty() && image.isNotEmpty()) {
                return Result(title, description, image)
            }
        }
        return Result(title, description, image)
    }
}
