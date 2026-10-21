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

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.AuthMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountResult
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.LimitsMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NotifyMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip45Count.HyperLogLog
import com.vitorpamplona.quartz.nip77Negentropy.NegErrMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegMsgMessage

/*
 * Relay-to-client frames, read the way `MessageDeserializer` reads them, plus the value
 * helpers the command reader shares. Where `MessageDeserializer` reads a JSON tree (COUNT's
 * result, LIMITS) these follow Jackson's `JsonNode` conversions.
 */

/** One relay-to-client frame: `["EVENT", …]`, `["EOSE", …]`, `["OK", …]` and the rest. */
internal fun NostrJsonReader.readMessageFrame(): Message {
    val label = openFrame() ?: fail("message type is not a string")
    val message =
        when (label) {
            EventMessage.LABEL -> {
                val subId = requiredString()
                requiredObjectStart()
                EventMessage(subId, readEventObject(2))
            }

            EoseMessage.LABEL -> {
                val subId = requiredString()
                EoseMessage(subId, eoseHints())
            }

            NoticeMessage.LABEL -> {
                NoticeMessage(requiredString())
            }

            OkMessage.LABEL -> {
                val eventId = requiredString()
                val success = requiredBoolean()
                val text = optionalText()
                OkMessage(eventId, success, text, optionalParams())
            }

            AuthMessage.LABEL -> {
                AuthMessage(requiredString())
            }

            NotifyMessage.LABEL -> {
                NotifyMessage(requiredString())
            }

            ClosedMessage.LABEL -> {
                val subId = requiredString()
                val text = optionalText()
                ClosedMessage(subId, text, optionalParams())
            }

            CountMessage.LABEL -> {
                val queryId = requiredString()
                if (!nextElement()) fail("missing the count result")
                CountMessage(queryId, countResult())
            }

            LimitsMessage.LABEL -> {
                // A payload-less ["LIMITS"], or one that is not an object, is an empty LIMITS.
                if (nextElement()) {
                    if (s[i] == '{') {
                        limits()
                    } else {
                        skipValue(1)
                        LimitsMessage()
                    }
                } else {
                    LimitsMessage()
                }
            }

            NegMsgMessage.LABEL -> {
                val subId = requiredString()
                NegMsgMessage(subId, requiredString())
            }

            NegErrMessage.LABEL -> {
                val subId = requiredString()
                val reason = optionalText()
                NegErrMessage(subId, reason, negErrCap())
            }

            else -> {
                fail("Message $label is not supported")
            }
        }
    finishFrame()
    return message
}

/** The next element, which must be `true` or `false`. */
internal fun NostrJsonReader.requiredBoolean(): Boolean {
    if (!nextElement()) fail("missing element")
    return when (s[i]) {
        't' -> {
            literal("true", "")
            true
        }
        'f' -> {
            literal("false", "")
            false
        }
        else -> fail("expected true or false")
    }
}

/**
 * An optional message text (OK, CLOSED, NEG-ERR): "" when absent, null or not a string, like
 * Jackson's `nextTextValue() ?: ""`. An array or object there fails: Jackson would go on
 * reading inside it.
 */
internal fun NostrJsonReader.optionalText(): String {
    if (!nextElement()) return ""
    return when (s[i]) {
        '"' -> readString()
        '{', '[' -> fail("expected a string")
        else -> {
            skipValue(1)
            ""
        }
    }
}

/**
 * The optional `params` of an OK or CLOSED: a string, or an integer by its text. Anything
 * else, a container included, reads as absent.
 */
internal fun NostrJsonReader.optionalParams(): String? {
    if (!nextElement()) return null
    return when (s[i]) {
        '"' -> readString()
        '-', in '0'..'9' -> {
            val start = i
            if (scanNumber()) null else s.substring(start, i)
        }
        else -> {
            skipValue(1)
            null
        }
    }
}

/** EOSE's optional NIP-67 hints: the strings of an array (other entries dropped), or null. */
private fun NostrJsonReader.eoseHints(): List<String>? {
    if (!nextElement()) return null
    if (s[i] != '[') {
        skipValue(1)
        return null
    }
    i++
    val hints = ArrayList<String>(2)
    if (peek() == ']') {
        i++
        return hints
    }
    while (true) {
        if (peek() == '"') hints.add(readString()) else skipValue(2)
        val c = peek()
        i++
        if (c == ',') continue
        if (c == ']') return hints
        i--
        fail("expected ',' or ']'")
    }
}

/** NEG-ERR's optional cap: an integer, else absent. */
private fun NostrJsonReader.negErrCap(): Long? {
    if (!nextElement()) return null
    if (s[i] == '-' || s[i] in '0'..'9') {
        val start = i
        if (scanNumber()) return null
        return s.substring(start, i).toLongOrNull() ?: fail("number out of range of long")
    }
    skipValue(1)
    return null
}

