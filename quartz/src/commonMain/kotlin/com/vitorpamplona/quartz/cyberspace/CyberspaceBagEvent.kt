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
package com.vitorpamplona.quartz.cyberspace

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.KotlinSerializationMapper
import com.vitorpamplona.quartz.utils.ciphers.AESGCM
import kotlinx.serialization.json.JsonArray
import kotlin.io.encoding.Base64

/**
 * One item out of an opened bag (`CYBERSPACE_V2.md` §7.6).
 *
 * [verified] is the only thing that decides whose words these are. §7.6: an item
 * "MAY be signed. If it carries a `sig`, its `id` MUST be the canonical id
 * (§8.2) and the signature MUST verify... An item without a `sig` is allowed,
 * because some content is deliberately left unsigned; its `pubkey` is then a
 * claim, and readers MUST NOT present it as verified."
 *
 * So placement is always attributable to the bag's author, and authorship of the
 * content only to [Event.pubKey] and only when this is true. "A signed item
 * written by one key and hidden by another is therefore shown as that author's
 * content, placed here by the hider."
 */
@Immutable
class CyberspaceBagItem(
    val event: Event,
    val verified: Boolean,
) {
    /**
     * §7.6: an item MAY carry a `C` tag, "its exact coordinate (§2), which lets
     * a client render it at a point rather than somewhere in the region."
     */
    fun coordinate(): String? = event.tags.firstOrNull { it.size > 1 && it[0] == "C" }?.get(1)
}

/** What a bag turned out to hold, once its key was found (§7.6). */
@Immutable
sealed class CyberspaceBagContents {
    /**
     * "A JSON array whose elements are nostr events, signed or unsigned.
     * Readers MUST try this shape first, because a list of items is the shape
     * clients render item by item."
     */
    @Immutable
    class Items(
        val items: List<CyberspaceBagItem>,
        /** How many elements were dropped for a bad id or signature. */
        val dropped: Int,
    ) : CyberspaceBagContents()

    /**
     * "Anything that is not a list of items, such as a text note or a file. Its
     * interpretation is application-defined."
     */
    @Immutable
    class Opaque(
        val bytes: ByteArray,
    ) : CyberspaceBagContents()
}

/**
 * `CYBERSPACE_V2.md` §7.6 and §8.6 — content hidden at a place.
 *
 * A bag is one addressable event holding everything its author has hidden in one
 * region at one height. The ciphertext is public and a relay will hand it to
 * anyone; only someone who has computed that region's key can open it, "whether
 * they computed it by moving into the region or by deriving it for the
 * coordinate directly". That is §7.1's chalk on the sidewalk: not secrecy, but a
 * thing you cannot read without paying the cost of being where it is.
 *
 * The `d` tag is the region's `lookup_id` — a hash of the key (§7.2) — so the
 * address is safe to publish and buys nothing on its own. That is also what a
 * §7.7 sweep queries on: derive a candidate region's key, hash it, ask for the
 * bag with that `d`.
 *
 * **A bag that will not open is not a broken bag.** §7.6 is explicit: "An
 * attempt with the wrong key fails at the GCM tag check and reveals nothing
 * about the plaintext. A failed decryption therefore means only that the reader
 * does not hold this region's key; it MUST NOT be treated as an error in the
 * bag." So [open] returns null and a client says nothing.
 */
