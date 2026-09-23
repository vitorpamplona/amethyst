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
package com.vitorpamplona.amethyst.cli.commands

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.quartz.cyberspace.CantorTree
import com.vitorpamplona.quartz.cyberspace.CyberspaceBagContents
import com.vitorpamplona.quartz.cyberspace.CyberspaceBagEvent
import com.vitorpamplona.quartz.cyberspace.CyberspaceCoordinate
import com.vitorpamplona.quartz.cyberspace.CyberspaceHint
import com.vitorpamplona.quartz.cyberspace.CyberspacePlane
import com.vitorpamplona.quartz.cyberspace.RegionKey
import com.vitorpamplona.quartz.cyberspace.RegionKeyMaterial
import com.vitorpamplona.quartz.cyberspace.RegionSweep
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.io.encoding.Base64

/**
 * `amy cyberspace …` — places and the keys they derive, local and accountless.
 *
 * Thin assembly only: §2's interleave, §4's Cantor trees and §7.2's derivation
 * all live in quartz's `cyberspace/`. These verbs exist so the Kotlin
 * implementation can be diffed against `cyberspace-cli` without a device, a
 * relay or an account — the same arrangement `amy sno` has, and for the same
 * reason. A region key is a consensus value: if ours differs from theirs by a
 * byte, a bag they hid is one we cannot open, and that is a difference worth
 * catching in a shell script rather than in a feed.
 */
object CyberspaceCommands {
    val USAGE: String =
        """
        |amy cyberspace — places and region keys (local, accountless)
        |
        |  cyberspace coord COORD_HEX             decode a coordinate: axes, plane, sectors (§2.2)
        |  cyberspace region COORD_HEX --height H the region key at that height (§7.2)
        |  cyberspace hint [EVENT|-]              read a bag's hint and price its sweep (§7.7)
        |  cyberspace open [EVENT|-] --key HEX    open a bag with a region key (§7.6)
        |  cyberspace sweep [EVENT|-]             sweep a bag's hint box for its key (§7.7)
        |
        |COORD_HEX is 32 bytes of lowercase hex, as a `C` or `hint` tag carries it.
        |
        |  --height H        the aligned subtree height, 0..20 (default 0)
        |  --max-height H    raise the refusal ceiling; the cost doubles and then
        |                    some with every height, so this is deliberate work
        |
        |open:
        |  --key HEX         the 32-byte region key (§7.2)
        |  --coord COORD_HEX derive the key from a coordinate instead of naming it
        |  --height H        the height to derive at; defaults to the bag's `h` tag
        |
        |sweep:
        |  --max-gap G       refuse a hint whose gap exceeds G bits (default 20).
        |                    The gap is the exponent: 2^G region keys to derive.
        |  --limit N         stop after N candidates, found or not
        |  --ids             print every candidate's lookup_id, not only the match
        """.trimMargin()

    suspend fun dispatch(tail: Array<String>): Int =
        route(
            "cyberspace",
            tail,
            "cyberspace <coord|region|hint|open|sweep>",
            mapOf(
                "coord" to { rest -> coord(rest) },
                "region" to { rest -> region(rest) },
                "hint" to { rest -> hint(rest) },
                "open" to { rest -> open(rest) },
                "sweep" to { rest -> sweep(rest) },
            ),
            USAGE,
        )

    /**
     * Decode a coordinate. The axes are reported as decimal strings because an
     * 85-bit value does not fit any JSON number a reader can trust.
     */
    private fun coord(rest: Array<String>): Int {
        val args = Args(rest)
        val hex = args.positional.firstOrNull() ?: return Output.error("bad_args", "cyberspace coord COORD_HEX")
        args.rejectUnknown()

        val point = CyberspaceCoordinate.decode(hex) ?: return Output.error("bad_args", "not a coordinate: 32 bytes of lowercase hex")

        Output.emit(
            mapOf(
                "coord" to hex,
                "plane" to if (point.plane == CyberspacePlane.DATASPACE) "dataspace" else "ideaspace",
                "x" to decimal(point.x.high, point.x.low),
                "y" to decimal(point.y.high, point.y.low),
                "z" to decimal(point.z.high, point.z.low),
                "sector" to point.sector(),
            ),
        )
        return 0
    }

