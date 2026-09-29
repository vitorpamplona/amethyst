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
package com.vitorpamplona.quartz.concord.cord03Channels.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * The `["epoch", <n>]` tag that binds a Concord Chat Plane rumor to the community
 * epoch it was authored under (CORD-03). Paired with [ChannelTag]; an event whose
 * epoch does not match the plane it arrived on is rejected, so a message can't be
 * replayed across a rekey.
 */
class EpochTag {
    companion object {
        const val TAG_NAME = "epoch"

        fun isTag(tag: Array<String>) = tag.has(1) && tag[0] == TAG_NAME && tag[1].isNotEmpty()

        /**
         * The epoch, or null when the value is not its canonical decimal form (CORD-01 Encoding:
         * "no leading zeros"). `"04"`, `"+4"`, `"-1"` and `" 4"` are all refused: the binding is a
         * strict string comparison, so a spelling that merely parses to the same number is a
         * different binding.
         */
        fun parse(tag: Array<String>): Long? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            val epoch = tag[1].toLongOrNull() ?: return null
            ensure(epoch >= 0 && epoch.toString() == tag[1]) { return null }
            return epoch
        }

        fun assemble(epoch: Long) = arrayOf(TAG_NAME, epoch.toString())
    }
}
