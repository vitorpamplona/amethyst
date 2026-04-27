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
package com.vitorpamplona.amethyst.commons.viewmodels

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Decoded shape of a moq-lite `catalog.json` track for a single
 * audio-room speaker. Each broadcast advertises one or more tracks
 * (one per codec / quality tier) — for nests audio rooms there's
 * exactly one Opus track today, but the model carries a list for
 * forward compatibility.
 *
 * Best-effort schema: nostrnests' upstream moq-lite catalog spec
 * (kixelated/moq) has evolved across revisions, and not every
 * publisher fills in every field. The parser tolerates missing
 * keys via `ignoreUnknownKeys = true` on [JsonMapper] and the
 * `?`-marked properties.
 */
@Immutable
@Serializable
data class RoomSpeakerCatalog(
    val version: Int? = null,
    val audio: List<AudioTrack> = emptyList(),
) {
    @Immutable
    @Serializable
    data class AudioTrack(
        val track: String? = null,
        val codec: String? = null,
        @SerialName("sample_rate") val sampleRate: Int? = null,
        @SerialName("channel_count") val channelCount: Int? = null,
        val bitrate: Int? = null,
    ) {
        /**
         * Short single-line summary suitable for a UI tooltip /
         * subtitle. Returns null when there's nothing useful to
         * show — the caller can omit the line entirely rather than
         * render "unknown · unknown".
         */
        fun describe(): String? {
            val parts =
                buildList {
                    codec?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
                    sampleRate?.let { add("${it / 1000}kHz") }
                    channelCount?.let { add(if (it == 1) "mono" else "${it}ch") }
                }
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        }
    }

    /** First audio track, if any. The current single-Opus reality. */
    fun primaryAudio(): AudioTrack? = audio.firstOrNull()

    companion object {
        /**
         * Parse a UTF-8 JSON byte array delivered on the
         * `catalog.json` track. Returns null when the bytes don't
         * decode as JSON or don't match the expected shape — the
         * caller falls back to "no catalog" rather than blowing up
         * the UI.
         */
        fun parseOrNull(bytes: ByteArray): RoomSpeakerCatalog? {
            val text =
                runCatching { bytes.decodeToString() }.getOrNull()
                    ?: return null
            return runCatching { JsonMapper.fromJson<RoomSpeakerCatalog>(text) }.getOrNull()
        }
    }
}
