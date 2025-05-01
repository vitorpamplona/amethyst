/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.nip84Highlights

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.addressables.firstTaggedATag
import com.vitorpamplona.quartz.nip01Core.tags.addressables.firstTaggedAddress
import com.vitorpamplona.quartz.nip01Core.tags.events.firstTaggedEvent
import com.vitorpamplona.quartz.nip01Core.tags.people.firstTaggedUser
import com.vitorpamplona.quartz.nip01Core.tags.references.ReferenceTag
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip84Highlights.tags.CommentTag
import com.vitorpamplona.quartz.nip84Highlights.tags.ContextTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class HighlightEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseThreadedEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    RootScope {
    fun inUrl() = tags.firstNotNullOfOrNull(ReferenceTag::parse)

    fun author() = firstTaggedUser()

    fun quote() = content

    fun comment() = tags.firstNotNullOfOrNull(CommentTag::parse)

    fun context() = tags.firstNotNullOfOrNull(ContextTag::parse)

    fun inPost() = firstTaggedATag()

    fun inPostAddress() = firstTaggedAddress()

    fun inPostVersion() = firstTaggedEvent()

    companion object {
        const val KIND = 9802
        const val ALT = "Highlight/quote event"

        fun create(
            msg: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (HighlightEvent) -> Unit,
        ) {
            signer.sign(createdAt, KIND, arrayOf(AltTag.assemble(ALT)), msg, onReady)
        }
    }
}
