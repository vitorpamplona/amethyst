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
package com.vitorpamplona.quartz.buzz.dvDmVisibility

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz per-viewer DM Visibility snapshot (NIP-DV, `kind:30622`): a relay-signed,
 * addressable event that projects one viewer's hidden-DM set. It is keyed by the viewer's
 * pubkey (the `d` tag), carries a matching `p` tag so the relay's `#p`-gated read path
 * scopes it to its owner, and lists one `h` tag per hidden DM channel (zero or more). The
 * content is empty; the relay re-publishes the whole snapshot on every hide/unhide.
 *
 * The event is signed by the relay keypair, not the viewer. Ground truth:
 * `buzz-core/src/kind.rs` (`KIND_DM_VISIBILITY`) and
 * `buzz-relay/src/handlers/side_effects.rs` (`publish_dm_visibility_snapshot`).
 */
@Immutable
class DmVisibilityEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The viewer this snapshot belongs to — the `d` tag (equal to the `p` tag). */
    fun viewer() = dTag()

    /** The viewer pubkey read from the `p` tag. */
    fun viewerFromPTag() = tags.dmVisibilityViewer()

    /** The channel ids the viewer has hidden — the `h` tags (possibly empty). */
    fun hiddenChannels() = tags.dmVisibilityHiddenChannels()

    companion object {
        const val KIND = 30622

        /**
         * Builds a DM-visibility snapshot template for [viewerPubKey] listing
         * [hiddenChannels]. The content is empty. Sign it with the relay keypair.
         */
        fun build(
            viewerPubKey: HexKey,
            hiddenChannels: List<String>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<DmVisibilityEvent>.() -> Unit = {},
        ) = eventTemplate<DmVisibilityEvent>(KIND, "", createdAt) {
            dTag(viewerPubKey)
            viewer(viewerPubKey)
            hiddenChannels(hiddenChannels)
            initializer()
        }
    }
}
