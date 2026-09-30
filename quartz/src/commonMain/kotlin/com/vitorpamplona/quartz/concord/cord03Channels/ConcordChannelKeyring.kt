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
package com.vitorpamplona.quartz.concord.cord03Channels

import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.concord.cord02Community.ConcordEntryResidue
import com.vitorpamplona.quartz.concord.cord02Community.PrivateChannelKey
import com.vitorpamplona.quartz.concord.cord06Rekey.SteppedChannelKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** A Private Channel key held for an older channel epoch — it still reads its own era's history. */
class HistoricalChannelKey(
    val key: HexKey,
    val epoch: Long,
)

/**
 * The Private Channel keys a Community List entry holds, and how a rotation rewrites them
 * (CORD-03 §1-2, CORD-06 §2, CORD-02 §8).
 *
 *  - **Current keys** are the entry's `channels` (`privateChannels`): exactly one per channel, the
 *    newest epoch held. A rotation replaces it in place, keeping any unknown keys another client
 *    wrote inside the channel object (the round-trip rule, CORD-02 §6/§8).
 *  - **Older keys** are read from the entry's `seed` snapshot (the earliest epoch held, the backfill
 *    anchor) and from the reference client's `priors` extension inside a channel object, and a
 *    rotation writes the key it replaces into `priors` (`{key, epoch, retired_at}`, as Armada
 *    does). CORD-02 §8 would rather keep intermediate keys out of the List and re-walk rotations
 *    from `seed`, but a channel created or privatised after the join has no `seed` key to walk
 *    from: without `priors` its pre-rotation history was unreadable after the next restart.
 *  - **Cuts** are the reference client's `channel_cuts` extension on the entry: per channel, the
 *    channel epoch whose rotation cut this member out. A floor, never rolled back: a key below it is
 *    refused, so a stale bundle or catch-up cannot quietly restore revoked access. Kept as the raw
 *    extension (`[{ "id", "epoch" }]`) with every other field in each object preserved.
 */
object ConcordChannelKeyring {
    const val CHANNEL_CUTS = "channel_cuts"
    const val PRIORS = "priors"

    private val HEX64 = Regex("^[0-9a-fA-F]{64}$")

    /** The current key held for [channelIdHex], or null (a keyless listing is not a key). */
    fun heldKey(
        entry: ConcordCommunityListEntry,
        channelIdHex: HexKey,
    ): PrivateChannelKey? = entry.privateChannels.firstOrNull { it.channelId.equals(channelIdHex, ignoreCase = true) && HEX64.matches(it.key) }

    // ---- cuts ------------------------------------------------------------------

    /** The `channel_cuts` floors on [entry], channel id (lowercase) → cut epoch (max wins). */
    fun cutsOf(entry: ConcordCommunityListEntry): Map<HexKey, Long> {
        val raw = entry.residue.entryExtras[CHANNEL_CUTS] as? JsonArray ?: return emptyMap()
        val out = HashMap<HexKey, Long>()
        for (element in raw) {
            val obj = element as? JsonObject ?: continue
            val id = (obj["id"] as? JsonPrimitive)?.contentOrNull?.lowercase() ?: continue
            val epoch = (obj["epoch"] as? JsonPrimitive)?.longOrNull ?: continue
            if (epoch > (out[id] ?: Long.MIN_VALUE)) out[id] = epoch
        }
        return out
    }

    /** True when a key for [channelIdHex] at [epoch] sits below a recorded cut and must be refused. */
    fun isCutOff(
        entry: ConcordCommunityListEntry,
        channelIdHex: HexKey,
        epoch: Long,
    ): Boolean = cutsOf(entry)[channelIdHex.lowercase()]?.let { epoch < it } ?: false

