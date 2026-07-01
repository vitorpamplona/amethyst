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
package com.vitorpamplona.quartz.nipXXPodcasting20.episode.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.podcasts.PodcastPerson
import com.vitorpamplona.quartz.utils.ensure

/**
 * Podcasting-2.0 episode person credit: `["person", "<name>", "<role>", "<img>", "<href>"]`.
 *
 * Only the name (slot 1) is required; role/img/href are optional and carried positionally, with
 * empty strings standing in for absent middle values. Maps to the shared [PodcastPerson] holder.
 * (The show-level `podcast:person` `group` attribute isn't carried on the episode tag — it's
 * organizational metadata that lives in the show's JSON when present.)
 */
class PersonTag {
    companion object {
        const val TAG_NAME = "person"

        fun parse(tag: Array<String>): PodcastPerson? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            return PodcastPerson(
                name = tag[1],
                role = tag.getOrNull(2)?.takeIf { it.isNotEmpty() },
                img = tag.getOrNull(3)?.takeIf { it.isNotEmpty() },
                href = tag.getOrNull(4)?.takeIf { it.isNotEmpty() },
            )
        }

        fun assemble(person: PodcastPerson): Array<String> {
            // Trim trailing empties so a person with only a name is a 2-element tag, but keep empty
            // placeholders in the middle so href stays in slot 4 when role/img are missing.
            val slots = listOf(person.name, person.role ?: "", person.img ?: "", person.href ?: "")
            val lastNonEmpty = slots.indexOfLast { it.isNotEmpty() }.coerceAtLeast(0)
            return (listOf(TAG_NAME) + slots.subList(0, lastNonEmpty + 1)).toTypedArray()
        }
    }
}
