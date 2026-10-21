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
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.AuthCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.Rumor
import com.vitorpamplona.quartz.nip77Negentropy.NegCloseCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegErrMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegMsgCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegMsgMessage
import com.vitorpamplona.quartz.nip77Negentropy.NegOpenCmd
import com.vitorpamplona.quartz.utils.Hex

/*
 * Relay frames, filters and rumors, written the way `MessageSerializer`, `CommandSerializer`,
 * `FilterSerializer`, `CountResultSerializer` and `RumorSerializer` write them: same field
 * order, absent optionals left out, no whitespace.
 */

/** `["<label>"`, the start of every frame. */
private fun NostrJsonWriter.frame(label: String) {
    raw('[')
    string(label)
}

private fun NostrJsonWriter.next(value: String) {
    raw(',')
    string(value)
}

/** One relay-to-client frame; false, with nothing written, for a type this writer does not know. */
internal fun NostrJsonWriter.message(msg: Message): Boolean {
    when (msg) {
        is EventMessage -> {
            frame(msg.label())
            next(msg.subId)
            raw(',')
            eventObject(msg.event)
        }

        is NoticeMessage -> {
            frame(msg.label())
            next(msg.message)
        }

        is OkMessage -> {
            // ["OK", <event_id>, <true|false>, <message>, <?params>]
            frame(msg.label())
            next(msg.eventId)
            raw(',')
            bool(msg.success)
            next(msg.message)
            msg.params?.let { next(it) }
        }

        is AuthMessage -> {
            frame(msg.label())
            next(msg.challenge)
        }

        is NotifyMessage -> {
            frame(msg.label())
            next(msg.message)
        }

        is ClosedMessage -> {
            // ["CLOSED", <subscription_id>, <message>, <?params>]
            frame(msg.label())
            next(msg.subId)
            next(msg.message)
            msg.params?.let { next(it) }
        }

        is CountMessage -> {
            frame(msg.label())
            next(msg.queryId)
            raw(',')
            countResult(msg.result)
        }

        is EoseMessage -> {
            // NIP-67: an optional third element, the completeness hints.
            frame(msg.label())
            next(msg.subId)
            msg.hints?.let {
                raw(',')
                strings(it)
            }
        }

        is LimitsMessage -> {
            frame(msg.label())
            raw(',')
            limits(msg)
        }

        is NegMsgMessage -> {
            frame(msg.label())
            next(msg.subId)
            next(msg.message)
        }

        is NegErrMessage -> {
            frame(msg.label())
            next(msg.subId)
            next(msg.reason)
            msg.cap?.let {
                raw(',')
                number(it)
            }
        }

        else -> {
            return false
        }
    }
    raw(']')
    return true
}

/** NIP-45's result object: `{"count":…}`, plus `"approximate":true` and `"hll"` when set. */
private fun NostrJsonWriter.countResult(result: CountResult) {
    objectStart()
    field("count")
    number(result.count)
    if (result.approximate) {
        field("approximate")
        bool(true)
    }
    result.hll?.let {
        field("hll")
        string(Hex.encode(it))
    }
    objectEnd()
}

/** The LIMITS object: only the limits the relay set, in declaration order. */
private fun NostrJsonWriter.limits(msg: LimitsMessage) {
    objectStart()
    msg.canWrite?.let {
        field("can_write")
        bool(it)
    }
    msg.canRead?.let {
        field("can_read")
        bool(it)
    }
    msg.authForRead?.let {
        field("auth_for_read")
        bool(it)
    }
    msg.authForWrite?.let {
        field("auth_for_write")
        bool(it)
    }
    msg.acceptedEventKinds?.let {
        field("accepted_event_kinds")
        numbers(it)
    }
    msg.blockedEventKinds?.let {
        field("blocked_event_kinds")
        numbers(it)
    }
    msg.minPowDifficulty?.let { intField("min_pow_difficulty", it) }
    msg.maxMessageLength?.let { intField("max_message_length", it) }
    msg.maxSubscriptions?.let { intField("max_subscriptions", it) }
    msg.maxFilters?.let { intField("max_filters", it) }
    msg.maxLimit?.let { intField("max_limit", it) }
    msg.maxEventTags?.let { intField("max_event_tags", it) }
    msg.maxContentLength?.let { intField("max_content_length", it) }
    msg.createdAtMsecsAgo?.let { longField("created_at_msecs_ago", it) }
    msg.createdAtMsecsAhead?.let { longField("created_at_msecs_ahead", it) }
    msg.filterRateLimit?.let { longField("filter_rate_limit", it) }
    msg.publishingRateLimit?.let { longField("publishing_rate_limit", it) }
    msg.requiredTags?.let { tags ->
        field("required_tags")
        raw('[')
        for (k in tags.indices) {
            if (k > 0) raw(',')
            strings(tags[k])
        }
        raw(']')
    }
    objectEnd()
}

