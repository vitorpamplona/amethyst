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
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.jackson.EventMapper
import com.vitorpamplona.quartz.nip01Core.relays.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relays.filters.FilterMatcher
import com.vitorpamplona.quartz.nip01Core.relays.filters.FilterSerializer

/**
 * This is a nostr filter with per-relay authors list and since parameters
 */
class SinceAuthorPerRelayFilter(
    val ids: List<HexKey>? = null,
    val authors: Map<String, List<HexKey>>? = null,
    val kinds: List<Int>? = null,
    val tags: Map<String, List<String>>? = null,
    val since: Map<String, EOSETime>? = null,
    val until: Long? = null,
    val limit: Int? = null,
    val search: String? = null,
) : IPerRelayFilter {
    // This only exists because some relays consider empty arrays as null and return everything.
    // So, if there is an author list, but no list for the specific relay or if the list is empty
    // don't send it.
    override fun isValidFor(forRelay: String) = authors == null || !authors[forRelay].isNullOrEmpty()

    override fun toRelay(forRelay: String) = Filter(ids, authors?.get(forRelay), kinds, tags, since?.get(forRelay)?.time, until, limit, search)

    override fun toJson(forRelay: String): String = FilterSerializer.toJson(ids, authors?.get(forRelay), kinds, tags, since?.get(forRelay)?.time, until, limit, search)

    override fun match(
        event: Event,
        forRelay: String,
    ) = FilterMatcher.match(event, ids, authors?.get(forRelay), kinds, tags, since?.get(forRelay)?.time, until)

    override fun toDebugJson(): String {
        val factory = JsonNodeFactory.instance
        val obj = FilterSerializer.toJsonObject(ids, null, kinds, tags, null, until, limit, search)
        authors?.run {
            if (isNotEmpty()) {
                val jsonObjectPerRelayAuthors = factory.objectNode()
                entries.forEach { relayAuthorPairs ->
                    jsonObjectPerRelayAuthors.put(relayAuthorPairs.key, factory.arrayNode(relayAuthorPairs.value.size).apply { relayAuthorPairs.value.forEach { add(it) } })
                }
                obj.put("authors", jsonObjectPerRelayAuthors)
            }
        }

        since?.run {
            if (isNotEmpty()) {
                val jsonObjectSince = factory.objectNode()
                entries.forEach { sincePairs ->
                    jsonObjectSince.put(sincePairs.key, "${sincePairs.value}")
                }
                obj.put("since", jsonObjectSince)
            }
        }
        return EventMapper.mapper.writeValueAsString(obj)
    }
}
