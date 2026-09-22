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
package com.vitorpamplona.quartz.cyberspace.deck0003Sno

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.cyberspace.CyberspaceCoordinate
import com.vitorpamplona.quartz.cyberspace.CyberspacePlane
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * DECK-0003 §3.2 — an object hidden at a place: a `kind 3330` bag item, whose
 * `content` is the payload of §1 exactly as a `kind 33331` object carries it.
 *
 * Regular, and deliberately so. An item in a bag is a thing someone hid at a
 * place and someone else found there; it must be exactly what it was when it
 * was found, and its id must keep meaning what it meant. The same payload
 * therefore travels under two kinds according to what is being done with it:
 * 33331 for an object its author is still working on, 3330 for one that has
 * been put somewhere. That is two containers for one format, not two ways of
 * writing the format — which is why this class adds a container and a
 * coordinate and delegates every rule to [SnoParser].
 *
 * **Most shards are not reachable, and that is by design.** A shard is an item
 * inside a `kind 33330` bag (`CYBERSPACE_V2.md` §7.6) whose payload is
 * AES-256-GCM ciphertext keyed to the region it was hidden in, so finding one
 * means deriving that region's key: the coordinate system of §2, the Cantor
 * trees of §4, the derivation of §7.2 and a sweep of the hinted box of §7.7.
 *
 * Not for want of somewhere to stand — §7.7 is explicit that "the seeker's own
 * position never enters this cost, because §7.1 makes looking and walking
 * equivalent", so a client with no avatar could open a hinted bag. What stops
 * it is the work: a key is three `O(2^h)` folds of BigInts that double in width
 * every level, which the spec measures at 816 ms per key at height 16 on a
 * desktop core and which grows about 2.2x per height above that. Amethyst
 * implements none of it and a reader without the key sees base64 and nothing
 * else — §7.6 is explicit that a failed decryption "MUST NOT be treated as an
 * error in the bag". What this class reads is a shard handed over directly:
 * quoted in a note, or fetched by id.
 *
 * **An item MAY be unsigned** (§7.6, and §6 of the deck), in which case its
 * `pubkey` is a claim and a client MUST NOT present it as verified authorship.
 * Placement is attributable to the bag's author; authorship of an item's
 * content only to the item's own pubkey, and only when the item is signed.
 */
@Immutable
class SnoShardEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    /**
     * True when this shard carries no payload of its own.
     *
     * Almost every 3330 that reaches a client this way is one: a shard's
     * geometry travels inside its bag's ciphertext, and what is left on a relay
     * is the item without it. §7.6 is explicit that not being able to read a
     * bag "MUST NOT be treated as an error in the bag", so this is a thing to
     * be quiet about rather than a malformed payload to complain of.
     */
    fun isSealed(): Boolean = content.isBlank()

    fun shard(fetchedPalette: SnoPalette? = null): SnoResult = SnoParser.parse(content, fetchedPalette)

    fun shardOrNull(fetchedPalette: SnoPalette? = null): SnoPayload? = shard(fetchedPalette).payloadOrNull()

    /**
     * The exact cyberspace coordinate this shard claims, if it carries one.
     *
     * §7.6: an item MAY carry a `C` tag, which lets a client render it at a
     * point rather than somewhere in the region. Where a shard came out of a
     * bag that coordinate MUST lie inside the region the bag is encrypted to;
     * this class has no bag to check it against, so it only reports the claim.
     */
    fun coordinate(): String? = tags.firstOrNull { it.size > 1 && it[0] == "C" }?.get(1)

    /**
     * The plane the claimed coordinate lies in, or null when there is no `C`
     * tag or it is not a coordinate.
     *
     * One bit of §2.2, and the only part of a coordinate a client with no world
     * can turn into something a reader understands: whether the shard was
     * hidden at a place on Earth or at one that has no physical counterpart.
     * See [CyberspaceCoordinate] for why it stops there.
     */
    fun plane(): CyberspacePlane? = coordinate()?.let { CyberspaceCoordinate.planeOf(it) }

    companion object {
        const val KIND = 3330
    }
}
