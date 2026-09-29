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
package com.vitorpamplona.quartz.nip90Dvms.tags

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip01Core.links.LinkBuilder
import com.vitorpamplona.quartz.nip01Core.links.Relation

// The NIP-90 job kinds share two shapes, one for every request (5000-5999) and one for every
// result (6000-6999) and feedback (7000), so each class's links() is one of these calls.

/**
 * One NIP-90 `["i", <data>, <input-type>, <relay>, <marker>]`. An `event` input is the event the
 * job works on; a `job` input is "the output of a previous job with the specified event ID"
 * (job chaining), so it points at that job's request; a `url` is a Tag target. `text` and
 * `prompt` inputs are free text, not references.
 */
private fun LinkBuilder.dvmInput(input: Array<String>) {
    if (input.size < 3) return
    when (input[2]) {
        "event" -> event(Relation.INPUT, input[1], InputTag.TAG_NAME)
        "job" -> event(Relation.INPUT_JOB, input[1], InputTag.TAG_NAME)
        "url" -> tag(Relation.TAG, InputTag.TAG_NAME, input[1])
    }
}

/**
 * A NIP-90 job request: its `i` inputs and, in `p`, the "Service Providers the customer is
 * interested in" ([Relation.SERVICE_PROVIDER]). With an `encrypted` tag the `i` and `param`
 * tags move into the NIP-04 content, so only `p` is left to link.
 *
 * [forUserParam]: the discovery kinds (5300, 5301) name the user to compute for in
 * `["param", "user", <pubkey>]` ([Relation.FOR_USER]); their `p` stays the DVM, as Quartz and
 * Amethyst write it.
 */
fun LinkBuilder.dvmRequestLinks(
    tags: TagArray,
    forUserParam: Boolean = false,
) = tags.fastForEach {
    if (it.size < 2) return@fastForEach
    when (it[0]) {
        InputTag.TAG_NAME -> dvmInput(it)
        "p" -> user(Relation.SERVICE_PROVIDER, it[1], "p")
        "param" -> if (forUserParam && it.size > 2 && it[1] == "user") user(Relation.FOR_USER, it[2], "param")
    }
}

/**
 * A NIP-90 job result or feedback: `e` is the job request it answers ([Relation.REQUEST]) and
 * `p` that request's author, the customer ([Relation.REQUEST_AUTHOR]). A result also copies the
 * request's `i` inputs ([withInputs]). The `request` tag re-embeds the request as JSON: the same
 * target as `e`, so not a second link.
 */
fun LinkBuilder.dvmResultLinks(
    tags: TagArray,
    withInputs: Boolean = true,
) = tags.fastForEach {
    if (it.size < 2) return@fastForEach
    when (it[0]) {
        "e" -> event(Relation.REQUEST, it[1], "e")
        "p" -> user(Relation.REQUEST_AUTHOR, it[1], "p")
        InputTag.TAG_NAME -> if (withInputs) dvmInput(it)
    }
}
