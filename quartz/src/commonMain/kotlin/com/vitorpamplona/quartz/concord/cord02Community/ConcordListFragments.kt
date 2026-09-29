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
package com.vitorpamplona.quartz.concord.cord02Community

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.io.encoding.Base64

/**
 * The wire layer of the fragmented Community List (CORD-02 §8, kind 33302).
 *
 * Two JSON shapes meet here:
 *
 * - **internal** — what [ConcordCommunityList] reads and writes, and what the retired kind-13302
 *   document always was: 32-byte values in lowercase hex, every snapshot carrying its own
 *   `community_id`, `seed` always present. Merges ([mergeDocs]) and the canonical-bytes
 *   tie-break run on this shape, as they do in the reference client.
 * - **wire** — one fragment's plaintext: `{"frags": n, "entries": […], "tombstones": […]}`, every
 *   32-byte value **this section names** as unpadded base64url (43 chars) at any depth, embedded
 *   snapshots without `community_id`, `seed` omitted when it equals `current`, and entries a
 *   tombstone outranks left out.
 *
 * ## Byte identity is the contract
 *
 * Two devices holding identical state must serialize identical bytes, or the tie-break flaps
 * between their republishes. So the emitter here is hand-ordered to match the reference
 * implementations (Armada `listFrag.ts`, Vector `list_frag.rs`): named fields in their declared
 * order, optional ones omitted rather than null, then unknown fields in sorted key order with
 * recursively key-sorted values. The packer is the same greedy first-fit, against the same
 * 56 KiB target, so identical state also fragments identically.
 *
 * Unknown fields are never decoded or re-encoded: base64url and hex are indistinguishable for
 * a string we don't know the meaning of, so an unknown field keeps its author's spelling.
 */
object ConcordListFragments {
    /** Pack target for one fragment's fully encoded event (the reference client's 56 KiB). */
    const val PACK_TARGET_BYTES = 57_344

    /** The refusal line: no fragment event may exceed this many bytes, fully encoded. */
    const val EVENT_CEILING_BYTES = 65_536

    /** Junk ceiling on a declared `frags`, so one bad fragment can't drive an unbounded scan. */
    const val MAX_DECLARED_FRAGS = 4096

    /** Non-content event bytes (id, pubkey, sig, tags, scaffolding) — deliberately generous. */
    private const val EVENT_ENVELOPE_BYTES = 320

    private val MATERIAL_KEYS = setOf("owner", "owner_salt", "community_root", "root_epoch", "control_pk", "control_root", "channels", "relays", "name")
    private val CHANNEL_KEYS = setOf("id", "key", "epoch", "name")
    private val ENTRY_KEYS = setOf("community_id", "seed", "current", "added_at")
    private val TOMBSTONE_KEYS = setOf("community_id", "removed_at")
    private val LIST_KEYS = setOf("frags", "entries", "tombstones")

    private val json = Json { prettyPrint = false }

    private val B64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    private val HEX64 = Regex("^[0-9a-fA-F]{64}$")
    private val B64URL43 = Regex("^[A-Za-z0-9_-]{43}$")

    // ---- encoding --------------------------------------------------------------------------

    /** 32 bytes of hex to unpadded base64url; anything else passes through untouched. */
    fun hexToB64(value: String): String = if (HEX64.matches(value)) B64.encode(value.hexToByteArray()) else value

    /** Unpadded base64url of 32 bytes back to lowercase hex; anything else passes through. */
    fun b64ToHex(value: String): String {
        if (!B64URL43.matches(value)) return value
        val bytes =
            try {
                B64.decode(value)
            } catch (_: Exception) {
                return value
            }
        return if (bytes.size == 32) bytes.toHexKey() else value
    }

    // ---- canonical JSON --------------------------------------------------------------------

    /** [element] with every object's keys sorted, recursively — a total order for tie-breaks. */
    fun canonical(element: JsonElement): JsonElement =
        when (element) {
            is JsonObject -> JsonObject(element.keys.sorted().associateWithTo(LinkedHashMap()) { canonical(element.getValue(it)) })
            is JsonArray -> JsonArray(element.map { canonical(it) })
            else -> element
        }

