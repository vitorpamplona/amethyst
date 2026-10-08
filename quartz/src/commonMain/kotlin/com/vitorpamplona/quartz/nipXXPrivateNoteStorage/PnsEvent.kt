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
package com.vitorpamplona.quartz.nipXXPrivateNoteStorage

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip59Giftwrap.HasInnerEvent
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.Rumor
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.utils.EventFactory
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.concurrent.Volatile

/**
 * NIP-PNS Private Note Storage (kind 1080, regular; draft nostr-protocol/nips#1893): a note the
 * user stores for themselves, NIP-44 v2 encrypted under a key only their device key derives, and
 * signed by a keypair derived from that same key (see [PnsKeys]). Relays see an opaque blob from
 * a pubkey that is unlinkable to the user.
 *
 * The inner note is any nostr event: either a **rumor** (unsigned; its author MUST be the device
 * key's pubkey — the spec's recommended form, since a rumor can't be accidentally rebroadcast) or
 * a **signed** event by anyone. Unlike NIP-59 there is no seal: only the device key can produce a
 * valid PNS payload, so a decrypted rumor is authenticated by the envelope itself.
 *
 * Read with [decryptThrowing] / [decryptOrNull], which also record the inner id in
 * [innerEventId] the way [com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent] does.
 *
 * **Not searchable**: the content is ciphertext and the event carries no tags, so there is nothing
 * to index (the decrypted inner event is indexed on its own, if its kind is searchable).
 *
 * **No hint providers / graph edges**: the envelope references nothing in public — the spec
 * defines no tags, and whatever the inner note points at is inside the ciphertext. Exposing
 * those references on the envelope would leak exactly what PNS hides.
 *
 * **Local keys only**: building or reading one needs the raw device secret (an HKDF over it),
 * which NIP-46 / NIP-55 remote signers can't provide; see [PnsKeys].
 */
@Immutable
class PnsEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    HasInnerEvent {
    // `@Volatile`: set by the decrypting coroutine in [decryptThrowing], read
    // by relay socket threads walking the envelope → inner chain.
    @kotlinx.serialization.Transient
    @kotlin.jvm.Transient
    @Volatile
    override var innerEventId: HexKey? = null

    fun copyNoContent(): PnsEvent {
        val copy = PnsEvent(id, pubKey, createdAt, tags, "", sig)
        copy.innerEventId = innerEventId
        return copy
    }

    override fun isContentEncoded() = true

    /** True when this envelope was signed by [keys]' PNS keypair, i.e. it is one of this device's notes. */
    fun isFrom(keys: PnsKeys) = pubKey == keys.pubKey

    /**
     * Decrypts and validates the inner note (spec §7) and records it as [innerEventId].
     *
     * Throws when the envelope is not signed by [keys]' PNS keypair, the NIP-44 payload does not
     * decrypt under [PnsKeys.nip44Key], the plaintext is not an event, a rumor claims an author
     * other than the device key, or a signed inner event does not verify. See [parseInner].
     */
    fun decryptThrowing(keys: PnsKeys): Event {
        check(isFrom(keys)) { "PNS event $id is signed by $pubKey, not this device's PNS key ${keys.pubKey}" }

        val inner = parseInner(keys.decrypt(content), keys, createdAt)
        innerEventId = inner.id
        return inner
    }

    /** [decryptThrowing], or null when this envelope is not a valid note of [keys]' device. */
    fun decryptOrNull(keys: PnsKeys): Event? =
        try {
            decryptThrowing(keys)
        } catch (e: Exception) {
            Log.d("PnsEvent") { "Couldn't decrypt PNS event $id: ${e.message}" }
            null
        }

    companion object {
        const val KIND = 1080

        /**
         * Template of an envelope whose content is an already-encrypted PNS payload. No tags:
         * the spec defines none and any tag would be readable by every relay.
         */
        fun build(
            encryptedContent: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<PnsEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, encryptedContent, createdAt, initializer)

        /**
         * Assembles a rumor (unsigned inner event authored by the device key, id computed) from
         * [template]. This is the form the spec recommends for the inner note.
         */
        fun <T : Event> assembleRumor(
            template: EventTemplate<T>,
            keys: PnsKeys,
        ): T = RumorAssembler.assembleRumor(keys.devicePubKey, template)

        /** Builds the rumor of [template] (see [assembleRumor]) and stores it in a signed PNS envelope. */
        fun <T : Event> createFromTemplate(
            template: EventTemplate<T>,
            keys: PnsKeys,
            createdAt: Long = TimeUtils.now(),
        ): PnsEvent = create(assembleRumor(template, keys), keys, createdAt)

        /**
         * Encrypts [inner] under [keys] and signs the kind-1080 envelope with the PNS keypair
         * (spec §5–§6).
         *
         * An unsigned [inner] (blank `sig`) is written as a rumor — `sig` dropped — and must be
         * authored by the device key: the spec allows dropping the signature "only if the pubkey
         * matches the original nsec's pubkey". A signed [inner] may have any author and is
         * written as-is.
         */
        fun create(
            inner: Event,
            keys: PnsKeys,
            createdAt: Long = TimeUtils.now(),
        ): PnsEvent {
            val json =
                if (inner.sig.isEmpty()) {
                    require(inner.pubKey == keys.devicePubKey) {
                        "A PNS rumor must be authored by the device key ${keys.devicePubKey}, not ${inner.pubKey}"
                    }
                    Rumor.toJson(Rumor.create(inner))
                } else {
                    inner.toJson()
                }

            return keys.signer.sign(build(keys.encrypt(json), createdAt))
        }

        /**
         * Parses a decrypted PNS plaintext into the inner event (spec §4, §7):
         *
         * - **Signed** (non-empty `sig`): returned as-is when its id and signature verify — any
         *   author is allowed. One that does not verify is rejected ("discard if it's invalid").
         * - **Rumor** (no `sig`): its `pubkey` must be the device key's ("an unsigned nostr event
         *   where the pubkey matches the original nsec's pubkey"). A missing `pubkey` is read as
         *   the device key, as nostrdb's ingester does, since the envelope already proves
         *   authorship. The id is always recomputed rather than trusted, a missing `created_at`
         *   falls back to [envelopeCreatedAt], and a missing `kind` is rejected.
         */
        fun parseInner(
            json: String,
            keys: PnsKeys,
            envelopeCreatedAt: Long,
        ): Event {
            val rumor = Rumor.fromJson(json)
            val claimedAuthor = rumor.pubKey?.ifBlank { null }

            if (claimedAuthor != null) {
                // Rumor drops `sig`; the full parse tells a signed inner event from a rumor.
                val event = Event.fromJson(json)
                if (event.sig.isNotEmpty()) {
                    check(event.verify()) { "PNS inner event ${event.id} has an invalid id or signature" }
                    return event
                }
                check(claimedAuthor == keys.devicePubKey) {
                    "PNS rumor claims author $claimedAuthor, not the device key ${keys.devicePubKey}"
                }
            }

            val kind = checkNotNull(rumor.kind) { "PNS rumor has no kind" }
            val createdAt = rumor.createdAt ?: envelopeCreatedAt
            val tags = rumor.tags ?: emptyArray()
            val content = rumor.content ?: ""

            return EventFactory.create(
                id = EventHasher.hashId(keys.devicePubKey, createdAt, kind, tags, content),
                pubKey = keys.devicePubKey,
                createdAt = createdAt,
                kind = kind,
                tags = tags,
                content = content,
                sig = "",
            )
        }
    }
}
