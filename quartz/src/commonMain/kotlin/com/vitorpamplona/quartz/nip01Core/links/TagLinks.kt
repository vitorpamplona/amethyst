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

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip01Core.links.props.LinkProps
import com.vitorpamplona.quartz.nip01Core.links.props.NoProps
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QAddressableTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QEventTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QTag
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.Entity
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub

// Building blocks for the shapes that recur across kinds. Each reads the tags through their Tag
// class; a class's links() still decides which parsed value means which relation.

/**
 * Every tag [parse] accepts, handed to [block]. The way a `links()` walks its tags: the Tag
 * class's parser says which tags are its own and what they hold, e.g.
 * `each(tags, PTag::parse) { user(Relation.MENTION, it, PTag.TAG_NAME) }`.
 */
inline fun <T : Any> LinkBuilder.each(
    tags: TagArray,
    parse: (Array<String>) -> T?,
    block: LinkBuilder.(T) -> Unit,
) = tags.fastForEach { tag -> parse(tag)?.let { block(it) } }

/** NIP-24 `t` tags ([HashtagTag]). Hashtags are case-insensitive, so the value is lowercased: #Nostr is #nostr. */
fun LinkBuilder.hashtags(tags: TagArray) = each(tags, HashtagTag::parse) { tag(Relation.HASHTAG, HashtagTag.TAG_NAME, it.lowercase()) }

/** NIP-18 `q` tags ([QTag]): an event or an address. */
fun LinkBuilder.quotes(
    tags: TagArray,
    relation: Relation<NoProps> = Relation.QUOTE,
) = each(tags, QTag::parse) {
    when (it) {
        is QEventTag -> event(relation, it, QTag.TAG_NAME)
        is QAddressableTag -> address(relation, it, QTag.TAG_NAME)
    }
}

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

// Raw-slot readers from the first implementation, kept only until every links() reads its tags
// through their Tag classes. Do not use them in new code.

@Deprecated("Read the tags through their Tag class: each(tags, ETag::parse) { event(relation, it, ETag.TAG_NAME) }")
fun <P : LinkProps> LinkBuilder.eventTags(
    relation: Relation<P>,
    tags: TagArray,
    name: String = "e",
    props: P? = null,
) = tags.fastForEach { if (it.size > 1 && it[0] == name) event(relation, it[1], name, props) }

@Deprecated("Read the tags through their Tag class: each(tags, PTag::parse) { user(relation, it, PTag.TAG_NAME) }")
fun <P : LinkProps> LinkBuilder.userTags(
    relation: Relation<P>,
    tags: TagArray,
    name: String = "p",
    props: P? = null,
) = tags.fastForEach { if (it.size > 1 && it[0] == name) user(relation, it[1], name, props) }

@Deprecated("Read the tags through their Tag class: each(tags, ATag::parse) { address(relation, it, ATag.TAG_NAME) }")
fun <P : LinkProps> LinkBuilder.addressTags(
    relation: Relation<P>,
    tags: TagArray,
    name: String = "a",
    props: P? = null,
) = tags.fastForEach { if (it.size > 1 && it[0] == name) address(relation, it[1], name, props) }

@Deprecated("Read the tags through their Tag class and pass its TAG_NAME: each(tags, XTag::parse) { tag(relation, XTag.TAG_NAME, it) }")
fun <P : LinkProps> LinkBuilder.valueTags(
    relation: Relation<P>,
    tags: TagArray,
    name: String,
    props: P? = null,
) = tags.fastForEach { if (it.size > 1 && it[0] == name) tag(relation, name, it[1], name, props) }