    fun canonicalString(element: JsonElement): String = json.encodeToString(JsonElement.serializer(), canonical(element))

    private fun str(element: JsonElement): String = json.encodeToString(JsonElement.serializer(), element)

    /** Appends [source]'s keys outside [named] in sorted order, values canonicalized. */
    private fun MutableMap<String, JsonElement>.putExtras(
        source: JsonObject,
        named: Set<String>,
    ) {
        for (k in source.keys.filter { it !in named }.sorted()) {
            val v = source.getValue(k)
            if (v is JsonNull) continue
            put(k, canonical(v))
        }
    }

    // ---- small typed readers (strict, like the reference parser) -------------------------------

    private class FragmentParseException(
        message: String,
    ) : IllegalArgumentException(message)

    private fun obj(
        v: JsonElement?,
        what: String,
    ): JsonObject = v as? JsonObject ?: throw FragmentParseException("$what is not an object")

    private fun string(
        v: JsonElement?,
        what: String,
    ): String {
        val p = v as? JsonPrimitive
        if (p == null || !p.isString) throw FragmentParseException("$what is not a string")
        return p.content
    }

    private fun u64(
        v: JsonElement?,
        what: String,
    ): Long {
        val p = v as? JsonPrimitive
        if (p == null || p.isString) throw FragmentParseException("$what is not an unsigned integer")
        val n = p.longOrNull ?: throw FragmentParseException("$what is not an unsigned integer")
        if (n < 0) throw FragmentParseException("$what is negative")
        return n
    }

    private fun absent(v: JsonElement?) = v == null || v is JsonNull

    private fun array(
        v: JsonElement?,
        what: String,
    ): List<JsonElement> =
        when {
            v == null -> emptyList()
            v is JsonArray -> v
            else -> throw FragmentParseException("$what is not an array")
        }

    // ---- wire -> internal ------------------------------------------------------------------

    /** One decoded fragment: its declared `frags` and its content in the internal shape. */
    class DecodedFragment(
        val frags: Int,
        val doc: JsonObject,
    )

