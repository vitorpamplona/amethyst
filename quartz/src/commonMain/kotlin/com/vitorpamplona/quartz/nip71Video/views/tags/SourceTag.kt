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
package com.vitorpamplona.quartz.nip71Video.views.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

/**
 * Where the viewer found the video.
 *
 * [type] is written as-is: divine-mobile names the discovery tab inside it (`discovery:foryou`,
 * `discovery:new`, `discovery:featured`, …), so [category] strips the tab when only the surface
 * matters. [detail] is what the surface was showing: the hashtag, the search query, the featured
 * tab's id.
 */
@Immutable
data class ViewSource(
    val type: String,
    val detail: String? = null,
) {
    /** [type] without the tab: `discovery` for every `discovery:<tab>`. */
    val category get() = type.substringBefore(':')

    companion object {
        const val HOME = "home"
        const val DISCOVERY = "discovery"
        const val PROFILE = "profile"
        const val SHARE = "share"
        const val SEARCH = "search"
        const val UNKNOWN = "unknown"
    }
}

class SourceTag {
    companion object {
        const val TAG_NAME = "source"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        fun parse(tag: Array<String>): ViewSource? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return ViewSource(tag[1], tag.getOrNull(2)?.ifEmpty { null })
        }

        fun assemble(source: ViewSource) = arrayOfNotNull(TAG_NAME, source.type, source.detail)
    }
}
