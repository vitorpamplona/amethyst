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
package com.vitorpamplona.quartz.nip99Classifieds.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

/**
 * Open Markets `["type", <simple|variable|variation>, <digital|physical>]` on a kind-30402 product.
 *
 * Both values are kept raw: the spec defaults an absent tag to simple/digital, but clients in the
 * wild disagree (Conduit reads a missing format as physical), so the default is the caller's call.
 */
data class ProductTypeTag(
    val type: String,
    val format: String?,
) {
    fun isVariable() = type == VARIABLE

    /** A variation names its `variable` parent with an `a` tag; see `ClassifiedsEvent.parentProduct`. */
    fun isVariation() = type == VARIATION

    fun isDigital() = format == DIGITAL

    fun isPhysical() = format == PHYSICAL

    fun toTagArray() = assemble(type, format)

    companion object {
        const val TAG_NAME = "type"

        const val SIMPLE = "simple"
        const val VARIABLE = "variable"
        const val VARIATION = "variation"

        const val DIGITAL = "digital"
        const val PHYSICAL = "physical"

        fun parse(tag: Array<String>): ProductTypeTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return ProductTypeTag(tag[1], tag.getOrNull(2)?.ifEmpty { null })
        }

        fun assemble(
            type: String,
            format: String?,
        ) = arrayOfNotNull(TAG_NAME, type, format)
    }
}
