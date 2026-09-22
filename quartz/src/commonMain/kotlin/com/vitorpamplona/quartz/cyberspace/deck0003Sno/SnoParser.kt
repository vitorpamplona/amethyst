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
import com.vitorpamplona.quartz.nip01Core.kotlinSerialization.KotlinSerializationMapper
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.math.roundToInt

/** What [SnoParser] made of a payload. */
@Immutable
sealed class SnoResult {
    @Immutable
    data class Valid(
        val payload: SnoPayload,
    ) : SnoResult()

    /**
     * The payload was refused. [rule] names the numbered rule of DECK-0003 §1.9
     * that failed, so a client can say why rather than failing silently, which
     * §3.1 asks it to do.
     */
    @Immutable
    data class Invalid(
        val rule: String,
        val reason: String,
    ) : SnoResult()

    fun payloadOrNull(): SnoPayload? = (this as? Valid)?.payload
}

/**
 * Reads a Simple Nostr Object out of an event's content (DECK-0003 §1).
 *
 * §1.9 is enforced in full and in its own order: a payload that fails any rule
 * is refused whole, because "a partially valid object is not rendered
 * partially: a face index pointing past the end of the vertex list is not a
 * defect that degrades gracefully". Counts are checked against the arrays
 * themselves and buffers are sized from those counts, never from a number the
 * payload supplies — the format has no length fields for exactly this reason
 * (§6).
 *
 * Written against the deck rather than ported, and checked against both
 * reference implementations: `decks/sno-reference.py` in the cyberspace
 * repository, which carries a rejection case per rule, and `sno-core`, the MIT
 * TypeScript library ONOSENDAI and the snocrash workshop both read with. Where
 * the two disagree, see [readLiteralTriple].
 */
object SnoParser {
    /**
     * @param fetchedPalette the palette an [SnoPaletteRef.Event] resolved to, if
     *  one has been fetched since. Absent, a reference reads as the built-in,
     *  which §1.3b requires: a reader MUST render without waiting for anything.
     */
    fun parse(
        content: String,
        fetchedPalette: SnoPalette? = null,
    ): SnoResult {
        val root =
            try {
                KotlinSerializationMapper.json.parseToJsonElement(content) as? JsonObject
            } catch (_: Exception) {
                null
            } ?: return SnoResult.Invalid("json", "content is not a JSON object")

        return parse(root, fetchedPalette)
    }

