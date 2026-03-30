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
package com.vitorpamplona.quartz.nip90Dvms.textGeneration

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip90Dvms.tags.InputTag
import com.vitorpamplona.quartz.nip90Dvms.tags.dvmParam
import com.vitorpamplona.quartz.nip90Dvms.tags.inputPrompt
import com.vitorpamplona.quartz.nip90Dvms.tags.inputs
import com.vitorpamplona.quartz.nip90Dvms.tags.output
import com.vitorpamplona.quartz.nip90Dvms.tags.outputMimeType
import com.vitorpamplona.quartz.nip90Dvms.tags.param
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class NIP90TextGenerationRequestEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun inputs(): List<InputTag> = tags.inputs()

    fun outputMimeType(): String? = tags.outputMimeType()

    fun model(): String? = tags.dvmParam("model")

    fun maxTokens(): Int? = tags.dvmParam("max_tokens")?.toIntOrNull()

    fun temperature(): Double? = tags.dvmParam("temperature")?.toDoubleOrNull()

    fun topK(): Int? = tags.dvmParam("top_k")?.toIntOrNull()

    fun topP(): Double? = tags.dvmParam("top_p")?.toDoubleOrNull()

    fun frequencyPenalty(): Double? = tags.dvmParam("frequency_penalty")?.toDoubleOrNull()

    companion object {
        const val KIND = 5050
        const val ALT = "NIP90 Text Generation request"

        fun build(
            prompt: String,
            model: String? = null,
            maxTokens: Int? = null,
            temperature: Double? = null,
            topK: Int? = null,
            topP: Double? = null,
            frequencyPenalty: Double? = null,
            outputMimeType: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<NIP90TextGenerationRequestEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            alt(ALT)
            inputPrompt(prompt)
            model?.let { param("model", it) }
            maxTokens?.let { param("max_tokens", it.toString()) }
            temperature?.let { param("temperature", it.toString()) }
            topK?.let { param("top_k", it.toString()) }
            topP?.let { param("top_p", it.toString()) }
            frequencyPenalty?.let { param("frequency_penalty", it.toString()) }
            outputMimeType?.let { output(it) }
            initializer()
        }
    }
}
