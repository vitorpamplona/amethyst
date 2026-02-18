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
package com.vitorpamplona.quartz.experimental.interactiveStories

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.interactiveStories.tags.ReadStatusTag
import com.vitorpamplona.quartz.experimental.interactiveStories.tags.RootSceneTag
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.builder
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip94FileMetadata.tags.SummaryTag
import com.vitorpamplona.quartz.nip99Classifieds.tags.StatusTag
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class InteractiveStoryReadingStateEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun summary() = tags.firstNotNullOfOrNull(SummaryTag::parse)

    fun image() = tags.firstNotNullOfOrNull(ImageTag::parse)

    fun status() = tags.firstNotNullOfOrNull(StatusTag::parse)

    fun root() = tags.firstNotNullOfOrNull(RootSceneTag::parse)

    fun currentScene() = tags.firstNotNullOfOrNull(ATag::parseAddress)

    companion object {
        const val KIND = 30298
        const val ALT1 = "Interactive Story Reading state"
        const val ALT2 = "The reading state of "

        fun createAddress(
            pubKey: HexKey,
            dtag: String,
        ): Address = Address(KIND, pubKey, dtag)

        fun createAddressATag(
            pubKey: HexKey,
            dtag: String,
        ): ATag = ATag(KIND, pubKey, dtag, null)

        fun createAddressTag(
            pubKey: HexKey,
            dtag: String,
        ): String = Address.assemble(KIND, pubKey, dtag)

        fun update(
            base: InteractiveStoryReadingStateEvent,
            currentScene: EventHintBundle<InteractiveStoryBaseEvent>,
            createdAt: Long = TimeUtils.now(),
        ): EventTemplate<InteractiveStoryReadingStateEvent> {
            val rootTag = base.dTag()
            val sceneTag = ATag(currentScene.event.address(), currentScene.relay)

            val status =
                if (rootTag == sceneTag.toTag()) {
                    ReadStatusTag.STATUS.NEW
                } else if (currentScene.event.options().isEmpty()) {
                    ReadStatusTag.STATUS.DONE
                } else {
                    ReadStatusTag.STATUS.READING
                }

            val updatedTags =
                base.tags.builder {
                    currentScene(sceneTag)
                    status(status)
                }

            return EventTemplate(createdAt, KIND, updatedTags, "")
        }

        fun build(
            root: EventHintBundle<InteractiveStoryBaseEvent>,
            currentScene: EventHintBundle<InteractiveStoryBaseEvent>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<InteractiveStoryReadingStateEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            val rootTag = ATag(root.event.address(), root.relay)
            val sceneTag = ATag(currentScene.event.address(), currentScene.relay)
            val status =
                if (rootTag == sceneTag) {
                    ReadStatusTag.STATUS.NEW
                } else if (currentScene.event.options().isEmpty()) {
                    ReadStatusTag.STATUS.DONE
                } else {
                    ReadStatusTag.STATUS.READING
                }

            dTag(rootTag.toTag())
            alt(root.event.title()?.let { ALT2 + it } ?: ALT1)

            rootScene(rootTag)
            currentScene(sceneTag)
            status(status)

            root.event.title()?.let { storyTitle(it) }
            root.event.summary()?.let { storyImage(it) }
            root.event.image()?.let { storySummary(it) }

            initializer()
        }
    }
}
