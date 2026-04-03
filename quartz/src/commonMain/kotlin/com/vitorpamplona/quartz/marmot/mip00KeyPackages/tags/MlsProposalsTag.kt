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
package com.vitorpamplona.quartz.marmot.mip00KeyPackages.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * Supported non-default MLS proposal type IDs.
 * Marmot requires: 0x000a (self_remove) per MLS Extensions draft.
 *
 * Note: 0x000a is used for both self_remove (proposal type) and last_resort
 * (extension type) but they belong to different IANA registries.
 *
 * Example tag: ["mls_proposals", "0x000a"]
 */
class MlsProposalsTag {
    companion object {
        const val TAG_NAME = "mls_proposals"

        /** SelfRemove proposal type (MIP-03) */
        const val SELF_REMOVE = "0x000a"

        fun parse(tag: Array<String>): List<String>? {
            ensure(tag.has(1) && tag[0] == TAG_NAME) { return null }
            return tag.drop(1).filter { it.isNotEmpty() }
        }

        fun assemble(proposalIds: List<String>) = arrayOf(TAG_NAME, *proposalIds.toTypedArray())

        fun assembleDefault() = assemble(listOf(SELF_REMOVE))
    }
}
