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
package com.vitorpamplona.quartz.experimental.walletScrutiny.verification.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.ensure

/**
 * `["based-on", "<verification event id>:<author pubkey>"]`: the earlier verification whose
 * method (build script) this one reuses. The author rides along so a client can name them even
 * after that verification is deleted. The event id must be 64 hex chars or the tag is dropped; an
 * author that is not a 64-hex key reads as null.
 */
@Immutable
data class BasedOnTag(
    val eventId: HexKey,
    val author: HexKey?,
) {
    fun toTagArray() = assemble(eventId, author)

    companion object {
        const val TAG_NAME = "based-on"

        fun parse(tag: Array<String>): BasedOnTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            val value = tag[1]
            ensure(value.length >= 64 && Hex.isHex64(value)) { return null }
            ensure(value.length == 64 || value[64] == ':') { return null }
            val author = if (value.length == 129 && Hex.isHex(value, 65, 129)) value.substring(65) else null
            return BasedOnTag(value.substring(0, 64), author)
        }

        fun assemble(
            eventId: HexKey,
            author: HexKey?,
        ) = arrayOf(TAG_NAME, if (author != null) "$eventId:$author" else eventId)
    }
}
