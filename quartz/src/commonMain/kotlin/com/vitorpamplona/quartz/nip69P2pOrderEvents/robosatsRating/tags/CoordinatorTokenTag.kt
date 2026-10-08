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
package com.vitorpamplona.quartz.nip69P2pOrderEvents.robosatsRating.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.ensure

/**
 * `sig`: the review token a RoboSats coordinator hands a robot after a finished trade
 * (`POST /api/review/`), proving the reviewer traded there.
 *
 * It is a BIP-340 Schnorr signature by the coordinator's Nostr key (the `p`) over the UTF-8 bytes
 * of `<rating event pubkey><order id>` — unhashed, variable length (`api/nostr.py` `sign_message`,
 * `frontend/src/utils/nostr.ts` `verifyCoordinatorToken`). Parsed only when it has the shape of a
 * signature: 128 hex characters.
 */
class CoordinatorTokenTag {
    companion object {
        const val TAG_NAME = "sig"

        fun parse(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 128) { return null }
            ensure(Hex.isHex(tag[1])) { return null }
            return tag[1]
        }

        fun assemble(token: String) = arrayOf(TAG_NAME, token)
    }
}
