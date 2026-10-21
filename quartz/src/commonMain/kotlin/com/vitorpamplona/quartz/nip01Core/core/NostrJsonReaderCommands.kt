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
package com.vitorpamplona.quartz.nip01Core.core

import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.AuthCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nip77Negentropy.NegCloseCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegMsgCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegOpenCmd

/*
 * Client-to-relay frames and their filters, read the way `CommandDeserializer` and
 * `ManualFilterDeserializer` read them.
 */

/** One client-to-relay frame: `["REQ", …]`, `["EVENT", …]`, `["CLOSE", …]` and the rest. */
internal fun NostrJsonReader.readCommandFrame(): Command {
    val label = openFrame() ?: fail("command type is not a string")
    val command =
        when (label) {
            ReqCmd.LABEL -> {
                val subId = requiredString()
                ReqCmd(subId, filtersToTheEnd())
            }

            CountCmd.LABEL -> {
                val queryId = requiredString()
                CountCmd(queryId, filtersToTheEnd())
            }

            EventCmd.LABEL -> {
                requiredObjectStart()
                EventCmd(readEventObject(2))
            }

            CloseCmd.LABEL -> {
                CloseCmd(requiredString())
            }

            AuthCmd.LABEL -> {
                requiredObjectStart()
                AuthCmd(readEventObject(2) as? RelayAuthEvent ?: fail("AUTH needs a relay authentication event"))
            }

            NegOpenCmd.LABEL -> {
                val subId = requiredString()
                requiredObjectStart()
                val filter = readFilterObject(2)
                NegOpenCmd(subId, filter, requiredString())
            }

            NegMsgCmd.LABEL -> {
                val subId = requiredString()
                NegMsgCmd(subId, requiredString())
            }

            NegCloseCmd.LABEL -> {
                NegCloseCmd(requiredString())
            }

            else -> {
                fail("Command $label is not supported")
            }
        }
    finishFrame()
    return command
}

/** Every remaining element of a REQ or COUNT, each a filter object. */
private fun NostrJsonReader.filtersToTheEnd(): List<Filter> {
    val filters = mutableListOf<Filter>()
    while (nextElement()) {
        if (s[i] != '{') fail("expected a filter object")
        filters.add(readFilterObject(2))
    }
    return filters
}

/**
 * One filter object, read like `ManualFilterDeserializer`: lenient lists (entries of the wrong
 * type dropped, a list that is not an array read as empty), `null` for `since`, `until`,
 * `limit` and `search` leaving them unset, and unknown fields skipped. [depth] is the object's
 * nesting depth.
 */
internal fun NostrJsonReader.readFilterObject(depth: Int): Filter {
    var ids: List<String>? = null
    var authors: List<String>? = null
    var kinds: List<Int>? = null
    var tags: MutableMap<String, List<String>>? = null
    var tagsAll: MutableMap<String, List<String>>? = null
    var since: Long? = null
    var until: Long? = null
    var limit: Int? = null
    var search: String? = null

    forEachField { key ->
        when {
            key == "ids" -> {
                ids = lenientStrings(depth)
            }

            key == "authors" -> {
                authors = lenientStrings(depth)
            }

            key == "kinds" -> {
                kinds = lenientInts(depth)
            }

            key == "since" -> {
                if (!takeNull()) since = readLong()
            }

            key == "until" -> {
                if (!takeNull()) until = readLong()
            }

            key == "limit" -> {
                if (!takeNull()) limit = readInt()
            }

            key == "search" -> {
                if (!takeNull()) search = scalarText()
            }

            key.length > 1 && key[0] == '#' -> {
                val map = tags ?: mutableMapOf<String, List<String>>().also { tags = it }
                map[key.substring(1)] = lenientStrings(depth)
            }

            key.length > 1 && key[0] == '&' -> {
                val map = tagsAll ?: mutableMapOf<String, List<String>>().also { tagsAll = it }
                map[key.substring(1)] = lenientStrings(depth)
            }

            else -> {
                skipValue(depth)
            }
        }
    }

    return Filter(
        ids = ids,
        authors = authors,
        kinds = kinds,
        tags = tags,
        tagsAll = tagsAll,
        since = since,
        until = until,
        limit = limit,
        search = search,
    )
}

/**
 * A string or a number by its text, or `true`/`false`, like Jackson's `getText()`. An array or
 * object fails: Jackson would go on reading inside it.
 */
private fun NostrJsonReader.scalarText(): String =
    when (s[i]) {
        '"' -> readString()
        't' -> literal("true", "true")
        'f' -> literal("false", "false")
        '{', '[' -> fail("expected a string")
        else -> numberText()
    }

/** The strings of an array (other entries dropped); empty when the value is not an array. */
private fun NostrJsonReader.lenientStrings(depth: Int): MutableList<String> {
    val out = mutableListOf<String>()
    if (s[i] != '[') {
        skipValue(depth)
        return out
    }
    i++
    if (peek() == ']') {
        i++
        return out
    }
    while (true) {
        if (peek() == '"') out.add(readString()) else skipValue(depth + 1)
        val c = peek()
        i++
        if (c == ',') continue
        if (c == ']') return out
        i--
        fail("expected ',' or ']'")
    }
}

/**
 * The numbers of an array as ints, like Jackson's `intValue` (a fraction truncated, out of range
 * fails); other entries dropped. Empty when the value is not an array.
 */
private fun NostrJsonReader.lenientInts(depth: Int): MutableList<Int> {
    val out = mutableListOf<Int>()
    if (s[i] != '[') {
        skipValue(depth)
        return out
    }
    i++
    if (peek() == ']') {
        i++
        return out
    }
    while (true) {
        peek()
        if (atNumber()) out.add(readInt()) else skipValue(depth + 1)
        val c = peek()
        i++
        if (c == ',') continue
        if (c == ']') return out
        i--
        fail("expected ',' or ']'")
    }
}
