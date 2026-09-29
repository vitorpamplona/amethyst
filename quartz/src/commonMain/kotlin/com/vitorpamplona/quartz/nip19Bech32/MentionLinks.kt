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
package com.vitorpamplona.quartz.nip19Bech32

import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkBuilder
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.graph.props.NoProps
import com.vitorpamplona.quartz.nip19Bech32.entities.Entity
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub

/**
 * NIP-27 `nostr:` URIs in [content], as [Relation.MENTION]s (or [relation]) `via` content. An
 * nsec is never a link: its hex is a private key.
 */
fun LinkBuilder.contentMentions(
    content: String,
    relation: Relation<NoProps> = Relation.MENTION,
) {
    if (!content.contains("nostr:")) return
    contentMentions(Nip19Parser.parseAll(content), relation)
}

/** [contentMentions] from entities a class already parsed (and cached) out of its content. */
fun LinkBuilder.contentMentions(
    entities: List<Entity>,
    relation: Relation<NoProps> = Relation.MENTION,
) = entities.forEach { entity ->
    when (entity) {
        is NPub -> user(relation, entity.hex, Link.VIA_CONTENT)
        is NProfile -> user(relation, entity.hex, Link.VIA_CONTENT)
        is NNote -> event(relation, entity.hex, Link.VIA_CONTENT)
        is NEvent -> event(relation, entity.hex, Link.VIA_CONTENT)
        is NAddress -> address(relation, entity.aTag(), Link.VIA_CONTENT)
        is NEmbed -> event(relation, entity.event.id, Link.VIA_CONTENT)
        else -> Unit
    }
}