private fun NostrJsonWriter.intField(
    name: String,
    value: Int,
) {
    field(name)
    number(value)
}

private fun NostrJsonWriter.longField(
    name: String,
    value: Long,
) {
    field(name)
    number(value)
}

/** One client-to-relay frame; false, with nothing written, for a type this writer does not know. */
internal fun NostrJsonWriter.command(cmd: Command): Boolean {
    when (cmd) {
        is ReqCmd -> {
            frame(cmd.label())
            next(cmd.subId)
            filters(cmd.filters)
        }

        is EventCmd -> {
            // A command built from received JSON forwards those exact bytes.
            frame(cmd.label())
            raw(',')
            val json = cmd.eventJson
            if (json != null) raw(json) else eventObject(cmd.event)
        }

        is CloseCmd -> {
            frame(cmd.label())
            next(cmd.subId)
        }

        is AuthCmd -> {
            frame(cmd.label())
            raw(',')
            eventObject(cmd.event)
        }

        is CountCmd -> {
            frame(cmd.label())
            next(cmd.queryId)
            filters(cmd.filters)
        }

        is NegOpenCmd -> {
            frame(cmd.label())
            next(cmd.subId)
            raw(',')
            filter(cmd.filter)
            next(cmd.initialMessage)
        }

        is NegMsgCmd -> {
            frame(cmd.label())
            next(cmd.subId)
            next(cmd.message)
        }

        is NegCloseCmd -> {
            frame(cmd.label())
            next(cmd.subId)
        }

        else -> {
            return false
        }
    }
    raw(']')
    return true
}

private fun NostrJsonWriter.filters(filters: List<Filter>) {
    for (k in filters.indices) {
        raw(',')
        filter(filters[k])
    }
}

/** kinds, ids, authors, each `#x` then each `&x`, since, until, limit, search; set fields only. */
internal fun NostrJsonWriter.filter(filter: Filter) {
    objectStart()
    filter.kinds?.let {
        field("kinds")
        numbers(it)
    }
    filter.ids?.let {
        field("ids")
        strings(it)
    }
    filter.authors?.let {
        field("authors")
        strings(it)
    }
    filter.tags?.let { tagFields('#', it) }
    filter.tagsAll?.let { tagFields('&', it) }
    filter.since?.let { longField("since", it) }
    filter.until?.let { longField("until", it) }
    filter.limit?.let { intField("limit", it) }
    filter.search?.let {
        field("search")
        string(it)
    }
    objectEnd()
}

/** `"#e":[…]` per entry; the tag name comes from the caller, so it is escaped like a value. */
private fun NostrJsonWriter.tagFields(
    prefix: Char,
    tags: Map<String, List<String>>,
) {
    for ((name, values) in tags) {
        fieldPrefixed(prefix, name)
        strings(values)
    }
}

/** A rumor's set fields, in event order and without `sig`. */
internal fun NostrJsonWriter.rumor(rumor: Rumor) {
    objectStart()
    rumor.id?.let {
        field("id")
        string(it)
    }
    rumor.pubKey?.let {
        field("pubkey")
        string(it)
    }
    rumor.createdAt?.let { longField("created_at", it) }
    rumor.kind?.let { intField("kind", it) }
    rumor.tags?.let {
        field("tags")
        tagArray(it)
    }
    rumor.content?.let {
        field("content")
        string(it)
    }
    objectEnd()
}