/** COUNT's result, read like `CountResultDeserializer` over a JSON tree. */
private fun NostrJsonReader.countResult(): CountResult {
    if (s[i] != '{') {
        // Any other value is a tree without fields: zero, not approximate.
        skipValue(1)
        return CountResult(0)
    }
    var count = 0
    var approximate = false
    var hll: ByteArray? = null
    forEachField { key ->
        when (key) {
            "count" -> count = nodeAsInt()
            "approximate" -> approximate = nodeAsBoolean()
            "hll" -> hll = HyperLogLog.decode(nodeAsText())
            else -> skipValue(2)
        }
    }
    return CountResult(count, approximate, hll)
}

/** LIMITS' object, read like `LimitsDeserializer`: a field of the wrong type reads as null. */
private fun NostrJsonReader.limits(): LimitsMessage {
    var canWrite: Boolean? = null
    var canRead: Boolean? = null
    var authForRead: Boolean? = null
    var authForWrite: Boolean? = null
    var acceptedEventKinds: List<Int>? = null
    var blockedEventKinds: List<Int>? = null
    var minPowDifficulty: Int? = null
    var maxMessageLength: Int? = null
    var maxSubscriptions: Int? = null
    var maxFilters: Int? = null
    var maxLimit: Int? = null
    var maxEventTags: Int? = null
    var maxContentLength: Int? = null
    var createdAtMsecsAgo: Long? = null
    var createdAtMsecsAhead: Long? = null
    var filterRateLimit: Long? = null
    var publishingRateLimit: Long? = null
    var requiredTags: List<List<String>>? = null
    forEachField { key ->
        when (key) {
            "can_write" -> canWrite = typedBoolean()
            "can_read" -> canRead = typedBoolean()
            "auth_for_read" -> authForRead = typedBoolean()
            "auth_for_write" -> authForWrite = typedBoolean()
            "accepted_event_kinds" -> acceptedEventKinds = typedIntList()
            "blocked_event_kinds" -> blockedEventKinds = typedIntList()
            "min_pow_difficulty" -> minPowDifficulty = typedInt()
            "max_message_length" -> maxMessageLength = typedInt()
            "max_subscriptions" -> maxSubscriptions = typedInt()
            "max_filters" -> maxFilters = typedInt()
            "max_limit" -> maxLimit = typedInt()
            "max_event_tags" -> maxEventTags = typedInt()
            "max_content_length" -> maxContentLength = typedInt()
            "created_at_msecs_ago" -> createdAtMsecsAgo = typedLong()
            "created_at_msecs_ahead" -> createdAtMsecsAhead = typedLong()
            "filter_rate_limit" -> filterRateLimit = typedLong()
            "publishing_rate_limit" -> publishingRateLimit = typedLong()
            "required_tags" -> requiredTags = requiredTagsList()
            else -> skipValue(2)
        }
    }
    return LimitsMessage(
        canWrite = canWrite,
        canRead = canRead,
        authForRead = authForRead,
        authForWrite = authForWrite,
        acceptedEventKinds = acceptedEventKinds,
        blockedEventKinds = blockedEventKinds,
        minPowDifficulty = minPowDifficulty,
        maxMessageLength = maxMessageLength,
        maxSubscriptions = maxSubscriptions,
        maxFilters = maxFilters,
        maxLimit = maxLimit,
        maxEventTags = maxEventTags,
        maxContentLength = maxContentLength,
        createdAtMsecsAgo = createdAtMsecsAgo,
        createdAtMsecsAhead = createdAtMsecsAhead,
        filterRateLimit = filterRateLimit,
        publishingRateLimit = publishingRateLimit,
        requiredTags = requiredTags,
    )
}

// ---- shared value helpers (messages and commands)

/**
 * Calls [action] with every key of the object at the cursor, with the cursor on the key's value,
 * which [action] must read or skip. Leaves the cursor past the object.
 */
internal inline fun NostrJsonReader.forEachField(action: (key: String) -> Unit) {
    if (peek() != '{') fail("expected an object")
    i++
    if (peek() == '}') {
        i++
        return
    }
    while (true) {
        if (peek() != '"') fail("expected a field name")
        i++
        val key = readStringBody()
        expect(':')
        peek()
        action(key)
        val c = peek()
        i++
        if (c == ',') continue
        if (c == '}') return
        i--
        fail("expected ',' or '}'")
    }
}

/** Consumes a `null` at the cursor and returns true, or returns false for any other value. */
internal fun NostrJsonReader.takeNull(): Boolean {
    if (s[i] != 'n') return false
    literal("null", "")
    return true
}

/** True when the value at the cursor is a number. */
internal fun NostrJsonReader.atNumber(): Boolean = s[i] == '-' || s[i] in '0'..'9'

private val INTEGER_TEXT = Regex("[+-]?[0-9]+")

/** An integer's text as Jackson's `JsonNode.asText()` writes it: canonical decimal. */
private fun canonicalInteger(literal: String): String = literal.toLongOrNull()?.toString() ?: literal