    /**
     * [entry] with a cut for [channelIdHex] at [epoch] merged into `channel_cuts` (max wins — a
     * removal never rolls back). Other channels' cut objects, and unknown keys inside the replaced
     * one, ride through untouched.
     */
    fun withCut(
        entry: ConcordCommunityListEntry,
        channelIdHex: HexKey,
        epoch: Long,
    ): ConcordCommunityListEntry {
        val id = channelIdHex.lowercase()
        val existing = (entry.residue.entryExtras[CHANNEL_CUTS] as? JsonArray)?.toList() ?: emptyList()
        if ((cutsOf(entry)[id] ?: Long.MIN_VALUE) >= epoch) return entry
        val mine = existing.filter { (it as? JsonObject)?.let { o -> (o["id"] as? JsonPrimitive)?.contentOrNull?.lowercase() == id } == true }
        val base = (mine.firstOrNull() as? JsonObject) ?: JsonObject(emptyMap())
        val next = JsonObject(base + mapOf("id" to JsonPrimitive(id), "epoch" to JsonPrimitive(epoch)))
        val cuts = JsonArray(existing.filterNot { it in mine } + next)
        val extras = JsonObject(entry.residue.entryExtras + (CHANNEL_CUTS to cuts))
        return entry.copyChannels(entry.privateChannels, ConcordEntryResidue(extras, entry.residue.seed, entry.residue.currentExtras))
    }

    // ---- current keys ----------------------------------------------------------

    /**
     * [entry] holding [key] as the current key for its channel — replacing a lower epoch, keeping
     * the replaced object's unknown fields — or null when it would not move anything forward: a key
     * at or below the held epoch, or one below a recorded cut.
     */
    fun withChannelKey(
        entry: ConcordCommunityListEntry,
        key: PrivateChannelKey,
        retiredAt: Long = TimeUtils.now(),
        steppedOver: List<SteppedChannelKey> = emptyList(),
    ): ConcordCommunityListEntry? {
        if (!HEX64.matches(key.key) || !HEX64.matches(key.channelId)) return null
        if (isCutOff(entry, key.channelId, key.epoch)) return null
        val held = entry.privateChannels.firstOrNull { it.channelId.equals(key.channelId, ignoreCase = true) }
        if (held != null && HEX64.matches(held.key) && held.epoch >= key.epoch) return null
        val retired =
            // A walk's stepped keys carry when their rotation was published, so they win the dedupe
            // over the held key's local "now".
            buildList {
                steppedOver.forEach { add(Triple(it.key.toHexKey(), it.epoch, it.retiredAt)) }
                if (held != null && HEX64.matches(held.key)) add(Triple(held.key, held.epoch, retiredAt))
            }
        val next =
            PrivateChannelKey(
                channelId = key.channelId.lowercase(),
                key = key.key.lowercase(),
                epoch = key.epoch,
                name = key.name.ifBlank { held?.name ?: "" },
                extras = withPriors(held?.extras ?: key.extras, retired, key.key),
            )
        return entry.copyChannels(entry.privateChannels.filterNot { it.channelId.equals(key.channelId, ignoreCase = true) } + next, entry.residue)
    }

    /** [entry] with [channelIdHex]'s key moved to [newKeyHex] at [newEpoch] (a rotation it launched or adopted). */
    fun withRotatedKey(
        entry: ConcordCommunityListEntry,
        channelIdHex: HexKey,
        newKeyHex: HexKey,
        newEpoch: Long,
        retiredAt: Long = TimeUtils.now(),
        steppedOver: List<SteppedChannelKey> = emptyList(),
    ): ConcordCommunityListEntry? {
        val held = heldKey(entry, channelIdHex) ?: return null
        return withChannelKey(entry, PrivateChannelKey(held.channelId, newKeyHex, newEpoch, held.name, held.extras), retiredAt, steppedOver)
    }

