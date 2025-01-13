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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.addressables.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.firstTag
import com.vitorpamplona.quartz.nip01Core.core.firstTagValue
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip19Bech32Entities.parse
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.removeTrailingNullsAndEmptyOthers

@Immutable
class InteractiveStoryReadingStateEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun title() = tags.firstTagValue("title")

    fun summary() = tags.firstTagValue("summary")

    fun image() = tags.firstTagValue("image")

    fun status() = tags.firstTagValue("status")

    fun root() = tags.firstTag("A")?.let { ATag.parse(it[1], it.getOrNull(2)) }

    fun currentScene() = tags.firstTag("a")?.let { ATag.parse(it[1], it.getOrNull(2)) }

    companion object {
        const val KIND = 30298
        const val ALT1 = "Interactive Story Reading state"
        const val ALT2 = "The reading state of "

        fun createAddressATag(
            pubKey: HexKey,
            dtag: String,
        ): ATag = ATag(KIND, pubKey, dtag, null)

        fun createAddressTag(
            pubKey: HexKey,
            dtag: String,
        ): String = ATag.assembleATag(KIND, pubKey, dtag)

        fun update(
            base: InteractiveStoryReadingStateEvent,
            currentScene: InteractiveStoryBaseEvent,
            currentSceneRelay: String?,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (InteractiveStoryReadingStateEvent) -> Unit,
        ) {
            val rootTag = base.dTag()
            val sceneTag = currentScene.addressTag()

            val status =
                if (rootTag == sceneTag) {
                    "new"
                } else if (currentScene.options().isEmpty()) {
                    "done"
                } else {
                    "reading"
                }

            val tags =
                base.tags.filter { it[0] != "a" && it[0] != "status" } +
                    listOf(
                        removeTrailingNullsAndEmptyOthers("a", sceneTag, currentSceneRelay),
                        arrayOf("status", status),
                    )

            signer.sign(createdAt, KIND, tags.toTypedArray(), "", onReady)
        }

        fun create(
            root: InteractiveStoryBaseEvent,
            rootRelay: String?,
            currentScene: InteractiveStoryBaseEvent,
            currentSceneRelay: String?,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (InteractiveStoryReadingStateEvent) -> Unit,
        ) {
            val rootTag = root.addressTag()
            val sceneTag = currentScene.addressTag()
            val status =
                if (rootTag == sceneTag) {
                    "new"
                } else if (currentScene.options().isEmpty()) {
                    "done"
                } else {
                    "reading"
                }

            val tags =
                listOfNotNull(
                    arrayOf("d", rootTag),
                    arrayOf("alt", root.title()?.let { ALT2 + it } ?: ALT1),
                    root.title()?.let { arrayOf("title", it) },
                    root.summary()?.let { arrayOf("summary", it) },
                    root.image()?.let { arrayOf("image", it) },
                    removeTrailingNullsAndEmptyOthers("A", rootTag, rootRelay),
                    removeTrailingNullsAndEmptyOthers("a", sceneTag, currentSceneRelay),
                    arrayOf("status", status),
                ).toTypedArray()

            signer.sign(createdAt, KIND, tags, "", onReady)
        }
    }

    enum class ReadingStatus {
        NEW,
        READING,
        DONE,
    }
}
