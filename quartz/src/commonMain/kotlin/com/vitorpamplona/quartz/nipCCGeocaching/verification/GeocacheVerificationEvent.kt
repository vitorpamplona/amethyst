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
package com.vitorpamplona.quartz.nipCCGeocaching.verification

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A geocache verification event (kind 7517): cryptographic proof that someone stood at a cache.
 *
 * **Signed by the cache's verification key, not by the finder and not by the cache owner.** The
 * private half lives at the cache itself, normally behind a QR code, so producing one of these
 * requires having physically been there. A finder signs it with an ephemeral signer built from
 * the scanned key, embeds the result in their found log, and throws the key away — it is not an
 * account key and must never reach a keystore, an account, or a log line.
 *
 * Read [GeocacheVerificationValidator] before trusting one: a verification event on its own
 * proves only that *somebody* was at the cache. It becomes evidence about a particular finder
 * only once the signer, the finder and the cache address have all been checked against the
 * listing and the log.
 */
@Immutable
class GeocacheVerificationEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun finderCache() = tags.finderCache()

    /** The pubkey this verification was issued to. */
    fun finder() = tags.finder()

    /** The cache it was issued at. */
    fun cache() = tags.verifiedCache()

    /** Whether `content` matches the static format NIP-CC mandates for [finder]. */
    fun hasExpectedContent() = finder()?.let { content == contentFor(it) } ?: false

    companion object {
        const val KIND = 7517

        /** NIP-CC fixes the content exactly: `"Geocache verification for <finder-npub>"`. */
        fun contentFor(finderPubKey: HexKey) = "Geocache verification for ${NPub.create(finderPubKey)}"

        fun build(
            finderPubKey: HexKey,
            cache: Address,
            relayHint: NormalizedRelayUrl? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<GeocacheVerificationEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, contentFor(finderPubKey), createdAt) {
            finderCache(finderPubKey, cache, relayHint)
            initializer()
        }
    }
}