    fun parse(
        root: JsonObject,
        fetchedPalette: SnoPalette? = null,
    ): SnoResult {
        // Rule 1. `v` is 1 or 2. A `type` field, if present, is ignored and never
        // rejected on (§1.1a): the field never said anything the kind did not,
        // and rejecting on noise would refuse every object already published.
        val version = root["v"].asIntOrNull()
        if (version != 1 && version != 2) return SnoResult.Invalid("1", "v is not 1 or 2")

        // Rule 2. The three required arrays, and vertices parallel to colors.
        val vertices = root["vertices"] as? JsonArray ?: return SnoResult.Invalid("2", "vertices is not an array")
        val colors = root["colors"] as? JsonArray ?: return SnoResult.Invalid("2", "colors is not an array")
        val faces = root["faces"] as? JsonArray ?: return SnoResult.Invalid("2", "faces is not an array")
        if (vertices.size != colors.size) return SnoResult.Invalid("2", "vertices and colors differ in length")

        // Rule 3. The limits, enforced before anything is allocated from them (§6).
        if (vertices.size > SnoPayload.MAX_VERTICES) return SnoResult.Invalid("3", "more than ${SnoPayload.MAX_VERTICES} vertices")
        if (faces.size > SnoPayload.MAX_FACES) return SnoResult.Invalid("3", "more than ${SnoPayload.MAX_FACES} faces")

        // Rule 4.
        val mode = SnoMode.parseOrNull(root["mode"].asStringOrNull()) ?: return SnoResult.Invalid("4", "mode is not solid, points or lines")

        // Rule 5.
        val unit = root["unit"].asIntOrNull() ?: return SnoResult.Invalid("5", "unit is not an integer")
        if (unit < 0 || unit > SnoPayload.MAX_UNIT) return SnoResult.Invalid("5", "unit is outside 0..${SnoPayload.MAX_UNIT}")

        // Rule 7, before rule 6, because a position needs both halves to exist
        // before it can be assembled. Absent ticks means every position is whole.
        val ticks = expandTicks(root["ticks"], vertices.size) ?: return SnoResult.Invalid("7", "ticks do not expand to one remainder per vertex")

        // Rule 6. §1.8 puts the position bound on publishers — "a publisher MUST
        // NOT write a vertex further than 64 model units from the origin" — and
        // lets a reader either reject such a payload or "repair it by growing
        // the extent". Both references repair: `sno-core`'s `fromPayload` does
        // not check a position at all and lets `neededExtent` grow past its own
        // `MAX_EXTENT`, and `sno-reference.py` does not check either. So this
        // repairs too, under rule 9 below, and the only bound left here is the
        // one the lattice itself imposes (see MAX_TICKS_FROM_ORIGIN).
        val positions = IntArray(vertices.size * 3)
        for (i in 0 until vertices.size) {
            val triple = vertices[i] as? JsonArray ?: return SnoResult.Invalid("6", "vertex $i is not an array")
            if (triple.size != 3) return SnoResult.Invalid("6", "vertex $i is not three integers")
            for (axis in 0..2) {
                // Read and multiplied as a Long, because the product is what has
                // to fit: a whole of Int.MIN_VALUE would otherwise overflow `*
                // 120` to 0 and parse as a silently rewritten coordinate, and
                // `abs` would not catch it either, being its own negative there.
                val whole = triple[axis].asLongOrNull() ?: return SnoResult.Invalid("6", "vertex $i is not three integers")
                val total = whole * SnoPayload.TICKS_PER_UNIT + ticks[i * 3 + axis]
                if (total > SnoPayload.MAX_TICKS_FROM_ORIGIN || total < -SnoPayload.MAX_TICKS_FROM_ORIGIN) {
                    return SnoResult.Invalid("bound", "vertex $i lies past what the lattice can hold exactly")
                }
                // §2: a `v: 1` payload has +Z away from the viewer, so its Z is
                // negated on read, which renders the object exactly as its author
                // built it. A `v: 2` payload is read as written.
                positions[i * 3 + axis] = (if (axis == 2 && version == 1) -total else total).toInt()
            }
        }

        // Rule 8.
        val faceIndices = IntArray(faces.size * 3)
        for (i in 0 until faces.size) {
            val triple = faces[i] as? JsonArray ?: return SnoResult.Invalid("8", "face $i is not an array")
            if (triple.size != 3) return SnoResult.Invalid("8", "face $i is not three indices")
            val a = triple[0].asIntOrNull() ?: return SnoResult.Invalid("8", "face $i has a non-integer index")
            val b = triple[1].asIntOrNull() ?: return SnoResult.Invalid("8", "face $i has a non-integer index")
            val c = triple[2].asIntOrNull() ?: return SnoResult.Invalid("8", "face $i has a non-integer index")
            if (a < 0 || b < 0 || c < 0 || a >= vertices.size || b >= vertices.size || c >= vertices.size) {
                return SnoResult.Invalid("8", "face $i points at a vertex that does not exist")
            }
            if (a == b || b == c || a == c) return SnoResult.Invalid("8", "face $i does not have three distinct vertices")
            faceIndices[i * 3] = a
            faceIndices[i * 3 + 1] = b
            faceIndices[i * 3 + 2] = c
        }

        // Rule 8a. An unresolved reference counts as the built-in and is never a
        // reason to reject (§1.3b).
        val paletteRef = readPaletteRef(root["palette"]) ?: return SnoResult.Invalid("8a", "palette is not a known name, a well-formed nevent/naddr, or 2..256 RGB entries")
        val palette =
            when (paletteRef) {
                is SnoPaletteRef.Inline -> paletteRef.palette
                is SnoPaletteRef.Event -> fetchedPalette ?: SnoPalette.BUILT_IN
                SnoPaletteRef.BuiltIn -> SnoPalette.BUILT_IN
            }

        // Rule 8b.
        val vertexColors = IntArray(colors.size)
        for (i in 0 until colors.size) {
            vertexColors[i] = readColor(colors[i], version, palette) ?: return SnoResult.Invalid("8b", "colors[$i] is not a valid colour for a v$version payload")
        }

        // Rule 8c.
        val faceColorsField = root["facecolors"]
        val faceColors =
            if (faceColorsField == null) {
                null
            } else {
                val expanded = expandFaceColors(faceColorsField, faces.size, palette.size) ?: return SnoResult.Invalid("8c", "facecolors do not expand to one valid index per face")
                IntArray(expanded.size) { palette[expanded[it]] }
            }

        // Rule 10.
        val upField = root["up"]
        if (upField != null && upField.asBooleanOrNull() == null) return SnoResult.Invalid("10", "up is not a boolean")
        val up = upField?.asBooleanOrNull() == true
        val spinField = root["spin"]
        // Validated whether or not `up` is present, because a payload carrying a
        // nonsense bearing is malformed even where the bearing is unused (§1.7).
        if (spinField != null) {
            val spin = spinField.asIntOrNull()
            if (spin == null || spin < 0 || spin > 359) return SnoResult.Invalid("10", "spin is not an integer in 0..359")
        }

        // Rule 9. `extent` is repaired rather than validated: out of range becomes
        // the default, then it grows to contain the data. The data wins and the
        // hint is corrected, so an object is never refused for disagreeing with
        // its own extent (§1.8) — and, since rule 6 no longer turns away a
        // vertex past 64 units, this is also where such a vertex is repaired.
        // The grown extent may therefore exceed MAX_EXTENT, exactly as the
        // reference's `neededExtent` does; MAX_EXTENT bounds what a payload may
        // *declare*, not what its geometry may need.
        val declared = root["extent"].asIntOrNull()
        val repaired = if (declared != null && declared >= SnoPayload.MIN_EXTENT && declared <= SnoPayload.MAX_EXTENT) declared else SnoPayload.DEFAULT_EXTENT
        val extent = grownExtent(repaired, positions)

        return SnoResult.Valid(
            SnoPayload(
                version = version,
                name = root["name"].asStringOrNull()?.take(SnoPayload.MAX_NAME) ?: "",
                unit = unit,
                extent = extent,
                mode = mode,
                positions = positions,
                colors = vertexColors,
                faces = faceIndices,
                faceColors = faceColors,
                paletteRef = paletteRef,
                up = up,
                // §1.7: a reader MUST ignore the value of `spin` when `up` is not true.
                spin = if (up) spinField?.asIntOrNull() ?: 0 else 0,
            ),
        )
    }

