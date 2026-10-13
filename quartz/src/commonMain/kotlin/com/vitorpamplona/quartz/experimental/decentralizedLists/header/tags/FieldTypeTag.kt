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
package com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * `["field-type", <field name>, <type>]`: types a field the header declares with a
 * `required`/`recommended`/`optional`/`allowed` rule. It never declares a field of its own.
 * Readers know `text` (the default) and `url`; Brainstorm also reads the provisional `tel` and
 * `email` (a call and a mail, when the value is one) and `address` (a `kind:pubkey:d`). A type
 * changes rendering only where it has one obvious rendering, and any other type reads as text.
 */
@Immutable
data class FieldType(
    val field: String,
    val type: String,
)

class FieldTypeTag {
    companion object {
        const val TAG_NAME = "field-type"

        const val TEXT = "text"
        const val URL = "url"
        const val TEL = "tel"
        const val EMAIL = "email"
        const val ADDRESS = "address"

        fun isTag(tag: Array<String>) = tag.has(2) && tag[0] == TAG_NAME && tag[1].isNotEmpty() && tag[2].isNotEmpty()

        fun parse(tag: Array<String>): FieldType? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            ensure(tag[2].isNotEmpty()) { return null }
            return FieldType(tag[1], tag[2])
        }

        fun assemble(
            field: String,
            type: String,
        ) = arrayOf(TAG_NAME, field, type)
    }
}
