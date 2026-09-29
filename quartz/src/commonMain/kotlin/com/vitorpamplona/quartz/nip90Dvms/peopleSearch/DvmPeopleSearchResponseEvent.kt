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
package com.vitorpamplona.quartz.nip90Dvms.peopleSearch

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkProvider
import com.vitorpamplona.quartz.graph.links
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip90Dvms.tags.dvmResultLinks
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class DvmPeopleSearchResponseEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    LinkProvider {
    override fun links(): List<Link<*>> = links { dvmResultLinks(tags) }

    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    var people: List<HexKey>? = null

    fun innerTags(): List<HexKey> {
        if (content.isEmpty()) {
            return listOf()
        }

        people?.let {
            return it
        }

        try {
            people =
                OptimizedJsonMapper.fromJsonToTagArray(content).mapNotNull {
                    if (it.size > 1 && it[0] == "p") {
                        it[1]
                    } else {
                        null
                    }
                }
        } catch (e: Throwable) {
            Log.w("DvmPeopleSearchResponseEvent") { "Error parsing the JSON ${e.message}" }
        }

        return people ?: listOf()
    }

    companion object {
        const val KIND = 6303

        fun build(
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<DvmPeopleSearchResponseEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            initializer()
        }
    }
}

@Deprecated(
    "Renamed to DvmPeopleSearchResponseEvent. NIP-90 Data Vending Machine events use the Dvm prefix.",
    ReplaceWith("DvmPeopleSearchResponseEvent", "com.vitorpamplona.quartz.nip90Dvms.peopleSearch.DvmPeopleSearchResponseEvent"),
)
typealias NIP90PeopleSearchResponseEvent = DvmPeopleSearchResponseEvent
