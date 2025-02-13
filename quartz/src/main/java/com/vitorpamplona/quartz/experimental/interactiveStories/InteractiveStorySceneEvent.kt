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
package com.vitorpamplona.quartz.experimental.interactiveStories

import com.vitorpamplona.quartz.experimental.interactiveStories.tags.StoryOptionTag
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.tags.dTags.dTag
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.utils.TimeUtils

class InteractiveStorySceneEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : InteractiveStoryBaseEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    RootScope {
    companion object {
        const val KIND = 30297
        const val ALT = "A scene of an interative story called "

        fun createAddressATag(
            pubKey: HexKey,
            dtag: String,
        ): ATag = ATag(KIND, pubKey, dtag, null)

        fun createAddressTag(
            pubKey: HexKey,
            dtag: String,
        ): String = ATag.assembleATagId(KIND, pubKey, dtag)

        fun build(
            baseId: String,
            title: String,
            content: String,
            options: List<StoryOptionTag>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<InteractiveStorySceneEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, content, createdAt) {
            dTag(baseId)
            title(title)
            options(options)
            initializer()
        }
    }
}
