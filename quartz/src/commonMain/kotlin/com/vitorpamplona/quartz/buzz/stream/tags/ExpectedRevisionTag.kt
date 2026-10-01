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
package com.vitorpamplona.quartz.buzz.stream.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.ensure

/**
 * The optional compare-and-swap precondition on a Buzz canvas write (`kind:40100`):
 * `["expected-revision", "none" | <64-hex event id>]`.
 *
 * A 64-hex id names the canvas head the write was composed against; the literal [NONE] asserts
 * that no head exists yet. The relay reads the live head under a lock and rejects a mismatch with
 * a `conflict:` OK message before storing anything; a canvas write without the tag is an
 * unconditional append.
 *
 * Mirrors `build_set_canvas` in Buzz's `buzz-sdk/src/builders.rs` and
 * `parse_canvas_expected_revision` in `buzz-relay/src/handlers/ingest.rs`: the tag has exactly one
 * value, and the relay rejects (`invalid:`) any other shape, so [parse] only accepts that one.
 */
class ExpectedRevisionTag {
    companion object {
        const val TAG_NAME = "expected-revision"

        /** The sentinel value asserting the channel has no canvas head yet. */
        const val NONE = "none"

        /** True for [NONE] or a 64-character hex event id — the only values the relay accepts. */
        fun isValidValue(value: String): Boolean = value == NONE || (value.length == 64 && Hex.isHex(value))

        fun parse(tag: Array<String>): String? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag.size == 2) { return null }
            ensure(isValidValue(tag[1])) { return null }
            return tag[1]
        }

        fun assemble(expectedRevision: String): Array<String> {
            require(isValidValue(expectedRevision)) {
                "expected-revision must be the literal \"none\" or a 64-character hex event id (got \"$expectedRevision\")"
            }
            return arrayOf(TAG_NAME, expectedRevision)
        }
    }
}