    /**
     * The region key at a height: the same three fields `cyberspace-cli`'s
     * `derive_region_key_material_for_height` returns, minus `region_n`, which
     * is eleven kilobytes of hex by height 10 and is pinned exactly by the key
     * that hashes it.
     */
    private fun region(rest: Array<String>): Int {
        val args = Args(rest)
        val hex = args.positional.firstOrNull() ?: return Output.error("bad_args", "cyberspace region COORD_HEX --height H")
        val height = args.intFlag("height", 0)
        val maxHeight = args.intFlag("max-height", CantorTree.DEFAULT_MAX_COMPUTE_HEIGHT)
        args.rejectUnknown()

        val point = CyberspaceCoordinate.decode(hex) ?: return Output.error("bad_args", "not a coordinate: 32 bytes of lowercase hex")
        if (height < 0) return Output.error("bad_args", "--height must be >= 0")
        if (height > maxHeight) {
            // The same refusal both references make, and for the same reason:
            // one height further is twice the leaves and a root twice as wide.
            return Output.error("too_big", "height $height exceeds max-height $maxHeight")
        }

        val material = RegionKey.at(point, height, maxHeight)
        Output.emit(
            mapOf(
                "coord" to hex,
                "height" to height,
                "region_bits" to material.regionN.bitLength,
                "key" to material.decryptionKey.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') },
                "lookup_id" to material.lookupId,
            ),
        )
        return 0
    }

    /**
     * Read a bag's `hint` tag and price the search it describes (§7.7).
     *
     * A hint is the hider's difficulty knob, so the number that matters is the
     * gap: `(Hx - h) + (Hy - h) + (Hz - h)`, the exponent of the candidate
     * count. §7.7's own table reads in those terms — 12 is seconds, 24 is
     * hours, 30 or more is "days to never" — and a client that means to offer a
     * sweep has to know which of those it is offering *before* it starts.
     *
     * A malformed hint reports `hint: false` rather than an error, because
     * §7.7 says a bad hint is an absent one and "never invalidates the bag".
     */
    private fun hint(rest: Array<String>): Int {
        val args = Args(rest)
        val json = RawEventSupport.readArgOrStdin(args)
        args.rejectUnknown()

        val event =
            try {
                Event.fromJson(json)
            } catch (_: Exception) {
                return Output.error("bad_event", "not a nostr event")
            }

        // §8.6's `h` tag: the height of the region the content is keyed to. A
        // hint is read against it, since a box smaller than the region it
        // claims to hold is one of §7.7's malformed cases.
        val bagHeight =
            event.tags
                .firstOrNull { it.size > 1 && it[0] == "h" }
                ?.get(1)
                ?.toIntOrNull()
        val hint = CyberspaceHint.read(event.tags, bagHeight)

        if (hint == null) {
            Output.emit(mapOf("hint" to false, "height" to bagHeight))
            return 0
        }

        val height = bagHeight ?: 0
        Output.emit(
            mapOf(
                "hint" to true,
                "height" to bagHeight,
                "box" to CyberspaceCoordinate.encode(hint.base),
                "heights" to listOf(hint.heightX, hint.heightY, hint.heightZ),
                "gap_bits" to hint.gapBits(height),
                "candidates" to hint.candidates(height),
                "axis_trees" to hint.axisTrees(height),
                "destination" to hint.isDestination(height),
                "sector_tags" to hint.sectorTags().map { it.toList() },
            ),
        )
        return 0
    }