/** A number as a `JsonNode.intValue()`: in range, a fraction truncated. Out of range fails (Jackson wraps). */
private fun NostrJsonReader.nodeNumberAsInt(): Int {
    val start = i
    val real = scanNumber()
    val text = s.substring(start, i)
    if (!real) {
        val v = text.toLongOrNull() ?: fail("number out of range of int")
        if (v < Int.MIN_VALUE || v > Int.MAX_VALUE) fail("number out of range of int")
        return v.toInt()
    }
    val d = text.toDouble()
    if (!(d >= Int.MIN_VALUE.toDouble() && d <= Int.MAX_VALUE.toDouble())) fail("number out of range of int")
    return d.toInt()
}

/** A number as a `JsonNode.longValue()`: in range, a fraction truncated. Out of range fails (Jackson wraps). */
private fun NostrJsonReader.nodeNumberAsLong(): Long {
    val start = i
    val real = scanNumber()
    val text = s.substring(start, i)
    if (!real) return text.toLongOrNull() ?: fail("number out of range of long")
    val d = text.toDouble()
    if (!(d >= Long.MIN_VALUE.toDouble() && d <= Long.MAX_VALUE.toDouble())) fail("number out of range of long")
    return d.toLong()
}

/** Any value as `JsonNode.asInt()`: numbers, `true` as 1, plain integer strings; 0 for the rest. */
private fun NostrJsonReader.nodeAsInt(): Int =
    when (s[i]) {
        '"' -> {
            // Jackson parses an integer string; anything fancier fails here instead.
            val text = readString().trim()
            if (!INTEGER_TEXT.matches(text)) fail("expected an integer")
            text.removePrefix("+").toIntOrNull() ?: fail("number out of range of int")
        }
        't' -> {
            literal("true", "")
            1
        }
        '-', in '0'..'9' -> nodeNumberAsInt()
        else -> {
            skipValue(2)
            0
        }
    }

/** Any value as `JsonNode.asBoolean()`: booleans, non-zero integers, "true"/"false" strings; false for the rest. */
private fun NostrJsonReader.nodeAsBoolean(): Boolean =
    when (s[i]) {
        '"' -> readString().trim() == "true"
        't' -> {
            literal("true", "")
            true
        }
        '-', in '0'..'9' -> {
            val start = i
            if (scanNumber()) fail("expected an integer or a boolean")
            s.substring(start, i).any { it in '1'..'9' }
        }
        else -> {
            skipValue(2)
            false
        }
    }

/** Any value as `JsonNode.asText()`: strings as they are, scalars by their text, containers as "". */
private fun NostrJsonReader.nodeAsText(): String =
    when (s[i]) {
        '"' -> readString()
        't' -> literal("true", "true")
        'f' -> literal("false", "false")
        'n' -> literal("null", "null")
        '-', in '0'..'9' -> {
            val start = i
            if (scanNumber()) fail("expected an integer")
            canonicalInteger(s.substring(start, i))
        }
        else -> {
            skipValue(2)
            ""
        }
    }

/** A boolean, or null for any other value. */
private fun NostrJsonReader.typedBoolean(): Boolean? =
    when (s[i]) {
        't' -> {
            literal("true", "")
            true
        }
        'f' -> {
            literal("false", "")
            false
        }
        else -> {
            skipValue(2)
            null
        }
    }

/** A number as an int, or null for any other value. */
private fun NostrJsonReader.typedInt(): Int? {
    if (atNumber()) return nodeNumberAsInt()
    skipValue(2)
    return null
}

/** A number as a long, or null for any other value. */
private fun NostrJsonReader.typedLong(): Long? {
    if (atNumber()) return nodeNumberAsLong()
    skipValue(2)
    return null
}

/** The numbers of an array as ints (other entries dropped), or null when it is not an array. */
private fun NostrJsonReader.typedIntList(): List<Int>? {
    if (s[i] != '[') {
        skipValue(2)
        return null
    }
    i++
    val out = ArrayList<Int>()
    if (peek() == ']') {
        i++
        return out
    }
    while (true) {
        peek()
        if (atNumber()) out.add(nodeNumberAsInt()) else skipValue(3)
        val c = peek()
        i++
        if (c == ',') continue
        if (c == ']') return out
        i--
        fail("expected ',' or ']'")
    }
}

/**
 * LIMITS' `required_tags`: every entry that is an array becomes the text of its scalar values;
 * any other entry an empty tag. Null when it is not an array.
 */
private fun NostrJsonReader.requiredTagsList(): List<List<String>>? {
    if (s[i] != '[') {
        skipValue(2)
        return null
    }
    i++
    val out = ArrayList<List<String>>()
    if (peek() == ']') {
        i++
        return out
    }
    while (true) {
        if (s[i] == '[') {
            i++
            val tag = ArrayList<String>()
            if (peek() == ']') {
                i++
            } else {
                while (true) {
                    if (s[i] == '{' || s[i] == '[') skipValue(4) else tag.add(nodeAsText())
                    val c = peek()
                    i++
                    if (c == ',') {
                        peek()
                        continue
                    }
                    if (c == ']') break
                    i--
                    fail("expected ',' or ']'")
                }
            }
            out.add(tag)
        } else {
            skipValue(3)
            out.add(emptyList())
        }
        val c = peek()
        i++
        if (c == ',') {
            peek()
            continue
        }
        if (c == ']') return out
        i--
        fail("expected ',' or ']'")
    }
}
