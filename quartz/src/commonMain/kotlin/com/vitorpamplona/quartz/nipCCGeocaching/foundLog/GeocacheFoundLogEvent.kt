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
package com.vitorpamplona.quartz.nipCCGeocaching.foundLog

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.tags.GeocacheTag
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.nipCCGeocaching.verification.GeocacheVerificationEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A found log (kind 7516): a claim that the author found a geocache.
 *
 * `content` is the log message. The `a` tag names the cache. A log may carry a kind 7517
 * verification event inline, which is what turns "I say I found it" into "I can prove I was
 * there" — but only after [com.vitorpamplona.quartz.nipCCGeocaching.verification
 * .GeocacheVerificationValidator] has checked it against the listing. [isVerified] on its own
 * means nothing more than "a verification event was attached".
 *
 * Only *found* logs are this kind. Did-not-find, notes and maintenance reports are NIP-22
 * comments rooted on the listing — see [com.vitorpamplona.quartz.nipCCGeocaching.comment
 * .GeocacheLogComment].
 */
@Immutable
class GeocacheFoundLogEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressHintProvider,
    SearchableEvent {
    override fun indexableContent() = content

    override fun addressHints() = tags.mapNotNull(GeocacheTag::parseAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(GeocacheTag::parseAddressId)

    fun geocache() = tags.geocache()

    fun geocacheId() = tags.geocacheId()

    fun images() = tags.logImages()

    fun embeddedVerification() = tags.embeddedVerification()

    /** Whether a verification event is attached at all. Says nothing about whether it is valid. */
    fun isVerified() = tags.embeddedVerification() != null

    companion object {
        const val KIND = 7516

        fun build(
            message: String,
            cache: EventHintBundle<GeocacheListingEvent>,
            verification: GeocacheVerificationEvent? = null,
            images: List<String>? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GeocacheFoundLogEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, message, createdAt) {
            geocache(cache)
            verification?.let { embeddedVerification(it) }
            images?.let { logImages(it) }
            initializer()
        }

        fun build(
            message: String,
            cache: Address,
            relayHint: NormalizedRelayUrl? = null,
            verification: GeocacheVerificationEvent? = null,
            images: List<String>? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GeocacheFoundLogEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, message, createdAt) {
            geocache(cache, relayHint)
            verification?.let { embeddedVerification(it) }
            images?.let { logImages(it) }
            initializer()
        }
    }
}
