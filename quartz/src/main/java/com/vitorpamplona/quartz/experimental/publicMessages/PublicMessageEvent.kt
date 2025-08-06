/**
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
package com.vitorpamplona.quartz.experimental.publicMessages

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.publicMessages.tags.ReceiverTag
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.any
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip10Notes.BaseNoteEvent
import com.vitorpamplona.quartz.nip19Bech32.pubKeyHints
import com.vitorpamplona.quartz.nip19Bech32.pubKeys
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class PublicMessageEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseNoteEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    PubKeyHintProvider,
    SearchableEvent {
    override fun indexableContent() = content

    override fun pubKeyHints() = tags.mapNotNull(ReceiverTag::parseAsHint) + citedNIP19().pubKeyHints()

    override fun linkedPubKeys() = tags.mapNotNull(ReceiverTag::parseKey) + citedNIP19().pubKeys()

    fun isIncluded(pubKey: HexKey) = tags.any(ReceiverTag::match, pubKey) || this.pubKey == pubKey

    fun group() = tags.mapNotNullTo(mutableListOf(ReceiverTag(this.pubKey, null)), ReceiverTag::parse)

    fun groupKeys() = tags.mapNotNullTo(mutableListOf(this.pubKey), ReceiverTag::parseKey)

    fun groupKeySet() = tags.mapNotNullTo(mutableSetOf(this.pubKey), ReceiverTag::parseKey)

    companion object {
        const val KIND = 24
        const val ALT_DESCRIPTION = "Public Message"

        fun build(
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PublicMessageEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            alt(ALT_DESCRIPTION)
            initializer()
        }

        fun build(
            to: ReceiverTag,
            msg: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PublicMessageEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, msg, createdAt) {
            alt(ALT_DESCRIPTION)
            toUser(to)
            initializer()
        }

        fun build(
            to: List<ReceiverTag>,
            msg: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PublicMessageEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, msg, createdAt) {
            alt(ALT_DESCRIPTION)
            toGroup(to)
            initializer()
        }
    }
}
