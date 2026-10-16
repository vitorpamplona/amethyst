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
package com.vitorpamplona.quartz.nip99Classifieds.shipping.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * The array-valued tags of an Open Markets shipping option: `["country", "US", "CA", …]`
 * (ISO 3166-1 alpha-2) and `["region", "US-FL", …]` (ISO 3166-2). Every value after the name
 * counts; blanks are dropped.
 */
class CodeListTag {
    companion object {
        const val COUNTRY = "country"
        const val REGION = "region"

        fun parse(
            tag: Array<String>,
            name: String,
        ): List<String>? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == name) { return null }
            val codes = (1 until tag.size).mapNotNull { tag[it].trim().ifEmpty { null } }
            return codes.ifEmpty { null }
        }

        fun assemble(
            name: String,
            codes: List<String>,
        ) = arrayOf(name, *codes.toTypedArray())
    }
}
