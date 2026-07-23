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
package com.vitorpamplona.quartz.buzz.jobs.tags

import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * The Buzz `status` tag - a short machine-readable status token attached to the agent
 * job-protocol progress/result/error kinds (43003/43004/43006).
 *
 * NOTE: the Buzz agent job protocol (kinds 43001-43006) is *reserved* in
 * `buzz-core/src/kind.rs` and surfaces in `buzz-db/src/feed.rs` as feed activity, but
 * no builder or ingest handler in the referenced Buzz clone pins its tag layout. This
 * tag name/shape is a best-effort model and MUST be reconciled against Buzz once the
 * job protocol is implemented there.
 */
object StatusTag {
    const val TAG_NAME = "status"

    fun match(tag: Tag) = tag.has(1) && tag[0] == TAG_NAME

    fun parse(tag: Tag): String? {
        ensure(tag.has(1)) { return null }
        ensure(tag[0] == TAG_NAME) { return null }
        ensure(tag[1].isNotEmpty()) { return null }
        return tag[1]
    }

    fun assemble(status: String) = arrayOf(TAG_NAME, status)
}