    /**
     * Open a bag with a region key (§7.6).
     *
     * The key can be named outright (`--key`) or derived here from a
     * coordinate (`--coord`), which is the shape that proves the whole chain in
     * one line: our §2.2 decode, our Cantor roots and our §7.2 derivation
     * against a ciphertext somebody else produced.
     *
     * **A bag that will not open exits 0.** §7.6: "A failed decryption
     * therefore means only that the reader does not hold this region's key; it
     * MUST NOT be treated as an error in the bag." So the wrong key is a
     * verdict — `opened: false` — the same way `sno validate` reports an
     * invalid payload rather than failing. What does fail is a bag this cannot
     * attempt at all: an unknown `version`, or no `aes-256-gcm` payload to try.
     */
    private fun open(rest: Array<String>): Int {
        val args = Args(rest)
        val json = RawEventSupport.readArgOrStdin(args)
        val keyHex = args.flag("key")
        val coordHex = args.flag("coord")
        val heightRaw = args.flag("height")
        val maxHeight = args.intFlag("max-height", CantorTree.DEFAULT_MAX_COMPUTE_HEIGHT)
        args.rejectUnknown()

        if ((keyHex == null) == (coordHex == null)) {
            return Output.error("bad_args", "give exactly one of --key HEX or --coord COORD_HEX")
        }

        val bag = bagOf(json) ?: return Output.error("bad_event", "not a kind ${CyberspaceBagEvent.KIND} bag")
        if (!bag.isKnownVersion()) {
            // §8.6: "A reader MUST ignore a bag whose version it does not know."
            return Output.error("unsupported_version", "not a version ${CyberspaceBagEvent.VERSION} bag (§8.6)")
        }
        if (bag.payload() == null) {
            return Output.error("bad_event", "no [\"encrypted\", \"${CyberspaceBagEvent.ALGORITHM}\", …] tag to open")
        }

        val height =
            if (heightRaw == null) {
                bag.height() ?: 0
            } else {
                heightRaw.toIntOrNull() ?: return Output.error("bad_args", "--height expects a number, got '$heightRaw'")
            }

        val key =
            if (keyHex != null) {
                keyHex.hexToByteArrayOrNull()?.takeIf { it.size == CyberspaceBagEvent.KEY_BYTES }
                    ?: return Output.error("bad_args", "--key must be ${CyberspaceBagEvent.KEY_BYTES} bytes of hex")
            } else {
                val point =
                    CyberspaceCoordinate.decode(coordHex!!)
                        ?: return Output.error("bad_args", "not a coordinate: 32 bytes of lowercase hex")
                if (height < 0) return Output.error("bad_args", "--height must be >= 0")
                if (height > maxHeight) return Output.error("too_big", "height $height exceeds max-height $maxHeight")
                RegionKey.at(point, height, maxHeight).decryptionKey
            }

        val contents = bag.open(key)
        if (contents == null) {
            Output.emit(mapOf("opened" to false, "height" to height, "lookup_id" to bag.lookupId()))
            return 0
        }

        val common = mapOf("opened" to true, "height" to height, "lookup_id" to bag.lookupId())
        when (contents) {
            is CyberspaceBagContents.Items ->
                Output.emit(
                    common +
                        mapOf(
                            "shape" to "items",
                            "dropped" to contents.dropped,
                            "items" to
                                contents.items.map { item ->
                                    mapOf(
                                        "kind" to item.event.kind,
                                        "id" to item.event.id,
                                        "pubkey" to item.event.pubKey,
                                        // §7.6: authorship only when this is true.
                                        "verified" to item.verified,
                                        "coord" to item.coordinate(),
                                        "event" to Output.mapper.readTree(item.event.toJson()),
                                    )
                                },
                        ),
                )

            is CyberspaceBagContents.Opaque ->
                Output.emit(
                    common +
                        mapOf(
                            "shape" to "opaque",
                            "bytes" to contents.bytes.size,
                            "base64" to Base64.encode(contents.bytes),
                            "text" to asText(contents.bytes),
                        ),
                )
        }
        return 0
    }

