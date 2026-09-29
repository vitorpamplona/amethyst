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
package com.vitorpamplona.quartz.concord.cord04Roles.pins

import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.concord.envelope.OpenedStreamEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip44Encryption.Nip44
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Concord Pins (CORD-04 §7): a pin does not quote a message, it **proves** one.
 *
 * One Pin List per Channel lives on the Control Plane (vsk 11, coordinate
 * `pins_locator(community_id, channel_id)`), replaced entire per edit. Each entry carries the
 * message's original kind-20013 seal, verbatim, plus the 76-byte NIP-44 key disclosure for that one
 * message ([PinKeyDisclosure]) — so any reader of the Control Plane, with no Chat-plane history and
 * no old keys, can verify author, words, Channel and time. Compaction (CORD-06) re-wraps the list
 * across rotations, which is how a pin reaches members who joined long after the message.
 *
 * An entry's wire shape is `{ "seal": {…}, "keys": "<152 hex>", "wrap"?: "<hex id>", "edit"?:
 * {"seal", "keys"} }`. Entries are kept as the raw JSON they arrived as: the seal's fields must be
 * carried exactly (its signature covers them), and fields we don't model must survive a republish.
 */
object ConcordPins {
    /** At most this many entries — judged by whoever can open the form. */
    const val MAX_ENTRIES = 25

    /** At most this many bytes of edition `content`, judged on the carried bytes, both forms. */
    const val MAX_CONTENT_BYTES = 32_768

    const val KIND_MESSAGE = 9
    const val KIND_COMMENT = 1111
    const val KIND_EDIT = 3302

    private val json = Json { prettyPrint = false }
    private val HEX64 = Regex("^[0-9a-f]{64}$")
    private val DECIMAL = Regex("^(0|[1-9][0-9]*)$")

    /** A pin entry that passed every §7 check — safe to render. */
    class VerifiedPin(
        /** Recomputed from the decrypted bytes; never an embedded id. The entry's identity. */
        val rumorId: HexKey,
        /** The seal's signer, equal to the rumor's author. */
        val author: HexKey,
        val kind: Int,
        /** The newest proven words: the attached Edit's when one verified, else the original's. */
        val content: String,
        val tags: Array<Array<String>>,
        /** The message's own `epoch` tag, for jump-to-context. */
        val epoch: String?,
        /** Ordering basis: `created_at * 1000 + ms`. */
        val orderMs: Long,
        val createdAt: Long,
        /** The unverifiable locator hint the entry carried, if any. */
        val wrapHint: HexKey?,
        /** True when a proven Edit supplied [content]. */
        val edited: Boolean,
        /** The proven Edit's rumor id, when one verified. */
        val editRumorId: HexKey?,
        /**
         * The proven Edit's own send time (`created_at * 1000 + ms`), when one verified. A client
         * holding an Edit newer than this MUST mark the pin edited (§7 Edits), and a refresh only
         * ever attaches something newer, or it would silently revert the entry.
         */
        val editOrderMs: Long?,
        /** The wire entry, verbatim, for republishing. */
        val entry: JsonObject,
    )

    /** How a Pin List's content read (§7 Limits). */
    class PinListRead(
        val entries: List<JsonObject>,
        /**
         * True when the list is the sealed form under an epoch key this reader doesn't hold. Such a
         * list is *unavailable*, not empty — a writer MUST NOT build an edition from it.
         */
        val sealedUnavailable: Boolean,
        /** True when the content broke a cap or the format, so every reader treats it as empty. */
        val violating: Boolean,
    ) {
        companion object {
            val EMPTY = PinListRead(emptyList(), sealedUnavailable = false, violating = false)
            val VIOLATING = PinListRead(emptyList(), sealedUnavailable = false, violating = true)
        }
    }

    // ---- reading ---------------------------------------------------------------------------

    private fun parse(s: String): JsonElement? =
        try {
            json.parseToJsonElement(s)
        } catch (_: Exception) {
            null
        }

    /**
     * Reads a Pin List edition's [content]. A cap-violating or malformed edition reads as an empty
     * list (it still folds and chains — refusing it would fork the version chain between
     * implementations). The sealed form opens with [unsealKey], the Channel's conversation key at
     * the named epoch, or reads as [PinListRead.sealedUnavailable] when this reader lacks it.
     */
    fun read(
        content: String,
        unsealKey: (epoch: Long) -> ByteArray?,
    ): PinListRead {
        if (content.encodeToByteArray().size > MAX_CONTENT_BYTES) return PinListRead.VIOLATING
        val root = parse(content) as? JsonObject ?: return PinListRead.VIOLATING
        val entries = root["entries"]
        if (entries != null) return entriesOf(entries)

        val epoch = (root["epoch"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val sealed = (root["sealed"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (epoch == null || sealed == null || !DECIMAL.matches(epoch)) return PinListRead.VIOLATING
        val epochValue = epoch.toLongOrNull() ?: return PinListRead.VIOLATING
        val key = unsealKey(epochValue) ?: return PinListRead(emptyList(), sealedUnavailable = true, violating = false)
        val inner =
            try {
                parse(Nip44.v2.decrypt(sealed, key)) as? JsonObject
            } catch (_: Exception) {
                null
            } ?: return PinListRead.VIOLATING
        return entriesOf(inner["entries"] ?: return PinListRead.VIOLATING)
    }

    private fun entriesOf(element: JsonElement): PinListRead {
        val array = element as? JsonArray ?: return PinListRead.VIOLATING
        if (array.size > MAX_ENTRIES) return PinListRead.VIOLATING
        // A non-object entry is just an invalid entry, dropped alone by the verifier.
        return PinListRead(array.mapNotNull { it as? JsonObject }, sealedUnavailable = false, violating = false)
    }

    /**
     * True when [content] is the self-describing **sealed** form (`{"epoch", "sealed"}`), false for
     * the public `{"entries"}` form or anything unreadable. A writer needs it to honor the
     * private→public rule (§7): a list sealed in a Channel's private era is never mechanically
     * re-formed into the public form, which would disclose private-era pins to everyone.
     */
    fun isSealedForm(content: String): Boolean {
        if (content.encodeToByteArray().size > MAX_CONTENT_BYTES) return false
        val root = parse(content) as? JsonObject ?: return false
        return root["entries"] == null && root["sealed"] != null
    }

    // ---- verification ----------------------------------------------------------------------

    private class OpenedRumor(
        val pubKey: HexKey,
        val kind: Int,
        val createdAt: Long,
        val tags: Array<Array<String>>,
        val content: String,
    ) {
        val id: HexKey by lazy { EventHasher.hashId(pubKey, createdAt, kind, tags, content) }

        fun tag(name: String): String? = tags.firstOrNull { it.size >= 2 && it[0] == name }?.get(1)

        fun orderMs(): Long {
            val raw = tag("ms")
            val ms = raw?.takeIf { DECIMAL.matches(it) }?.toLongOrNull()?.takeIf { it <= 999 } ?: 0L
            return createdAt * 1000 + ms
        }
    }

    /** The seal object as an [Event], only if it is a correctly signed kind-20013 seal. */
    private fun sealOf(element: JsonElement?): Event? {
        val obj = element as? JsonObject ?: return null
        val seal =
            try {
                Event.fromJson(json.encodeToString(JsonObject.serializer(), obj))
            } catch (_: Exception) {
                return null
            }
        if (seal.kind != ConcordStreamEnvelope.KIND_SEAL_ENCRYPTED) return null
        val valid =
            try {
                seal.verify()
            } catch (_: Exception) {
                false
            }
        return seal.takeIf { valid }
    }

    /** Steps 2–4 up to the rumor: MAC, decrypt, unpad, parse, author equality. */
    private fun openRumor(
        seal: Event,
        keysHex: String?,
    ): OpenedRumor? {
        val keys = PinKeyDisclosure.decode(keysHex ?: return null) ?: return null
        val plaintext = PinKeyDisclosure.decryptWith(seal.content, keys) ?: return null
        val obj = parse(plaintext) as? JsonObject ?: return null
        val pubKey = (obj["pubkey"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        val kind = (obj["kind"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull ?: return null
        val createdAt = (obj["created_at"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull ?: return null
        val content = (obj["content"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        val tags =
            (obj["tags"] as? JsonArray)?.map { tag ->
                (tag as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: return null }?.toTypedArray() ?: return null
            } ?: return null
        // NIP-59's impersonation check: renderers display rumor fields.
        if (pubKey != seal.pubKey) return null
        return OpenedRumor(pubKey, kind, createdAt, tags.toTypedArray(), content)
    }

    /**
     * The five §7 verification steps, holding nothing but [entry] and its list's [channelIdHex]:
     * a signed kind-20013 seal; the MAC under the disclosed HMAC key; decrypt, unpad and parse; the
     * rumor's author equals the seal's, its kind is 9 or 1111, and it carries
     * `["channel", channelIdHex]`; and its id recomputed from the bytes. Null on any failure — the
     * entry is then dropped alone. A failing `edit` bundle costs only the revision, never the pin.
     */
    fun verify(
        entry: JsonObject,
        channelIdHex: HexKey,
    ): VerifiedPin? {
        if (!HEX64.matches(channelIdHex)) return null
        val seal = sealOf(entry["seal"]) ?: return null
        val rumor = openRumor(seal, (entry["keys"] as? JsonPrimitive)?.takeIf { it.isString }?.content) ?: return null
        if (rumor.kind != KIND_MESSAGE && rumor.kind != KIND_COMMENT) return null
        // The binding stops a private Channel's keyholder from pinning its messages into a public list.
        if (rumor.tag("channel") != channelIdHex) return null
        val rumorId = rumor.id

        val edit = (entry["edit"] as? JsonObject)?.let { verifyEdit(it, seal.pubKey, rumorId, channelIdHex) }
        return VerifiedPin(
            rumorId = rumorId,
            author = seal.pubKey,
            kind = rumor.kind,
            content = edit?.content ?: rumor.content,
            tags = rumor.tags,
            epoch = rumor.tag("epoch"),
            orderMs = rumor.orderMs(),
            createdAt = rumor.createdAt,
            wrapHint = (entry["wrap"] as? JsonPrimitive)?.contentOrNull?.takeIf { HEX64.matches(it) },
            edited = edit != null,
            editRumorId = edit?.id,
            editOrderMs = edit?.orderMs(),
            entry = entry,
        )
    }

    /** An Edit bundle: the same steps with kind 3302, plus the same author and an `e` naming the original. */
    private fun verifyEdit(
        bundle: JsonObject,
        originalAuthor: HexKey,
        originalRumorId: HexKey,
        channelIdHex: HexKey,
    ): OpenedRumor? {
        val seal = sealOf(bundle["seal"]) ?: return null
        if (seal.pubKey != originalAuthor) return null
        val rumor = openRumor(seal, (bundle["keys"] as? JsonPrimitive)?.takeIf { it.isString }?.content) ?: return null
        if (rumor.kind != KIND_EDIT) return null
        if (rumor.tag("channel") != channelIdHex) return null
        if (rumor.tag("e") != originalRumorId) return null
        return rumor
    }

    // ---- writing ---------------------------------------------------------------------------

    private fun sealJson(seal: Event): JsonObject = parse(seal.toJson()) as JsonObject

    /**
     * Builds the entry for a message this client opened: its verbatim seal, the disclosure derived
     * with the Channel's [conversationKey] at the message's epoch, and the [wrapId] hint. Null when
     * the result would not verify (an unencrypted seal, a wrong epoch key — the MAC catches it).
     */
    fun buildEntry(
        opened: OpenedStreamEvent,
        conversationKey: ByteArray,
        channelIdHex: HexKey,
        wrapId: HexKey?,
    ): JsonObject? {
        val seal = opened.seal
        if (seal.kind != ConcordStreamEnvelope.KIND_SEAL_ENCRYPTED) return null
        val keys = PinKeyDisclosure.discloseFor(seal.content, conversationKey) ?: return null
        val fields = LinkedHashMap<String, JsonElement>()
        fields["seal"] = sealJson(seal)
        fields["keys"] = JsonPrimitive(PinKeyDisclosure.encode(keys))
        if (wrapId != null) fields["wrap"] = JsonPrimitive(wrapId)
        val entry = JsonObject(fields)
        return entry.takeIf { verify(it, channelIdHex) != null }
    }

    /**
     * [entry] with the proof of [edit] attached (§7 Edits), replacing any earlier one — an entry
     * carries at most the newest provable Edit. Returns [entry] unchanged when the bundle would not
     * verify, so a refresh never downgrades it.
     */
    fun withEdit(
        entry: JsonObject,
        edit: OpenedStreamEvent,
        conversationKey: ByteArray,
        channelIdHex: HexKey,
    ): JsonObject {
        val seal = edit.seal
        if (seal.kind != ConcordStreamEnvelope.KIND_SEAL_ENCRYPTED) return entry
        val keys = PinKeyDisclosure.discloseFor(seal.content, conversationKey) ?: return entry
        val bundle = JsonObject(mapOf("seal" to sealJson(seal), "keys" to JsonPrimitive(PinKeyDisclosure.encode(keys))))
        val candidate = JsonObject(entry + ("edit" to bundle))
        return if (verify(candidate, channelIdHex)?.edited == true) candidate else entry
    }

    private fun listJson(entries: List<JsonObject>) = json.encodeToString(JsonObject.serializer(), JsonObject(mapOf("entries" to JsonArray(entries))))

    /** Thrown instead of publishing an edition every reader would read as empty. */
    class PinListTooLargeException(
        message: String,
    ) : IllegalArgumentException(message)

    private fun checkCaps(
        count: Int,
        content: String,
    ): String {
        if (count > MAX_ENTRIES) throw PinListTooLargeException("pin list exceeds $MAX_ENTRIES entries")
        val bytes = content.encodeToByteArray().size
        if (bytes > MAX_CONTENT_BYTES) throw PinListTooLargeException("pin list content is $bytes bytes (cap $MAX_CONTENT_BYTES)")
        return content
    }

    /** A public Channel's list: plaintext, since the Control Plane's wrap is the gate. */
    fun serializePublic(entries: List<JsonObject>): String = checkCaps(entries.size, listJson(entries))

    /** A private Channel's list, sealed under its [conversationKey] at [epoch]; caps judged on the final bytes. */
    fun serializeSealed(
        entries: List<JsonObject>,
        conversationKey: ByteArray,
        epoch: Long,
    ): String {
        if (entries.size > MAX_ENTRIES) throw PinListTooLargeException("pin list exceeds $MAX_ENTRIES entries")
        val sealed = Nip44.v2.encrypt(listJson(entries), conversationKey).encodePayload()
        val content = json.encodeToString(JsonObject.serializer(), JsonObject(mapOf("epoch" to JsonPrimitive(epoch.toString()), "sealed" to JsonPrimitive(sealed))))
        return checkCaps(entries.size, content)
    }

    // ---- deletion --------------------------------------------------------------------------

    /**
     * True when a kind-5 delete by [deleteAuthor] with [deleteTags] kills [pin]: self-erasure
     * outranks curation, so only the pin's proven author can, by naming its recomputed rumor id.
     */
    fun killedBy(
        pin: VerifiedPin,
        deleteAuthor: HexKey,
        deleteTags: Array<Array<String>>,
    ): Boolean = deleteAuthor == pin.author && deleteTags.any { it.size >= 2 && it[0] == "e" && it[1] == pin.rumorId }
}