@Immutable
class CyberspaceBagEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** The `d` tag: this region's `lookup_id` (§7.2), and the address a sweep asks for. */
    fun lookupId(): HexKey? = tags.firstOrNull { it.size > 1 && it[0] == "d" }?.get(1)

    /**
     * The `h` tag: "the height of the region whose key encrypts the content,
     * which is the discovery radius of §7.3". Optional, and a hint is read
     * against it.
     */
    fun height(): Int? = tags.firstOrNull { it.size > 1 && it[0] == "h" }?.get(1)?.toIntOrNull()

    /**
     * §8.6: "`version` names the rules of §7.6. A reader MUST ignore a bag whose
     * version it does not know."
     */
    fun isKnownVersion(): Boolean = tags.firstOrNull { it.size > 1 && it[0] == "version" }?.get(1) == VERSION

    fun hint(): CyberspaceHint? = CyberspaceHint.read(tags, height())

    /**
     * The `encrypted` tag's payload, or null when the bag carries none or names
     * a cipher this does not implement.
     *
     * §7.6 fixes the cipher: AES-256-GCM, a fresh 12-byte nonce, a 16-byte tag,
     * `nonce || ciphertext || tag`, base64. Only the lookup and the key are
     * normative across the protocol; the cipher is the convention the reference
     * CLI and ONOSENDAI share, so a bag naming a different one is somebody
     * else's format rather than a malformed bag.
     */
    fun payload(): ByteArray? {
        val tag = tags.firstOrNull { it.size > 2 && it[0] == "encrypted" } ?: return null
        if (tag[1] != ALGORITHM) return null
        return try {
            Base64.decode(tag[2])
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Open this bag with a region key, or null when the key is not this
     * region's — which §7.6 says is not an error and reveals nothing.
     *
     * @param key the 32-byte `location_decryption_key` of §7.2.
     */
    fun open(key: ByteArray): CyberspaceBagContents? {
        if (!isKnownVersion()) return null
        if (key.size != KEY_BYTES) return null

        val payload = payload() ?: return null
        if (payload.size < NONCE_BYTES + TAG_BYTES) return null

        val nonce = payload.copyOfRange(0, NONCE_BYTES)
        val sealed = payload.copyOfRange(NONCE_BYTES, payload.size)
        val plaintext =
            try {
                AESGCM(key, nonce).decryptOrNull(sealed)
            } catch (_: Exception) {
                null
            } ?: return null

        return read(plaintext)
    }

    companion object {
        const val KIND = 33330

        /** §7.6's cipher, as the `encrypted` tag names it. */
        const val ALGORITHM = "aes-256-gcm"

        /** §8.6's `version` tag: the rules of §7.6. */
        const val VERSION = "2"

        const val KEY_BYTES = 32
        const val NONCE_BYTES = 12
        const val TAG_BYTES = 16

        /**
         * §7.6's two plaintext shapes, in the order it requires: "Readers MUST
         * try this shape first, because a list of items is the shape clients
         * render item by item."
         */
        fun read(plaintext: ByteArray): CyberspaceBagContents = readItems(plaintext) ?: CyberspaceBagContents.Opaque(plaintext)

        /**
         * A JSON array of nostr events, each verified on its own.
         *
         * §7.6: "a reader MUST drop an item that fails either check, **and only
         * that item**, because one corrupt or forged item says nothing about the
         * others." So a bad element costs itself and nothing else — and a
         * plaintext that is not an array of events at all is not a list, which
         * makes it opaque rather than broken.
         */
        private fun readItems(plaintext: ByteArray): CyberspaceBagContents.Items? {
            val text = plaintext.decodeToString()
            if (text.trimStart().firstOrNull() != '[') return null

            val elements =
                try {
                    KotlinSerializationMapper.json.parseToJsonElement(text) as? JsonArray
                } catch (_: Exception) {
                    null
                } ?: return null

            val items = mutableListOf<CyberspaceBagItem>()
            var dropped = 0
            var understood = 0
            for (element in elements) {
                val event =
                    try {
                        Event.fromJson(element.toString())
                    } catch (_: Exception) {
                        // Not an event at all. A plaintext of arbitrary JSON is
                        // not "a list of items", so this is not a dropped item;
                        // it is evidence the whole shape is the other one.
                        return null
                    }
                understood++
                if (event.sig.isBlank()) {
                    // "An item without a `sig` is allowed, because some content
                    // is deliberately left unsigned; its `pubkey` is then a
                    // claim." Kept, and marked.
                    items.add(CyberspaceBagItem(event, verified = false))
                } else if (event.verify()) {
                    items.add(CyberspaceBagItem(event, verified = true))
                } else {
                    dropped++
                }
            }
            if (understood == 0) return null
            return CyberspaceBagContents.Items(items, dropped)
        }
    }
}
