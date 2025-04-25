/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.ammolite.relays.filters

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.jackson.EventMapper
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.filters.FilterMatcher
import com.vitorpamplona.quartz.nip01Core.relay.filters.FilterSerializer

/**
 * This is a nostr filter with per-relay authors list and since parameters
 */
class SincePerRelayFilter(
    val ids: List<String>? = null,
    val authors: List<String>? = null,
    val kinds: List<Int>? = null,
    val tags: Map<String, List<String>>? = null,
    val since: Map<String, EOSETime>? = null,
    val until: Long? = null,
    val limit: Int? = null,
    val search: String? = null,
) : IPerRelayFilter {
    override fun isValidFor(url: String) = true

    override fun toRelay(forRelay: String) = Filter(ids, authors, kinds, tags, since?.get(forRelay)?.time, until, limit, search)

    override fun toJson(forRelay: String) = FilterSerializer.toJson(ids, authors, kinds, tags, since?.get(forRelay)?.time, until, limit, search)

    override fun match(
        event: Event,
        forRelay: String,
    ) = FilterMatcher.match(event, ids, authors, kinds, tags, since?.get(forRelay)?.time, until)

    override fun toDebugJson(): String {
        val factory = JsonNodeFactory.instance
        val obj = FilterSerializer.toJsonObject(ids, authors, kinds, tags, null, until, limit, search)

        since?.run {
            if (isNotEmpty()) {
                val jsonObjectSince = factory.objectNode()
                entries.forEach { sincePairs ->
                    jsonObjectSince.put(sincePairs.key, "${sincePairs.value}")
                }
                obj.replace("since", jsonObjectSince)
            }
        }
        return EventMapper.toJson(obj)
    }
}
