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
package com.vitorpamplona.quartz.experimental.agora

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.agora.tags.DeadlineTag
import com.vitorpamplona.quartz.experimental.agora.tags.GoalAmountTag
import com.vitorpamplona.quartz.experimental.agora.tags.WalletTag
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.tags.BannerTag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.publishedAt.PublishedAtProvider
import com.vitorpamplona.quartz.nip23LongContent.tags.ImageTag
import com.vitorpamplona.quartz.nip23LongContent.tags.PublishedAtTag
import com.vitorpamplona.quartz.nip23LongContent.tags.TitleTag

/**
 * Agora fundraiser / crowdfunding campaign (kind 33863).
 *
 * An app-specific addressable kind used by the Agora client (built on the Ditto
 * stack). It is **not** defined by any NIP; the schema below is derived from
 * events seen in the wild. It reuses standard NIP tags wherever possible
 * (`title`, `banner`, `imeta`, `t` hashtags, `published_at`) plus a few
 * fundraiser-specific tags:
 *
 * - `goal`     — fundraising target, integer sats.
 * - `deadline` — unix-seconds deadline to reach the goal.
 * - `w`        — one or more on-chain donation addresses (Bitcoin / silent
 *                payment). Display/copy only; Amethyst does not send on-chain.
 *
 * Amethyst renders progress from zaps to this event (it cannot observe on-chain
 * donations), so the progress bar reflects Lightning support, not the full
 * amount raised.
 */
@Immutable
class FundraiserEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    PublishedAtProvider {
    fun title() = tags.firstNotNullOfOrNull(TitleTag::parse)

    fun banner() = tags.firstNotNullOfOrNull(BannerTag::parse)

    fun image() = tags.firstNotNullOfOrNull(ImageTag::parse)

    /** Best image to show for the campaign: explicit banner, else an `image` tag. */
    fun coverImage() = banner() ?: image()

    /** Fundraising target in sats, from the `goal` tag. */
    fun goal() = tags.firstNotNullOfOrNull(GoalAmountTag::parse)

    /** Deadline (unix seconds) from the `deadline` tag. */
    fun deadline() = tags.firstNotNullOfOrNull(DeadlineTag::parse)

    /** On-chain donation addresses from the `w` tags (may be empty). */
    fun wallets() = tags.mapNotNull(WalletTag::parse)

    fun topics() = tags.hashtags()

    override fun publishedAt(): Long? {
        val publishedAt = tags.firstNotNullOfOrNull(PublishedAtTag::parse) ?: return null

        // ignore timestamps in the future
        return if (publishedAt <= createdAt) publishedAt else null
    }

    companion object {
        const val KIND = 33863
        const val ALT_DESCRIPTION = "Fundraiser campaign"
    }
}
