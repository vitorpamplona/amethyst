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
package com.vitorpamplona.quartz.nipA0VoiceMessages

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip94FileMetadata.tags.HashSha256Tag
import com.vitorpamplona.quartz.nipA0VoiceMessages.tags.ReplyAuthorTag
import com.vitorpamplona.quartz.nipA0VoiceMessages.tags.ReplyEventTag
import com.vitorpamplona.quartz.nipA0VoiceMessages.tags.ReplyKindTag

fun <T : BaseVoiceEvent> TagArrayBuilder<T>.audioIMeta(
    url: String,
    mimeType: String? = null,
    hash: String? = null,
    duration: Int? = null,
    waveform: List<Float>? = null,
) = audioIMeta(AudioMeta(url, mimeType, hash, duration, waveform))

fun <T : BaseVoiceEvent> TagArrayBuilder<T>.audioIMeta(imeta: AudioMeta): TagArrayBuilder<T> {
    add(imeta.toIMetaArray())
    imeta.hash?.let { add(HashSha256Tag.assemble(it)) }
    return this
}

fun <T : BaseVoiceEvent> TagArrayBuilder<T>.audioIMeta(audioUrls: List<AudioMeta>): TagArrayBuilder<T> {
    audioUrls.forEach { audioIMeta(it) }
    return this
}

fun TagArrayBuilder<VoiceReplyEvent>.replyEvent(
    eventId: String,
    relayHint: NormalizedRelayUrl?,
    pubkey: String?,
) = addUnique(ReplyEventTag.assemble(eventId, relayHint, pubkey))

fun TagArrayBuilder<VoiceReplyEvent>.replyKind(kind: String) = addUnique(ReplyKindTag.assemble(kind))

fun TagArrayBuilder<VoiceReplyEvent>.replyKind(kind: Int) = addUnique(ReplyKindTag.assemble(kind))

fun TagArrayBuilder<VoiceReplyEvent>.replyAuthor(
    pubKey: HexKey,
    relay: NormalizedRelayUrl?,
) = add(ReplyAuthorTag.assemble(pubKey, relay))