    /**
     * The grid half-width that holds every vertex, in model units (§1.8).
     * Rounded away from zero, so a vertex at 8 units and one tick needs 9.
     */
    private fun grownExtent(
        declared: Int,
        positions: IntArray,
    ): Int {
        var needed = declared
        for (tick in positions) {
            val magnitude = if (tick < 0) -tick.toLong() else tick.toLong()
            val units = ((magnitude + SnoPayload.TICKS_PER_UNIT - 1) / SnoPayload.TICKS_PER_UNIT).toInt()
            if (units > needed) needed = units
        }
        return needed
    }

    /**
     * The sub-unit part of every position, run-length decoded (§1.2).
     *
     * An entry is either a triple, standing for one vertex, or a negative
     * integer `-N`, standing for N consecutive vertices whose remainder is
     * `[0, 0, 0]`. Zero and positive integers are not valid entries, and the
     * entries must expand to exactly one remainder per vertex. Absent means
     * every position is whole.
     */
    private fun expandTicks(
        field: JsonElement?,
        vertexCount: Int,
    ): IntArray? {
        val out = IntArray(vertexCount * 3)
        if (field == null) return out

        val entries = field as? JsonArray ?: return null
        var written = 0
        for (entry in entries) {
            when (entry) {
                is JsonArray -> {
                    if (entry.size != 3) return null
                    if (written >= vertexCount) return null
                    for (axis in 0..2) {
                        val v = entry[axis].asIntOrNull() ?: return null
                        if (v < 0 || v >= SnoPayload.TICKS_PER_UNIT) return null
                        out[written * 3 + axis] = v
                    }
                    written++
                }
                is JsonPrimitive -> {
                    val run = entry.asIntOrNull() ?: return null
                    // Bounded before it is negated. `-Int.MIN_VALUE` is itself
                    // negative, which would drive `written` below zero and index
                    // out of the buffer on the next triple; and a run longer than
                    // the vertex list is invalid anyway, so one test does both.
                    if (run >= 0 || run < -vertexCount) return null
                    val count = -run
                    if (written + count > vertexCount) return null
                    written += count
                }
                else -> return null
            }
        }
        return if (written == vertexCount) out else null
    }

    /**
     * One palette index per face, run-length decoded (§1.4a).
     *
     * An entry is either a palette index, read exactly as §1.3 reads one, or a
     * negative integer `-N` standing for N further faces of the index before
     * it. The first entry must be an index, since a run has nothing to repeat
     * before one, and the sign is what separates the two kinds of entry —
     * which works because an index is never negative.
     */
    private fun expandFaceColors(
        field: JsonElement,
        faceCount: Int,
        paletteSize: Int,
    ): IntArray? {
        val entries = field as? JsonArray ?: return null
        val out = IntArray(faceCount)
        var written = 0
        var previous = -1
        for (entry in entries) {
            val value = (entry as? JsonPrimitive).asIntOrNull() ?: return null
            if (value < 0) {
                if (previous < 0) return null
                // Bounded before it is negated, as in expandTicks: `-Int.MIN_VALUE`
                // is negative, so an unguarded run would be swallowed rather than
                // refused. A run longer than the face list is invalid regardless.
                if (value < -faceCount) return null
                val count = -value
                if (written + count > faceCount) return null
                repeat(count) { out[written++] = previous }
            } else {
                if (value >= paletteSize) return null
                if (written >= faceCount) return null
                out[written++] = value
                previous = value
            }
        }
        return if (written == faceCount) out else null
    }

