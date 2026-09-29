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

import com.vitorpamplona.quartz.graph.LinkBuilder
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.graph.each
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryRequest.tags.ParamTag

// The NIP-90 job kinds share two shapes, one for every request (5000-5999) and one for every
// result (6000-6999) and feedback (7000), so each class's links() is one of these calls.

/**
 * The NIP-90 `["i", <data>, <input-type>, <relay>, <marker>]` inputs ([InputTag]). An `event`
 * input is the event the job works on; a `job` input is "the output of a previous job with the
 * specified event ID" (job chaining), so it points at that job's request; a `url` is a Tag
 * target. `text` and `prompt` inputs are free text, not references.
 */
private fun LinkBuilder.dvmInputs(tags: TagArray) =
    each(tags, InputTag::parse) {
        when (it.type) {
            InputTag.TYPE_EVENT -> event(Relation.INPUT, it.value, InputTag.TAG_NAME)
            InputTag.TYPE_JOB -> event(Relation.INPUT_JOB, it.value, InputTag.TAG_NAME)
            InputTag.TYPE_URL -> tag(Relation.TAG, InputTag.TAG_NAME, it.value)
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
) {
    dvmInputs(tags)
    each(tags, PTag::parse) { user(Relation.SERVICE_PROVIDER, it, PTag.TAG_NAME) }
    if (forUserParam) {
        each(tags, ParamTag::parse) { if (it.key == ParamTag.KEY_USER) user(Relation.FOR_USER, it.value, ParamTag.TAG_NAME) }
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
) {
    each(tags, ETag::parse) { event(Relation.REQUEST, it, ETag.TAG_NAME) }
    if (withInputs) dvmInputs(tags)
    each(tags, PTag::parse) { user(Relation.REQUEST_AUTHOR, it, PTag.TAG_NAME) }
}
