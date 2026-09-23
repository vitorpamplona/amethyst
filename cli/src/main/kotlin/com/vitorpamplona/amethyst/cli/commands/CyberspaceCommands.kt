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
import com.vitorpamplona.quartz.cyberspace.CyberspaceCoordinate
import com.vitorpamplona.quartz.cyberspace.CyberspaceHint
import com.vitorpamplona.quartz.cyberspace.CyberspacePlane
import com.vitorpamplona.quartz.cyberspace.RegionKey
import com.vitorpamplona.quartz.nip01Core.core.Event

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
        |
        |COORD_HEX is 32 bytes of lowercase hex, as a `C` or `hint` tag carries it.
        |
        |  --height H        the aligned subtree height, 0..20 (default 0)
        |  --max-height H    raise the refusal ceiling; the cost doubles and then
        |                    some with every height, so this is deliberate work
        """.trimMargin()

    suspend fun dispatch(tail: Array<String>): Int =
        route(
            "cyberspace",
            tail,
            "cyberspace <coord|region|hint>",
            mapOf(
                "coord" to { rest -> coord(rest) },
                "region" to { rest -> region(rest) },
                "hint" to { rest -> hint(rest) },
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