    /**
     * [extras] with each [retired] `(key, epoch, retired_at)` appended to `priors`, skipping one
     * already there at that epoch and key, and never [currentKey] itself. Unknown fields inside
     * existing prior objects ride through.
     */
    private fun withPriors(
        extras: JsonObject,
        retired: List<Triple<HexKey, Long, Long>>,
        currentKey: HexKey,
    ): JsonObject {
        val existing = (extras[PRIORS] as? JsonArray)?.toList() ?: emptyList()
        val seen =
            existing.mapNotNullTo(HashSet()) { p ->
                val obj = p as? JsonObject ?: return@mapNotNullTo null
                val k = (obj["key"] as? JsonPrimitive)?.contentOrNull?.lowercase() ?: return@mapNotNullTo null
                val e = (obj["epoch"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNullTo null
                e to k
            }
        val added =
            retired.mapNotNull { (key, epoch, at) ->
                val k = key.lowercase()
                if (!HEX64.matches(k) || k == currentKey.lowercase() || !seen.add(epoch to k)) return@mapNotNull null
                JsonObject(mapOf("key" to JsonPrimitive(k), "epoch" to JsonPrimitive(epoch), "retired_at" to JsonPrimitive(at)))
            }
        if (added.isEmpty()) return extras
        return JsonObject(extras + (PRIORS to JsonArray(existing + added)))
    }

    /**
     * [entry] after a rotation to [cutEpoch] cut this member from [channelIdHex] (CORD-06 §2): the
     * key leaves `channels` and the cut is recorded, so no older key can come back.
     */
    fun withoutChannel(
        entry: ConcordCommunityListEntry,
        channelIdHex: HexKey,
        cutEpoch: Long,
    ): ConcordCommunityListEntry {
        val dropped = entry.copyChannels(entry.privateChannels.filterNot { it.channelId.equals(channelIdHex, ignoreCase = true) }, entry.residue)
        return withCut(dropped, channelIdHex, cutEpoch)
    }

    // ---- older keys --------------------------------------------------------------

    /**
     * Every older key this entry still carries for [channelIdHex] — the `seed` snapshot's and any
     * `priors` a peer wrote — excluding the current one, newest first. Each reads its own epoch's
     * history.
     */
    fun historicalKeys(
        entry: ConcordCommunityListEntry,
        channelIdHex: HexKey,
    ): List<HistoricalChannelKey> {
        val id = channelIdHex.lowercase()
        val current = heldKey(entry, id)
        val out = LinkedHashMap<Pair<Long, String>, HistoricalChannelKey>()

        fun add(
            key: String?,
            epoch: Long?,
        ) {
            if (key == null || epoch == null || !HEX64.matches(key)) return
            val k = key.lowercase()
            if (current != null && current.key.equals(k, ignoreCase = true) && current.epoch == epoch) return
            out.getOrPut(epoch to k) { HistoricalChannelKey(k, epoch) }
        }
        current?.extras?.get(PRIORS)?.let { priors ->
            for (p in (priors as? JsonArray).orEmpty()) {
                val obj = p as? JsonObject ?: continue
                add((obj["key"] as? JsonPrimitive)?.contentOrNull, (obj["epoch"] as? JsonPrimitive)?.longOrNull)
            }
        }
        val seedChannels = entry.residue.seed?.get("channels") as? JsonArray
        for (c in seedChannels.orEmpty()) {
            val obj = c as? JsonObject ?: continue
            if ((obj["id"] as? JsonPrimitive)?.contentOrNull?.lowercase() != id) continue
            add((obj["key"] as? JsonPrimitive)?.contentOrNull, (obj["epoch"] as? JsonPrimitive)?.longOrNull)
        }
        return out.values.sortedByDescending { it.epoch }
    }

    /**
     * The channel epoch a privatisation must mint at (CORD-03 §2): one past the highest generation
     * this entry knows of — the held key, any older key, a recorded cut — and [observedFloor] (the
     * highest rotation epoch seen on the wire, for a privatiser who never held earlier
     * generations). Monotonic, so a stale key is always a lower epoch; 1 for a never-private channel.
     */
    fun nextChannelEpoch(
        entry: ConcordCommunityListEntry,
        channelIdHex: HexKey,
        observedFloor: Long = 0,
    ): Long {
        val id = channelIdHex.lowercase()
        var highest = observedFloor
        entry.privateChannels.filter { it.channelId.equals(id, ignoreCase = true) }.forEach { highest = maxOf(highest, it.epoch) }
        historicalKeys(entry, id).forEach { highest = maxOf(highest, it.epoch) }
        cutsOf(entry)[id]?.let { highest = maxOf(highest, it) }
        return highest + 1
    }

    private fun ConcordCommunityListEntry.copyChannels(
        privateChannels: List<PrivateChannelKey>,
        residue: ConcordEntryResidue,
    ) = ConcordCommunityListEntry(
        id = id,
        owner = owner,
        ownerSalt = ownerSalt,
        root = root,
        rootEpoch = rootEpoch,
        controlPk = controlPk,
        controlRoot = controlRoot,
        heldRoots = heldRoots,
        privateChannels = privateChannels,
        relays = relays,
        name = name,
        addedAt = addedAt,
        inviteRef = inviteRef,
        excludedAtEpoch = excludedAtEpoch,
        residue = residue,
    )
}