    /**
     * Sweep the box a bag's hint names until its own `lookup_id` comes up
     * (§7.7) — the position-free search, priced before it starts.
     *
     * The price is the point. A hint is a stranger's choice of difficulty, and
     * the gap it declares is the exponent of the work: 2^gap region keys. So
     * the gap is read and checked against `--max-gap` before the first tree is
     * built, and a hint asking for more says so and stops rather than running
     * for a day. Raising the ceiling is how a caller spends it on purpose.
     */
    private fun sweep(rest: Array<String>): Int {
        val args = Args(rest)
        val json = RawEventSupport.readArgOrStdin(args)
        val maxGap = args.intFlag("max-gap", DEFAULT_MAX_GAP)
        val limit = args.longFlag("limit", Long.MAX_VALUE)
        val wantIds = args.bool("ids")
        val maxHeight = args.intFlag("max-height", CantorTree.DEFAULT_MAX_COMPUTE_HEIGHT)
        args.rejectUnknown()

        val bag = bagOf(json) ?: return Output.error("bad_event", "not a kind ${CyberspaceBagEvent.KIND} bag")
        val height = bag.height() ?: return Output.error("no_hint", "a sweep needs the bag's `h` tag (§8.6)")
        val hint = bag.hint() ?: return Output.error("no_hint", "no usable `hint` tag to sweep (§7.7)")

        val gap = hint.gapBits(height)
        if (gap > maxGap) {
            return Output.error(
                "too_big",
                "a gap of $gap bits is 2^$gap region keys; pass --max-gap $gap to spend it",
                extra = mapOf("gap_bits" to gap, "max_gap" to maxGap),
            )
        }
        if (limit < 1) return Output.error("bad_args", "--limit must be >= 1")

        // What the hider published as the address, and therefore the only thing
        // a sweep can recognise when it walks past the right region.
        val target = bag.lookupId()
        val ids = if (wantIds) mutableListOf<String>() else null

        var examined = 0L
        var exhausted = true
        var found: RegionKeyMaterial? = null
        val candidates =
            try {
                RegionSweep.of(hint, height, maxHeight)
            } catch (e: IllegalArgumentException) {
                return Output.error("too_big", e.message)
            }

        for (material in candidates) {
            examined++
            ids?.add(material.lookupId)
            if (target != null && material.lookupId == target) {
                found = material
                exhausted = false
                break
            }
            if (examined >= limit) {
                exhausted = false
                break
            }
        }

        Output.emit(
            mapOf(
                "height" to height,
                "gap_bits" to gap,
                "candidates" to hint.candidates(height),
                "axis_trees" to hint.axisTrees(height),
                "target" to target,
                "examined" to examined,
                "exhausted" to exhausted,
                "found" to (found != null),
                "key" to found?.decryptionKey?.toHexKey(),
                "lookup_id" to found?.lookupId,
                "lookup_ids" to ids,
            ),
        )
        return 0
    }

    /**
     * Read a kind-33330 bag out of raw JSON.
     *
     * [Event.fromJson] already answers with a [CyberspaceBagEvent] for a
     * registered kind; the rebuild is for the caller that hands us an event
     * from a factory that did not, so the verb behaves the same either way.
     */
    private fun bagOf(json: String): CyberspaceBagEvent? {
        val event =
            try {
                Event.fromJson(json)
            } catch (_: Exception) {
                return null
            }
        if (event is CyberspaceBagEvent) return event
        if (event.kind != CyberspaceBagEvent.KIND) return null
        return CyberspaceBagEvent(event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig)
    }

    /**
     * These bytes as text, or null when they are not UTF-8 — §7.6's opaque
     * shape is "a text note or a file", and a file should not be printed as a
     * field of replacement characters.
     */
    private fun asText(bytes: ByteArray): String? {
        val text = bytes.decodeToString()
        return if (text.encodeToByteArray().contentEquals(bytes)) text else null
    }

    /**
     * The gap a sweep spends without being told to.
     *
     * 2^20 is about a million region keys — a minute or so of a laptop, and the
     * scale §7.7's own table calls a reasonable search. Everything past it is
     * the caller's decision to make in the command line, because §7.7's larger
     * boxes run from "hours" to "days to never" and nothing about a bag tells
     * you which one a stranger meant.
     */
    private const val DEFAULT_MAX_GAP = 20

    /** An 85-bit axis as decimal, from its two halves, without a big integer. */
    private fun decimal(
        high: Long,
        low: Long,
    ): String {
        // high * 2^64 + low, with low unsigned. Done in base 10^9 chunks so the
        // CLI does not need arbitrary precision for a number it only prints.
        var result = "0"
        for (bit in 84 downTo 0) {
            result = addDecimal(result, result)
            val set = if (bit >= 64) (high ushr (bit - 64)) and 1L else (low ushr bit) and 1L
            if (set == 1L) result = addDecimal(result, "1")
        }
        return result
    }

    private fun addDecimal(
        a: String,
        b: String,
    ): String {
        val out = StringBuilder()
        var carry = 0
        var i = a.length - 1
        var j = b.length - 1
        while (i >= 0 || j >= 0 || carry > 0) {
            val sum = (if (i >= 0) a[i--] - '0' else 0) + (if (j >= 0) b[j--] - '0' else 0) + carry
            out.append(('0' + sum % 10))
            carry = sum / 10
        }
        return out.reverse().toString()
    }
}