    /** §1.3a, with rule 8a's shapes. Null means the payload is refused. */
    private fun readPaletteRef(field: JsonElement?): SnoPaletteRef? {
        if (field == null) return SnoPaletteRef.BuiltIn

        val name = field.asStringOrNull()
        if (name != null) {
            if (name == SnoBuiltInPalette.NAME) return SnoPaletteRef.BuiltIn
            val entity = Nip19Parser.uriToRoute(name)?.entity
            return if (entity is NEvent || entity is NAddress) SnoPaletteRef.Event(name) else null
        }

        val entries = field as? JsonArray ?: return null
        if (entries.size < SnoPalette.MIN_ENTRIES || entries.size > SnoPalette.MAX_ENTRIES) return null
        val colors = IntArray(entries.size)
        for (i in 0 until entries.size) {
            val rgb = entries[i] as? JsonArray ?: return null
            if (rgb.size != 3) return null
            val r = rgb[0].asIntOrNull() ?: return null
            val g = rgb[1].asIntOrNull() ?: return null
            val b = rgb[2].asIntOrNull() ?: return null
            if (r !in 0..255 || g !in 0..255 || b !in 0..255) return null
            colors[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return SnoPaletteRef.Inline(SnoPalette(colors))
    }

    private fun readColor(
        field: JsonElement,
        version: Int,
        palette: SnoPalette,
    ): Int? {
        if (field is JsonArray) return readLiteralTriple(field)

        // An index is version 2's form, and version 1 never had one.
        if (version == 1) return null
        val index = field.asIntOrNull() ?: return null
        if (index < 0 || index >= palette.size) return null
        return palette[index]
    }

    /**
     * A literal `[r, g, b]` of numbers from 0 to 1, clamped on read and taken
     * exactly as written — never snapped to the nearest palette entry, because
     * snapping on read would change objects nobody asked to change (§1.3).
     *
     * This is version 1's colour, and it is **also accepted in version 2**,
     * which is the one place this reader is knowingly more forgiving than
     * `sno-reference.py`. Version 2 arrived as two changes that did not ship
     * together: the wire turned right-handed first, and colour became a palette
     * index two releases later. Objects written in between declare `v: 2`,
     * carry triples, and are correct in every other respect including their
     * frame. §5 permits a reader to be generous with them, `sno-core` was
     * changed to accept them because refusing orphaned real work, and three of
     * the seven objects on the network are of exactly this shape — so a strict
     * reader shows an error for nearly half of what exists.
     *
     * The two forms cannot be confused, because one is an array and the other
     * an integer. Note that the frame still follows the declared version: one
     * of these is in the v2 frame and flipping it as though it were v1 would
     * mirror the object.
     */
    private fun readLiteralTriple(triple: JsonArray): Int? {
        if (triple.size != 3) return null
        val r = channel(triple[0]) ?: return null
        val g = channel(triple[1]) ?: return null
        val b = channel(triple[2]) ?: return null
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun channel(field: JsonElement): Int? {
        val raw = (field as? JsonPrimitive)?.let { if (it.isString) null else it.doubleOrNull } ?: return null
        val clamped =
            if (raw < 0.0) {
                0.0
            } else if (raw > 1.0) {
                1.0
            } else {
                raw
            }
        return (clamped * 255.0).roundToInt()
    }

    /**
     * A JSON integer as a Long, or null when the token is not one — including a
     * whole number too large for a Long, which is not a coordinate anybody can
     * hold and is rejected the same way a string would be.
     */
    private fun JsonElement?.asLongOrNull(): Long? {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive.isString) return null
        return primitive.longOrNull
    }

    private fun JsonElement?.asIntOrNull(): Int? {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive.isString) return null
        return primitive.intOrNull
    }

    private fun JsonElement?.asStringOrNull(): String? {
        val primitive = this as? JsonPrimitive ?: return null
        return if (primitive.isString) primitive.content else null
    }

    private fun JsonElement?.asBooleanOrNull(): Boolean? {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive.isString) return null
        return primitive.booleanOrNull
    }
}
