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
package com.vitorpamplona.quartz.experimental.forks

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.utils.ensure

/**
 * The address an `a` tag marked `fork` points at: `["a", <address>, <relay>, "fork"]`, the
 * version a note, a NIP text or a wiki article (NIP-54 "Forks") was forked from — of any kind.
 * Only the marked tag counts: an event also carries unmarked `a` tags (a community, a mention),
 * and those are not its origin.
 */
fun parseForkedAddress(tag: Array<String>): Address? {
    if (tag.size < 4 || tag[0] != "a") return null
    if (tag[3] != MarkedETag.MARKER.FORK.code) return null
    return Address.parse(tag[1])
}

/** [parseForkedAddress] as the parsed [ATag], relay hint included. */
fun parseForkedATag(tag: Array<String>): ATag? {
    ensure(isForkMarked(tag)) { return null }
    return ATag.parse(tag)
}

/** An `a` that is not marked `fork`: a plain reference (a mention, a community). */
fun parseUnforkedATag(tag: Array<String>): ATag? {
    ensure(!isForkMarked(tag)) { return null }
    return ATag.parse(tag)
}

private fun isForkMarked(tag: Array<String>) = tag.has(MarkedETag.ORDER_MARKER) && tag[MarkedETag.ORDER_MARKER] == MarkedETag.MARKER.FORK.code
