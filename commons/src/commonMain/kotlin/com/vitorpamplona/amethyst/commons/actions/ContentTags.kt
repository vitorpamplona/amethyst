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
package com.vitorpamplona.amethyst.commons.actions

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip18Reposts.quotes.quote
import com.vitorpamplona.quartz.nip19Bech32.entities.Entity
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub

/**
 * Cache-free tagging of what a user typed into a note, matching the tags the
 * Amethyst composer (`ShortNotePostViewModel`) derives from the text: every cited
 * profile becomes a `p` tag (so they are notified), every cited event a NIP-18 `q`
 * tag, every `#hashtag` a `t` tag and every web link an `r` tag. The composer
 * resolves mentions through `LocalCache` (`NewMessageTagger`); this variant needs
 * nothing but the text, for callers with no cache (amy, the reply/quote builders here).
 *
 * Only standalone references count ([findNostrUris]): an `npub1…` glued inside a
 * URL is not a citation and must not notify whoever owns that key.
 */
object ContentTags {
    fun pubKeys(entities: List<Entity>): List<HexKey> =
        entities
            .mapNotNull {
                when (it) {
                    is NPub -> it.hex
                    is NProfile -> it.hex
                    else -> null
                }
            }.distinct()

    fun events(entities: List<Entity>): List<Entity> = entities.filter { it is NNote || it is NEvent || it is NAddress }
}

/** Adds the `p`, `q`, `t` and `r` tags [content] calls for; never duplicates an existing `p`/`q` value. */
fun <T : Event> TagArrayBuilder<T>.contentTags(content: String) {
    val entities = findNostrUris(content)
    ContentTags.pubKeys(entities).forEach { addUniqueValueIfNew(PTag(it).toTagArray()) }
    ContentTags.events(entities).forEach { quote(it) }
    hashtags(findHashtags(content))
    references(findURLs(content))
}
