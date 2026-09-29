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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.links.props.LinkProps

/**
 * One statement an event makes about something else: "my [relation] is [target]".
 *
 * A link always starts at the event that states it (the event is the provenance: its author,
 * its time and whatever supersedes it hang off it), so the source is implicit.
 *
 * @property via where the reference was written: the tag name, or [VIA_CONTENT] for a
 * `nostr:` URI in the text (NIP-27).
 * @property props the values that qualify this one link (a report's category, a zap's amount,
 * a member's roles), of the type its [relation] declares; null when there are none.
 * [LinkProps.toMap] is their store form.
 */
data class Link<P : LinkProps>(
    val relation: Relation<P>,
    val target: LinkTarget,
    val via: String? = null,
    val props: P? = null,
) {
    companion object {
        /** [via] for a reference written as a `nostr:` URI in the content (NIP-27). */
        const val VIA_CONTENT = "content"
    }
}

/**
 * What a link points at. Ids and pubkeys are 64-char lowercase hex and addresses are
 * `kind:pubkey:d` coordinates: [LinkBuilder] rejects anything else, so a consumer can key nodes
 * on these values directly.
 */
sealed interface LinkTarget {
    data class Event(
        val id: HexKey,
    ) : LinkTarget

    data class User(
        val pubkey: HexKey,
    ) : LinkTarget

    /** `kind:pubkey:d`, as NIP-01 writes it in an `a` tag (d is empty for replaceable kinds). */
    data class Address(
        val value: String,
    ) : LinkTarget

    /**
     * A value that is not an event, an address or a user: a hashtag, a URL, an external id, a
     * NIP-29 group id. [name] is the tag it was written in (`t`, `r`, `i`, `h`…), which says how
     * to read [value].
     */
    data class Tag(
        val name: String,
        val value: String,
    ) : LinkTarget
}
