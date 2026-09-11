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
package com.vitorpamplona.quartz.marmot.mip05PushNotifications.tags

import com.vitorpamplona.quartz.marmot.mip05PushNotifications.PushGossip
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * The `v` tag every push event carries — kinds 446, 447, 448 and 449.
 *
 * A recipient MUST reject any other value. `marmot-push-v1` is not a rename of
 * the earlier exploratory `mip05-v1`: that version carried tokens in tags with
 * an empty content, left the sender's leaf implicit, defined no removals and
 * predated owner authentication entirely. The two are not interoperable, and
 * refusing the old string is how they stay apart.
 */
class VersionTag {
    companion object {
        const val TAG_NAME = "v"
        const val CURRENT_VERSION = PushGossip.VERSION

        fun parse(tag: Array<String>): String? {
            ensure(tag.has(1) && tag[0] == TAG_NAME) { return null }
            ensure(tag[1] == CURRENT_VERSION) { return null }
            return tag[1]
        }

        fun assemble() = arrayOf(TAG_NAME, CURRENT_VERSION)
    }
}
