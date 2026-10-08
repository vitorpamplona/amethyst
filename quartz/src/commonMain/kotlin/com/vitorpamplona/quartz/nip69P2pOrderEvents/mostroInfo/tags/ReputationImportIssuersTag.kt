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
package com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.ensure

/**
 * `reputation_import_issuers`: the keys of every issuer whose reputation attestations the instance
 * imports, one hex key per value.
 *
 * other_events.md: present only when import is enabled, and "an enabled node with an empty trust
 * list publishes the tag with no values" — so a bare `["reputation_import_issuers"]` parses to an
 * empty list (import on, nobody trusted), unlike a missing tag (null: import off). Values that are
 * not 64-hex keys are skipped rather than failing the tag.
 */
class ReputationImportIssuersTag {
    companion object {
        const val TAG_NAME = "reputation_import_issuers"

        fun parse(tag: Array<String>): List<HexKey>? {
            ensure(tag.isNotEmpty()) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            if (!tag.has(1)) return emptyList()
            return tag.drop(1).filter { it.length == 64 && Hex.isHex64(it) }
        }

        fun assemble(issuers: List<HexKey>) = arrayOf(TAG_NAME, *issuers.toTypedArray())
    }
}
