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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.video.datasource

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.service.relayClient.composeSubscriptionManagers.ComposeSubscriptionManager
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.datasource.subassemblies.VideoMixGeohashHashtagsFilterSubAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.datasource.subassemblies.VideoOutboxEventsFilterSubAssembler
import com.vitorpamplona.ammolite.relays.NostrClient
import kotlinx.coroutines.CoroutineScope

val SUPPORTED_VIDEO_FEED_MIME_TYPES = listOf("image/jpeg", "image/gif", "image/png", "image/webp", "video/mp4", "video/mpeg", "video/webm", "audio/aac", "audio/mpeg", "audio/webm", "audio/wav", "image/avif")
val SUPPORTED_VIDEO_FEED_MIME_TYPES_SET = SUPPORTED_VIDEO_FEED_MIME_TYPES.toSet()

// This allows multiple screen to be listening to tags, even the same tag
class VideoQueryState(
    val account: Account,
    val scope: CoroutineScope,
)

class VideoFilterAssembler(
    client: NostrClient,
) : ComposeSubscriptionManager<VideoQueryState>() {
    val group =
        listOf(
            VideoOutboxEventsFilterSubAssembler(client, ::allKeys),
            VideoMixGeohashHashtagsFilterSubAssembler(client, ::allKeys),
        )

    override fun start() = group.forEach { it.start() }

    override fun stop() = group.forEach { it.stop() }

    override fun invalidateKeys() = invalidateFilters()

    override fun invalidateFilters() = group.forEach { it.invalidateFilters() }

    override fun destroy() = group.forEach { it.start() }

    override fun printStats() = group.forEach { it.printStats() }
}
