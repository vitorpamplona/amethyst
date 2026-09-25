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
package com.vitorpamplona.quartz.experimental.decentralizedLists.assistant

import com.vitorpamplona.quartz.experimental.decentralizedLists.item.AddressableListItemEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.CommentsTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.NameTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.ParentListTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.SlugTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.tags.TitleTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.tags.DescriptionTag
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip01Core.tags.kinds.KindTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * Tapestry Assistant Designation, *curation copies*: how an empowered assistant fills the list
 * it curates. It files its own kind 39999 copy of each item it accepts under the curated
 * header, and deletes the copies it stops accepting.
 *
 * A copy's tags are exactly its `d`, one `z` (the curated header), its `q` pointers back to the
 * original, and the original's `name`/`title`/`slug`/`description`/`comments` and item tags
 * (`p`/`e`/`t`/`a`) verbatim, plus the original's `content`. Nothing else is carried — not
 * `json`, `n` or `s`, which describe the original's place in its author's own graph — and the
 * copy is never stamped with the original list's `z`s.
 */
object CurationCopy {
    const val Q_TAG = "q"

    /**
     * `copy-` + hex SHA-256 of `<header address>\n<original ref>`, where the ref is the
     * original's `39999:<author>:<d>` address for a 39999, else its event id. Copying the same
     * original into the same list again therefore replaces the copy.
     */
    fun dTag(
        curatedHeader: Address,
        original: Event,
    ) = "copy-" + sha256((curatedHeader.toValue() + "\n" + originalRef(original)).encodeToByteArray()).toHexKey()

    fun originalRef(original: Event) = if (original is AddressableListItemEvent) original.addressTag() else original.id

    private val CARRIED =
        setOf(
            NameTag.TAG_NAME,
            TitleTag.TAG_NAME,
            SlugTag.TAG_NAME,
            DescriptionTag.TAG_NAME,
            CommentsTag.TAG_NAME,
            PTag.TAG_NAME,
            ETag.TAG_NAME,
            HashtagTag.TAG_NAME,
            ATag.TAG_NAME,
        )

    /**
     * @param curatedHeader the assistant's curated header, `<kind>:<assistant>:<d>`.
     * @param relay where the original can be fetched; written into the `q` tags.
     */
    fun build(
        curatedHeader: Address,
        original: Event,
        relay: NormalizedRelayUrl?,
        createdAt: Long = TimeUtils.now(),
    ) = AddressableListItemEvent.build(
        parent = ParentListTag.classify(curatedHeader.toValue()),
        dTag = dTag(curatedHeader, original),
        createdAt = createdAt,
        content = original.content,
    ) {
        val relayUrl = relay?.url ?: ""
        // the stable address, surviving the author's edits
        if (original is AddressableListItemEvent) add(arrayOf(Q_TAG, original.addressTag(), relayUrl))
        // the exact version copied, NIP-18 style, naming the author here rather than in a `p`
        add(arrayOf(Q_TAG, original.id, relayUrl, original.pubKey))
        original.tags.forEach { tag ->
            if (tag.isNotEmpty() && tag[0] in CARRIED) add(tag)
        }
    }

    /** The NIP-09 removal of a copy: by address, by event id and by kind. */
    fun buildRemoval(
        copy: AddressableListItemEvent,
        createdAt: Long = TimeUtils.now(),
    ) = eventTemplate<DeletionEvent>(DeletionEvent.KIND, "", createdAt) {
        add(ATag.assemble(copy.address(), null))
        add(ETag.assemble(copy.id, null, null))
        add(KindTag.assemble(AddressableListItemEvent.KIND))
    }
}
