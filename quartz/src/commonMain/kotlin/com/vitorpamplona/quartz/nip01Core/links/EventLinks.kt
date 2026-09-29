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
package com.vitorpamplona.quartz.nip01Core.links

import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.fastForEach

/**
 * Every link [this] event states: its `AUTHOR`, its `ADDRESS` (replaceable and addressable
 * kinds: the coordinate other events' `a` tags point at), the tags any kind may carry
 * ([everyKindLinks]), then the class's own [LinkProvider.links]. An event whose kind Quartz
 * has no class for states only the first three.
 */
fun Event.allLinks(): List<Link> =
    links {
        user(Relation.AUTHOR, pubKey)
        if (this@allLinks is AddressableEvent) address(Relation.ADDRESS, addressTag())
        everyKindLinks(this@allLinks)
        if (this@allLinks is LinkProvider) links().forEach { add(it) }
    }

/**
 * The tags NIP-89, NIP-57 and NIP-30 let any event carry, so no class repeats them:
 * - `["client", <name>, <31990 address>, <relay>]` → [Relation.CLIENT] (the handler's address);
 * - `["zap", <pubkey>, <relay>, <weight>]` → [Relation.ZAP_SPLIT], a split SETTING, not a
 *   payment (so not `ZAP_RECIPIENT`), with its `weight`;
 * - `["emoji", <shortcode>, <url>, <30030 address>]` → [Relation.EMOJI_SET], the set it is from.
 */
fun LinkBuilder.everyKindLinks(event: Event) =
    event.tags.fastForEach { tag ->
        if (tag.size < 2) return@fastForEach
        when (tag[0]) {
            "client" -> if (tag.size > 2) address(Relation.CLIENT, tag[2], "client")
            "zap" -> {
                val weight = tag.getOrNull(3)?.toDoubleOrNull()
                user(Relation.ZAP_SPLIT, tag[1], "zap", weight?.let { mapOf("weight" to it) })
            }
            "emoji" -> if (tag.size > 3) address(Relation.EMOJI_SET, tag[3], "emoji")
        }
    }
