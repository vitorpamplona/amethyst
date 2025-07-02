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
package com.vitorpamplona.quartz.nip28PublicChat.base

import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.nip01Core.jackson.EventMapper
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

@Immutable
data class ChannelData(
    val name: String? = null,
    val about: String? = null,
    val picture: String? = null,
    val relays: List<String>? = null,
) {
    fun toContent() = assemble(this)

    fun normalize() = ChannelDataNorm(name, about, picture, relays?.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) })

    companion object {
        fun parse(content: String): ChannelData? = EventMapper.mapper.readValue(content)

        fun assemble(data: ChannelData) = EventMapper.mapper.writeValueAsString(data)
    }
}

data class ChannelDataNorm(
    val name: String? = null,
    val about: String? = null,
    val picture: String? = null,
    val relays: List<NormalizedRelayUrl>? = null,
) {
    fun denormalize() = ChannelData(name, about, picture, relays?.mapNotNull { it.url })

    fun toContent() = denormalize().toContent()
}
