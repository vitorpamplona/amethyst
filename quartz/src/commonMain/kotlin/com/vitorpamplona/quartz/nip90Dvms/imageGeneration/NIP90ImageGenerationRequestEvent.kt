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
package com.vitorpamplona.quartz.nip90Dvms.imageGeneration

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip90Dvms.tags.InputTag
import com.vitorpamplona.quartz.nip90Dvms.tags.dvmParam
import com.vitorpamplona.quartz.nip90Dvms.tags.firstInputByType
import com.vitorpamplona.quartz.nip90Dvms.tags.inputText
import com.vitorpamplona.quartz.nip90Dvms.tags.inputUrl
import com.vitorpamplona.quartz.nip90Dvms.tags.inputs
import com.vitorpamplona.quartz.nip90Dvms.tags.param
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class NIP90ImageGenerationRequestEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun inputs(): List<InputTag> = tags.inputs()

    fun prompt(): String? = tags.firstInputByType("text")?.value

    fun sourceImageUrl(): String? = tags.firstInputByType("url")?.value

    fun model(): String? = tags.dvmParam("model")

    fun lora(): String? = tags.dvmParam("lora")

    fun ratio(): String? = tags.dvmParam("ratio")

    fun size(): String? = tags.dvmParam("size")

    fun negativePrompt(): String? = tags.dvmParam("negative_prompt")

    companion object {
        const val KIND = 5100
        const val ALT = "NIP90 Image Generation request"

        fun build(
            prompt: String,
            sourceImageUrl: String? = null,
            model: String? = null,
            lora: String? = null,
            ratio: String? = null,
            size: String? = null,
            negativePrompt: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<NIP90ImageGenerationRequestEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            alt(ALT)
            sourceImageUrl?.let { inputUrl(it) }
            inputText(prompt)
            model?.let { param("model", it) }
            lora?.let { param("lora", it) }
            size?.let { param("size", it) }
            ratio?.let { param("ratio", it) }
            negativePrompt?.let { param("negative_prompt", it) }
            initializer()
        }
    }
}
