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
package com.vitorpamplona.quartz.events

import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.IMetaTag
import com.vitorpamplona.quartz.signers.NostrSigner
import com.vitorpamplona.quartz.utils.TimeUtils

class InteractiveStoryPrologueEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : InteractiveStoryBaseEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    RootScope {
    companion object {
        const val KIND = 30296
        const val ALT = "The prologue of an interative story called "

        fun createAddressATag(
            pubKey: HexKey,
            dtag: String,
        ): ATag = ATag(KIND, pubKey, dtag, null)

        fun createAddressTag(
            pubKey: HexKey,
            dtag: String,
        ): String = ATag.assembleATag(KIND, pubKey, dtag)

        fun create(
            baseId: String,
            title: String,
            content: String,
            options: List<StoryOption>,
            summary: String? = null,
            image: String? = null,
            zapReceiver: List<ZapSplitSetup>? = null,
            markAsSensitive: Boolean = false,
            zapRaiserAmount: Long? = null,
            geohash: String? = null,
            imetas: List<IMetaTag>? = null,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            isDraft: Boolean,
            onReady: (InteractiveStoryPrologueEvent) -> Unit,
        ) {
            val tags =
                makeTags(baseId, ALT + title, title, summary, image, options) +
                    generalTags(content, zapReceiver, markAsSensitive, zapRaiserAmount, geohash, imetas)

            if (isDraft) {
                signer.assembleRumor(createdAt, KIND, tags, content, onReady)
            } else {
                signer.sign(createdAt, KIND, tags, content, onReady)
            }
        }
    }
}
