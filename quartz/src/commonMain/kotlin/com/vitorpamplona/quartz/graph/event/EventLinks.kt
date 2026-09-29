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
package com.vitorpamplona.quartz.graph.event

import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkBuilder
import com.vitorpamplona.quartz.graph.LinkProvider
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.graph.links
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip30CustomEmoji.emojiSetLinks
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitLinks
import com.vitorpamplona.quartz.nip89AppHandlers.clientTag.clientLinks

// The one place that knows every event's links, so it depends on the NIPs whose tags any kind may
// carry. It sits in its own package so `graph` itself depends on no NIP: the NIPs import `graph`
// to state their links, and this package imports them back to assemble an event's.

/**
 * Every link [this] event states: its `AUTHOR`, its `ADDRESS` (replaceable and addressable
 * kinds: the coordinate other events' `a` tags point at), the tags any kind may carry
 * ([everyKindLinks]), then the class's own [LinkProvider.links]. An event whose kind Quartz
 * has no class for states only the first three.
 */
fun Event.allLinks(): List<Link<*>> =
    links {
        user(Relation.AUTHOR, pubKey)
        if (this@allLinks is AddressableEvent) address(Relation.ADDRESS, addressTag())
        everyKindLinks(this@allLinks)
        if (this@allLinks is LinkProvider) links().forEach { add(it) }
    }

/** The tags NIP-89, NIP-57 and NIP-30 let any event carry, read by their own packages' helpers, so no class repeats them. */
fun LinkBuilder.everyKindLinks(event: Event) {
    clientLinks(event.tags)
    zapSplitLinks(event.tags)
    emojiSetLinks(event.tags)
}
