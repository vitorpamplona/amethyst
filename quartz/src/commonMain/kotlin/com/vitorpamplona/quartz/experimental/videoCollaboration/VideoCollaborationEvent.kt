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
package com.vitorpamplona.quartz.experimental.videoCollaboration

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.videoCollaboration.tags.RoleTag
import com.vitorpamplona.quartz.experimental.videoCollaboration.tags.StatusTag
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A collaborator's answer to being credited on someone else's NIP-71 video.
 *
 * The invite is not an event of its own: the video's author adds the collaborator as a `p` tag on
 * the video itself, and the collaborator answers with one of these, `a`-tagging the video. Only
 * the collaborator can sign it, which is the point — it is what turns an unsolicited tag into a
 * credit the tagged person actually stands behind.
 *
 * Two shapes exist in the wild and both are accepted here, because the `a` tag is the only field
 * either guarantees:
 *  - divine-web writes a random `d` with the coordinate in `a`;
 *  - divine-mobile writes the coordinate itself as `d` (one response per video, replaceable) and
 *    adds `p`, `role` and `status`.
 *
 * A response with no `status` is an acceptance: divine-web only publishes the event when the
 * collaborator approves, so the event's existence *is* the answer. [isAccepted] reads it that way.
 */
@Immutable
class VideoCollaborationEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressHintProvider,
    PubKeyHintProvider {
    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(ATag::parseAddressId)

    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    /** The video being collaborated on. */
    fun video() = tags.firstNotNullOfOrNull(ATag::parseAddress)

    /** The video's author, when the response names them. */
    fun videoAuthor() = tags.firstNotNullOfOrNull(PTag::parseKey)

    /** Free text, as the video's author typed it: "Collaborator", "Director", … */
    fun role() = tags.firstNotNullOfOrNull(RoleTag::parse)

    fun status() = tags.firstNotNullOfOrNull(StatusTag::parse)

    /**
     * True unless the collaborator explicitly said no. An absent `status` means yes — see the
     * class docs: one of the two publishers only emits the event on approval.
     */
    fun isAccepted() = status()?.let { it == StatusTag.ACCEPTED } ?: true

    companion object {
        const val KIND = 34238

        fun build(
            video: ATag,
            videoAuthor: HexKey? = null,
            role: String? = null,
            status: String = StatusTag.ACCEPTED,
            // One response per video: keying on the coordinate makes a later answer replace the
            // earlier one instead of leaving both on relays for readers to date-sort.
            dTag: String = video.toTag(),
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<VideoCollaborationEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            dTag(dTag)
            add(video.toATagArray())
            videoAuthor?.let { add(PTag.assemble(it, null)) }
            role?.let { add(RoleTag.assemble(it)) }
            add(StatusTag.assemble(status))
            initializer()
        }
    }
}
