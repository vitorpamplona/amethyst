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
package com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper

/**
 * `["EVENT", <event>]`. [eventJson], when given, is [event]'s own JSON object as the caller
 * already holds it (a line of an export, a row of a store) and is put on the wire verbatim
 * instead of serializing [event] again: a bulk publish of kind 3s from a file spent about 12%
 * of the publisher's CPU re-serializing events it had just parsed. It must be [event]'s JSON, the same id and
 * signature: the relay verifies what it receives, and answers by the id it parsed from it.
 */
class EventCmd(
    val event: Event,
    val eventJson: String? = null,
) : Command {
    override fun label(): String = LABEL

    override fun isValid() = true

    override fun toJson(): String = if (eventJson != null) "[\"$LABEL\",$eventJson]" else OptimizedJsonMapper.toJson(this)

    companion object {
        const val LABEL = "EVENT"
    }
}