    /**
     * Parses one fragment's decrypted plaintext into the internal shape. Throws on anything the
     * reference parser rejects; a caller treats that as "this index is missing", never as an
     * empty fragment.
     */
    fun decodeFragment(plaintext: String): DecodedFragment {
        val root = obj(json.parseToJsonElement(plaintext), "fragment")
        val frags = u64(root["frags"], "frags")
        val entries = array(root["entries"], "entries").map { decodeEntry(it) }
        val tombstones = array(root["tombstones"], "tombstones").map { decodeTombstone(it) }
        val doc = LinkedHashMap<String, JsonElement>()
        for ((k, v) in root) if (k !in LIST_KEYS) doc[k] = v
        doc["entries"] = JsonArray(entries)
        doc["tombstones"] = JsonArray(tombstones)
        return DecodedFragment(frags.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), JsonObject(doc))
    }

    private fun decodeEntry(v: JsonElement): JsonObject {
        val o = obj(v, "entry")
        val cid = b64ToHex(string(o["community_id"], "entry.community_id"))
        val current = decodeMaterial(o["current"], "entry.current", cid)
        val addedAt = u64(o["added_at"], "entry.added_at")
        val seed = if (absent(o["seed"])) current else decodeMaterial(o["seed"], "entry.seed", cid)
        val out = LinkedHashMap<String, JsonElement>()
        for ((k, value) in o) if (k !in ENTRY_KEYS) out[k] = value
        out["community_id"] = JsonPrimitive(cid)
        out["seed"] = seed
        out["current"] = current
        out["added_at"] = JsonPrimitive(addedAt)
        return JsonObject(out)
    }

    private fun decodeMaterial(
        v: JsonElement?,
        what: String,
        communityId: String,
    ): JsonObject {
        val o = obj(v, what)
        val out = LinkedHashMap<String, JsonElement>()
        for ((k, value) in o) if (k !in MATERIAL_KEYS && k != "community_id") out[k] = value
        out["community_id"] = JsonPrimitive(communityId)
        out["owner"] = JsonPrimitive(b64ToHex(string(o["owner"], "$what.owner")))
        out["owner_salt"] = JsonPrimitive(b64ToHex(string(o["owner_salt"], "$what.owner_salt")))
        out["community_root"] = JsonPrimitive(b64ToHex(string(o["community_root"], "$what.community_root")))
        out["root_epoch"] = JsonPrimitive(u64(o["root_epoch"], "$what.root_epoch"))
        if (!absent(o["control_pk"])) out["control_pk"] = JsonPrimitive(b64ToHex(string(o["control_pk"], "$what.control_pk")))
        if (!absent(o["control_root"])) out["control_root"] = JsonPrimitive(b64ToHex(string(o["control_root"], "$what.control_root")))
        out["channels"] = JsonArray(array(o["channels"], "$what.channels").map { decodeChannel(it) })
        out["relays"] = JsonArray(array(o["relays"], "$what.relays").map { JsonPrimitive(string(it, "$what.relays[]")) })
        out["name"] = JsonPrimitive(string(o["name"], "$what.name"))
        return JsonObject(out)
    }

    private fun decodeChannel(v: JsonElement): JsonObject {
        val o = obj(v, "channel")
        val out = LinkedHashMap<String, JsonElement>()
        for ((k, value) in o) if (k !in CHANNEL_KEYS) out[k] = value
        out["id"] = JsonPrimitive(b64ToHex(string(o["id"], "channel.id")))
        if (!absent(o["key"])) out["key"] = JsonPrimitive(b64ToHex(string(o["key"], "channel.key")))
        out["epoch"] = JsonPrimitive(u64(o["epoch"], "channel.epoch"))
        out["name"] = JsonPrimitive(string(o["name"], "channel.name"))
        return JsonObject(out)
    }

    private fun decodeTombstone(v: JsonElement): JsonObject {
        val o = obj(v, "tombstone")
        val out = LinkedHashMap<String, JsonElement>()
        for ((k, value) in o) if (k !in TOMBSTONE_KEYS) out[k] = value
        out["community_id"] = JsonPrimitive(b64ToHex(string(o["community_id"], "tombstone.community_id")))
        out["removed_at"] = JsonPrimitive(u64(o["removed_at"], "tombstone.removed_at"))
        return JsonObject(out)
    }

    // ---- internal -> wire ------------------------------------------------------------------

    private fun stringOr(
        v: JsonElement?,
        default: String,
    ): String = (v as? JsonPrimitive)?.takeIf { it.isString }?.content ?: default

    private fun number(v: JsonElement?): JsonPrimitive? = (v as? JsonPrimitive)?.takeIf { !it.isString && it.longOrNull != null }

    /**
     * An internal snapshot in the wire shape: named 32-byte values as base64url, `community_id`
     * dropped (the entry carries it), empty `channels`/`relays` omitted. Throws on a snapshot
     * missing its keys rather than sealing a corrupt membership.
     */
    private fun materialToWire(m: JsonObject): JsonObject {
        val owner = stringOr(m["owner"], "")
        val ownerSalt = stringOr(m["owner_salt"], "")
        val root = stringOr(m["community_root"], "")
        val epoch = number(m["root_epoch"])
        require(owner.isNotEmpty() && ownerSalt.isNotEmpty() && root.isNotEmpty() && epoch != null) {
            "malformed join material — refusing to serialize a corrupt snapshot"
        }
        val out = LinkedHashMap<String, JsonElement>()
        out["owner"] = JsonPrimitive(hexToB64(owner))
        out["owner_salt"] = JsonPrimitive(hexToB64(ownerSalt))
        out["community_root"] = JsonPrimitive(hexToB64(root))
        out["root_epoch"] = epoch
        (m["control_pk"] as? JsonPrimitive)?.takeIf { it.isString }?.let { out["control_pk"] = JsonPrimitive(hexToB64(it.content)) }
        (m["control_root"] as? JsonPrimitive)?.takeIf { it.isString }?.let { out["control_root"] = JsonPrimitive(hexToB64(it.content)) }
        val channels = (m["channels"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let(::channelToWire) }.orEmpty()
        if (channels.isNotEmpty()) out["channels"] = JsonArray(channels)
        val relays = (m["relays"] as? JsonArray)?.filter { it is JsonPrimitive && it.isString }.orEmpty()
        if (relays.isNotEmpty()) out["relays"] = JsonArray(relays)
        out["name"] = JsonPrimitive(stringOr(m["name"], ""))
        out.putExtras(m, MATERIAL_KEYS + "community_id")
        return JsonObject(out)
    }

    private fun channelToWire(c: JsonObject): JsonObject? {
        val id = stringOr(c["id"], "")
        val epoch = number(c["epoch"])
        if (id.isEmpty() || epoch == null) return null
        val out = LinkedHashMap<String, JsonElement>()
        out["id"] = JsonPrimitive(hexToB64(id))
        (c["key"] as? JsonPrimitive)?.takeIf { it.isString }?.let { out["key"] = JsonPrimitive(hexToB64(it.content)) }
        out["epoch"] = epoch
        out["name"] = JsonPrimitive(stringOr(c["name"], ""))
        out.putExtras(c, CHANNEL_KEYS)
        return JsonObject(out)
    }

    /**
     * `seed` with its cosmetic fields (`name`, `relays`, each channel's `name`) overwritten from
     * `current`: they are not the anchor's own, and leaving a stale label in place would fork the
     * two snapshots forever after one rename (CORD-02 §8).
     */
    private fun withCurrentCosmetics(
        seed: JsonObject,
        current: JsonObject,
    ): JsonObject {
        val currentNames =
            (current["channels"] as? JsonArray)
                .orEmpty()
                .mapNotNull { it as? JsonObject }
                .associate { stringOr(it["id"], "") to (it["name"] ?: JsonPrimitive("")) }
        // Rebuilt in declared order so the result serializes exactly like a fresh wire snapshot.
        val out = LinkedHashMap<String, JsonElement>()
        for (k in listOf("owner", "owner_salt", "community_root", "root_epoch", "control_pk", "control_root")) seed[k]?.let { out[k] = it }
        (seed["channels"] as? JsonArray)?.let { channels ->
            out["channels"] =
                JsonArray(
                    channels.map { ch ->
                        val o = ch as? JsonObject ?: return@map ch
                        val name = currentNames[stringOr(o["id"], "")] ?: return@map o
                        JsonObject(o.mapValuesTo(LinkedHashMap()) { (k, v) -> if (k == "name") name else v })
                    },
                )
        }
        current["relays"]?.let { out["relays"] = it }
        out["name"] = current["name"] ?: JsonPrimitive("")
        // The seed's unknown keys, already in sorted order behind the named ones.
        for ((k, v) in seed) if (k !in MATERIAL_KEYS) out[k] = v
        return JsonObject(out)
    }

    /** One internal entry in the wire shape, or throws on corrupt join material. */
    fun entryToWire(entry: JsonObject): JsonObject {
        val cid = stringOr(entry["community_id"], "")
        require(cid.isNotEmpty()) { "entry without a community_id" }
        val currentInternal = (entry["current"] as? JsonObject) ?: (entry["seed"] as? JsonObject) ?: throw IllegalArgumentException("entry without join material")
        val seedInternal = (entry["seed"] as? JsonObject) ?: currentInternal
        val current = materialToWire(currentInternal)
        val seed = withCurrentCosmetics(materialToWire(seedInternal), current)
        val out = LinkedHashMap<String, JsonElement>()
        out["community_id"] = JsonPrimitive(hexToB64(cid))
        if (str(seed) != str(current)) out["seed"] = seed
        out["current"] = current
        out["added_at"] = number(entry["added_at"]) ?: JsonPrimitive(0L)
        out.putExtras(entry, ENTRY_KEYS)
        return JsonObject(out)
    }

    fun tombstoneToWire(tombstone: JsonObject): JsonObject? {
        val cid = stringOr(tombstone["community_id"], "")
        if (cid.isEmpty()) return null
        val out = LinkedHashMap<String, JsonElement>()
        out["community_id"] = JsonPrimitive(hexToB64(cid))
        out["removed_at"] = number(tombstone["removed_at"]) ?: JsonPrimitive(0L)
        out.putExtras(tombstone, TOMBSTONE_KEYS)
        return JsonObject(out)
    }

    /** A fragment's exact plaintext: what NIP-44 seals and relays store. */
    fun serializeFragment(
        frags: Int,
        entries: List<JsonObject>,
        tombstones: List<JsonObject>,
        extras: JsonObject = JsonObject(emptyMap()),
    ): String {
        val out = LinkedHashMap<String, JsonElement>()
        out["frags"] = JsonPrimitive(frags)
        if (entries.isNotEmpty()) out["entries"] = JsonArray(entries)
        if (tombstones.isNotEmpty()) out["tombstones"] = JsonArray(tombstones)
        out.putExtras(extras, LIST_KEYS)
        return str(JsonObject(out))
    }

    /** The zero-element List an emptied index republishes (CORD-02 §8). */
    fun emptyFragment(frags: Int): String = serializeFragment(frags, emptyList(), emptyList())

    // ---- sizing ----------------------------------------------------------------------------

    /** NIP-44 v2 padded plaintext length. */
    fun nip44PaddedLen(unpadded: Int): Int {
        if (unpadded <= 32) return 32
        val nextPower = 1 shl (32 - (unpadded - 1).countLeadingZeroBits())
        val chunk = if (nextPower <= 256) 32 else nextPower / 8
        return chunk * ((unpadded - 1) / chunk + 1)
    }

    /** The encoded event a plaintext of [plaintextBytes] becomes (NIP-44 v2 payload, base64). */
    fun projectedEventBytes(plaintextBytes: Int): Int {
        val raw = 1 + 32 + 2 + nip44PaddedLen(plaintextBytes) + 32
        return (raw + 2) / 3 * 4 + EVENT_ENVELOPE_BYTES
    }

    private fun byteLen(s: String) = s.encodeToByteArray().size

    // ---- merge (internal shape) ------------------------------------------------------------

    private fun epochOf(m: JsonObject?): Long = number(m?.get("root_epoch"))?.longOrNull ?: 0L

    /** Higher epoch wins; a tie goes to the lexicographically lowest canonical bytes. */
    private fun freshest(
        a: JsonObject,
        b: JsonObject,
    ): JsonObject {
        val ea = epochOf(a)
        val eb = epochOf(b)
        if (ea != eb) return if (ea > eb) a else b
        return if (canonicalString(a) <= canonicalString(b)) a else b
    }

    /** Lower epoch wins; a tie goes to the lowest canonical bytes. */
    private fun earliest(
        a: JsonObject,
        b: JsonObject,
    ): JsonObject {
        val ea = epochOf(a)
        val eb = epochOf(b)
        if (ea != eb) return if (ea < eb) a else b
        return if (canonicalString(a) <= canonicalString(b)) a else b
    }

    private fun currentOf(e: JsonObject): JsonObject? = (e["current"] as? JsonObject) ?: (e["seed"] as? JsonObject)

    private fun seedOf(e: JsonObject): JsonObject? = (e["seed"] as? JsonObject) ?: (e["current"] as? JsonObject)

    private fun mergeEntry(
        x: JsonObject,
        y: JsonObject,
    ): JsonObject {
        val cx = currentOf(x)
        val cy = currentOf(y)
        val current = if (cx != null && cy != null) freshest(cx, cy) else cx ?: cy
        val sx = seedOf(x)
        val sy = seedOf(y)
        val seed = if (sx != null && sy != null) earliest(sx, sy) else sx ?: sy
        val addedAt = maxOf(number(x["added_at"])?.longOrNull ?: 0L, number(y["added_at"])?.longOrNull ?: 0L)
        val merged = LinkedHashMap<String, JsonElement>(x)
        merged.putAll(y)
        merged["community_id"] = x.getValue("community_id")
        if (seed != null) merged["seed"] = seed
        if (current != null) merged["current"] = current
        merged["added_at"] = JsonPrimitive(addedAt)
        // An exclusion marker only means something while it is beyond the held epoch.
        val excluded = listOfNotNull(number(x["excluded_at_epoch"])?.longOrNull, number(y["excluded_at_epoch"])?.longOrNull).maxOrNull()
        if (excluded != null && excluded > epochOf(current)) merged["excluded_at_epoch"] = JsonPrimitive(excluded) else merged.remove("excluded_at_epoch")
        return JsonObject(merged)
    }

    private fun idOf(o: JsonObject): String? = stringOr(o["community_id"], "").ifEmpty { null }

    /**
     * Merges two internal documents: one membership per `community_id` (current keeps the higher
     * epoch, seed the lower, `added_at` the later), one tombstone per id (the later `removed_at`),
     * other keys with [b] winning. Commutative and idempotent on everything but those unknown
     * document keys, which is what lets fragments and devices merge in any order.
     */
    fun mergeDocs(
        a: JsonObject,
        b: JsonObject,
    ): JsonObject {
        val entries = LinkedHashMap<String, JsonObject>()
        for (e in listOf(a, b).flatMap { (it["entries"] as? JsonArray).orEmpty() }) {
            val o = e as? JsonObject ?: continue
            val id = idOf(o) ?: continue
            entries[id] = entries[id]?.let { mergeEntry(it, o) } ?: o
        }
        val tombstones = LinkedHashMap<String, JsonObject>()
        for (t in listOf(a, b).flatMap { (it["tombstones"] as? JsonArray).orEmpty() }) {
            val o = t as? JsonObject ?: continue
            val id = idOf(o) ?: continue
            val prev = tombstones[id]
            if (prev == null || (number(o["removed_at"])?.longOrNull ?: 0L) > (number(prev["removed_at"])?.longOrNull ?: 0L)) tombstones[id] = o
        }
        val out = LinkedHashMap<String, JsonElement>()
        for ((k, v) in a) if (k != "entries" && k != "tombstones") out[k] = v
        for ((k, v) in b) if (k != "entries" && k != "tombstones") out[k] = v
        out["entries"] = JsonArray(entries.keys.sorted().map { entries.getValue(it) })
        out["tombstones"] = JsonArray(tombstones.keys.sorted().map { tombstones.getValue(it) })
        return JsonObject(out)
    }

    /** Per community id, the latest `removed_at` in [doc]. */
    fun removals(doc: JsonObject): Map<String, Long> {
        val out = HashMap<String, Long>()
        for (t in (doc["tombstones"] as? JsonArray).orEmpty()) {
            val o = t as? JsonObject ?: continue
            val id = idOf(o) ?: continue
            val at = number(o["removed_at"])?.longOrNull ?: 0L
            if (at > (out[id] ?: Long.MIN_VALUE)) out[id] = at
        }
        return out
    }

    /** A membership is live only while its entry outranks its removal (CORD-02 §8). */
    fun isLive(
        entry: JsonObject,
        removals: Map<String, Long>,
    ): Boolean {
        val removedAt = removals[idOf(entry) ?: return false] ?: return true
        return (number(entry["added_at"])?.longOrNull ?: 0L) > removedAt
    }

    // ---- packing ---------------------------------------------------------------------------

    private class Packing(
        val entries: MutableList<JsonObject> = ArrayList(),
        val tombstones: MutableList<JsonObject> = ArrayList(),
        val extras: JsonObject = JsonObject(emptyMap()),
    ) {
        fun serialize(frags: Int) = serializeFragment(frags, entries, tombstones, extras)
    }

    /**
     * Splits an internal document into fragment plaintexts, greedily (first fragment with room)
     * under [PACK_TARGET_BYTES]. Entries a tombstone outranks are dropped — the tombstone alone
     * carries the state — and document-level unknown keys ride on fragment 0.
     */
    fun pack(doc: JsonObject): List<String> {
        val removals = removals(doc)
        val entries =
            (doc["entries"] as? JsonArray)
                .orEmpty()
                .mapNotNull { it as? JsonObject }
                .filter { isLive(it, removals) }
                .sortedBy { idOf(it) }
                .map { entryToWire(it) }
        val tombstones =
            (doc["tombstones"] as? JsonArray)
                .orEmpty()
                .mapNotNull { it as? JsonObject }
                .sortedBy { idOf(it) }
                .mapNotNull { tombstoneToWire(it) }
        val docExtras = JsonObject(doc.filterKeys { it != "entries" && it != "tombstones" && it !in LIST_KEYS })

        val frags = mutableListOf(Packing(extras = docExtras))

        fun fits(
            f: Packing,
            add: Int,
        ) = projectedEventBytes(byteLen(f.serialize(1)) + add) <= PACK_TARGET_BYTES

        for (e in entries) {
            val cost = byteLen(str(e)) + 1
            val last = frags.last()
            if (last.entries.isEmpty() || fits(last, cost)) last.entries.add(e) else frags.add(Packing(entries = mutableListOf(e)))
        }
        for (t in tombstones) {
            val cost = byteLen(str(t)) + 1
            val last = frags.last()
            if (last.tombstones.isEmpty() || fits(last, cost)) last.tombstones.add(t) else frags.add(Packing(tombstones = mutableListOf(t)))
        }
        val total = frags.size
        return frags.map { it.serialize(total) }
    }

    /**
     * Re-serializes one held fragment ([fragmentDoc], internal shape) after replacing its
     * memberships and removals for [ids] with [merged]'s, keeping its declared [frags]. This is
     * the scoped write (CORD-02 §8): it touches only the fragment holding the changed membership,
     * so a fragment stranded on an unreachable relay never blocks a join or a leave.
     */
    fun rewriteFragment(
        fragmentDoc: JsonObject,
        merged: JsonObject,
        ids: Set<String>,
        frags: Int,
    ): String {
        fun pick(
            doc: JsonObject,
            key: String,
        ) = (doc[key] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

        val keptEntries = pick(fragmentDoc, "entries").filter { idOf(it) !in ids } + pick(merged, "entries").filter { idOf(it) in ids }
        val keptTombs = pick(fragmentDoc, "tombstones").filter { idOf(it) !in ids } + pick(merged, "tombstones").filter { idOf(it) in ids }
        val doc =
            JsonObject(
                fragmentDoc.filterKeys { it != "entries" && it != "tombstones" } +
                    mapOf("entries" to JsonArray(keptEntries), "tombstones" to JsonArray(keptTombs)),
            )
        val removals = removals(doc)
        return serializeFragment(
            frags,
            keptEntries.filter { isLive(it, removals) }.sortedBy { idOf(it) }.map { entryToWire(it) },
            keptTombs.sortedBy { idOf(it) }.mapNotNull { tombstoneToWire(it) },
            JsonObject(doc.filterKeys { it != "entries" && it != "tombstones" && it !in LIST_KEYS }),
        )
    }

    /** The community ids a document mentions, in entries or tombstones. */
    fun idsIn(doc: JsonObject): Set<String> =
        ((doc["entries"] as? JsonArray).orEmpty() + (doc["tombstones"] as? JsonArray).orEmpty())
            .mapNotNullTo(HashSet()) { (it as? JsonObject)?.let(::idOf) }
}
