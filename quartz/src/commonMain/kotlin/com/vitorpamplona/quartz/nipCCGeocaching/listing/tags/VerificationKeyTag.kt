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
package com.vitorpamplona.quartz.nipCCGeocaching.listing.tags

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.ensure

/**
 * The `verification` tag of a geocache listing (kind 37516): the public half of the keypair a
 * finder proves physical presence with.
 *
 * The matching private key lives at the cache itself, normally behind a QR code. It is not the
 * owner's key and is not an account key — see
 * [com.vitorpamplona.quartz.nipCCGeocaching.verification.GeocacheVerificationEvent].
 */
class VerificationKeyTag {
    companion object {
        const val TAG_NAME = "verification"

        /** [Hex.isHex64] does not check the length itself, so a 70-char value would pass it alone. */
        private fun isPubKey(value: String) = value.length == 64 && Hex.isHex64(value)

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && isPubKey(tag[1])

        fun parse(tag: Array<String>): HexKey? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(isPubKey(tag[1])) { return null }
            return tag[1]
        }

        fun assemble(pubKey: HexKey) = arrayOf(TAG_NAME, pubKey)
    }
}
