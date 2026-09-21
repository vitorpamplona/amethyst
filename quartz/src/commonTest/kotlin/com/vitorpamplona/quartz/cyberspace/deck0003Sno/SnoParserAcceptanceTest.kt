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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What DECK-0003 says a reader must *accept*, which the rejection table cannot
 * cover: the worked example, the exactness the lattice promises, the two places
 * the format repairs rather than refuses, and the run-length encodings.
 */
class SnoParserAcceptanceTest {
    private val appendixA =
        """
        {"v":2,"name":"tetra","unit":0,"extent":8,"mode":"solid",
         "vertices":[[0,0,0],[2,0,0],[1,0,2],[1,2,1]],
         "ticks":[-4],
         "colors":[238,235,239,225],
         "faces":[[0,1,2],[0,1,3],[1,2,3],[0,2,3]]}
        """.trimIndent()

    @Test
    fun appendixAReads() {
        val payload = SnoParser.parse(appendixA).payloadOrNull()
        assertNotNull(payload)
        assertEquals("tetra", payload.name)
        assertEquals(4, payload.vertexCount)
        assertEquals(4, payload.faceCount)
        assertEquals(SnoMode.SOLID, payload.mode)
        assertEquals(0, payload.unit)
        assertEquals(8, payload.extent)

        // The built-in palette names these: pure red, green, blue, white.
        assertEquals(0xFFFF0000.toInt(), payload.colors[0])
        assertEquals(0xFF00FF00.toInt(), payload.colors[1])
        assertEquals(0xFF0000FF.toInt(), payload.colors[2])
        assertEquals(0xFFFFFFFF.toInt(), payload.colors[3])

        // The fourth vertex is at (1, 2, 1) whole units, exactly.
        assertEquals(120, payload.tickAt(3, 0))
        assertEquals(240, payload.tickAt(3, 1))
        assertEquals(120, payload.tickAt(3, 2))
    }

    @Test
    fun fortyTicksIsExactlyOneThirdOfAUnit() {
        // The whole reason positions are integers: a third of a unit lands on the
        // lattice and stays there. Three of them make exactly one unit, which is
        // a thing no float format can promise.
        val payload = SnoParser.parse(payload(vertices = "[[0,0,0],[2,0,0],[1,0,2],[1,2,1]]", ticks = ""","ticks":[[40,0,0],[40,0,0],[40,0,0],-1]""")).payloadOrNull()
        assertNotNull(payload)
        assertEquals(40, payload.tickAt(0, 0))
        assertEquals(SnoPayload.TICKS_PER_UNIT, payload.tickAt(0, 0) * 3)
    }

    @Test
    fun absentTicksMeansEveryPositionIsWhole() {
        val withRun = SnoParser.parse(appendixA).payloadOrNull()!!
        val without = SnoParser.parse(payload(ticks = "")).payloadOrNull()!!
        assertTrue(withRun.positions.contentEquals(without.positions), "[-4] and an absent ticks array must mean the same thing")
    }

    @Test
    fun extentIsRepairedAndThenGrown() {
        // Out of range becomes the default of 8...
        assertEquals(8, SnoParser.parse(payload(extent = ""","extent":0""")).payloadOrNull()!!.extent)
        assertEquals(8, SnoParser.parse(payload(extent = ""","extent":999""")).payloadOrNull()!!.extent)
        assertEquals(8, SnoParser.parse(payload(extent = "")).payloadOrNull()!!.extent)

        // ...and then grows until it contains every vertex. The data wins and the
        // hint is corrected; the object is never refused for disagreeing with it.
        val wide = SnoParser.parse(payload(vertices = "[[0,0,0],[2,0,0],[1,0,2],[9,2,1]]", extent = ""","extent":8""")).payloadOrNull()
        assertNotNull(wide)
        assertEquals(9, wide.extent)
    }

    @Test
    fun aVertexPastOneUnitGrowsTheExtentToTheNextWholeUnit() {
        val justOver = SnoParser.parse(payload(vertices = "[[0,0,0],[2,0,0],[1,0,2],[8,2,1]]", ticks = ""","ticks":[-3,[1,0,0]]""")).payloadOrNull()
        assertNotNull(justOver)
        assertEquals(9, justOver.extent, "8 units and one tick needs a grid of 9")
    }

    @Test
    fun faceColoursRunLengthDecode() {
        val flat = SnoParser.parse(payload(extra = ""","facecolors":[238,-3]""")).payloadOrNull()
        assertNotNull(flat)
        val faceColors = flat.faceColors
        assertNotNull(faceColors)
        assertEquals(4, faceColors.size)
        faceColors.forEach { assertEquals(0xFFFF0000.toInt(), it) }
    }

    @Test
    fun absentFaceColoursIsNotColourZero() {
        assertNull(SnoParser.parse(appendixA).payloadOrNull()!!.faceColors)
    }

    @Test
    fun vertexColoursSurviveFaceColours() {
        // §1.4a: vertex colours are untouched and still colour the points and the
        // lines, so an object may carry both without contradiction.
        val both = SnoParser.parse(payload(extra = ""","facecolors":[238,-3]""")).payloadOrNull()!!
        assertEquals(0xFF00FF00.toInt(), both.colors[1])
    }

    @Test
    fun versionOneNegatesZAndVersionTwoDoesNot() {
        val v2 = SnoParser.parse(payload(version = 2)).payloadOrNull()!!
        val v1 = SnoParser.parse(payload(version = 1, colors = """[[1,0,0],[0,1,0],[0,0,1],[1,1,1]]""")).payloadOrNull()!!

        // Vertex 2 is at z = 2 units as written.
        assertEquals(240, v2.tickAt(2, 2))
        assertEquals(-240, v1.tickAt(2, 2))
        // X and Y are untouched by the flip.
        assertEquals(v2.tickAt(2, 0), v1.tickAt(2, 0))
        assertEquals(v2.tickAt(2, 1), v1.tickAt(2, 1))
    }

    @Test
    fun aVersionTwoPayloadCarryingLiteralTriplesStillReads() {
        // The one place this reader is knowingly more forgiving than
        // sno-reference.py, and it matches sno-core. See SnoParser.readLiteralTriple.
        val payload = SnoParser.parse(payload(colors = """[[1,0.15,0.15],[0,1,0],[0,0,1],[1,1,1]]""")).payloadOrNull()
        assertNotNull(payload)
        assertEquals(0xFF, (payload.colors[0] shr 16) and 0xFF)
        assertEquals(38, (payload.colors[0] shr 8) and 0xFF, "0.15 of 255, rounded")

        // ...and it keeps the v2 frame. Flipping it as though it were v1 would
        // mirror the object.
        assertEquals(240, payload.tickAt(2, 2))
    }

    @Test
    fun aVersionOnePayloadWithAnIndexIsRefused() {
        // Version 1 never had an index, so this is not a colour it can read.
        val result = SnoParser.parse(payload(version = 1))
        assertTrue(result is SnoResult.Invalid)
        assertEquals("8b", result.rule)
    }

    @Test
    fun literalTriplesAreClampedNotRejected() {
        val payload = SnoParser.parse(payload(colors = """[[2,-1,0.5],[0,1,0],[0,0,1],[1,1,1]]""")).payloadOrNull()
        assertNotNull(payload)
        assertEquals(0xFF, (payload.colors[0] shr 16) and 0xFF)
        assertEquals(0x00, (payload.colors[0] shr 8) and 0xFF)
    }

    @Test
    fun aStrayTypeFieldIsIgnored() {
        // §1.1a: rejecting on noise would refuse every object already published,
        // and three of the seven on the network carry this field.
        val payload = SnoParser.parse(payload(extra = ""","type":"shard"""")).payloadOrNull()
        assertNotNull(payload)
        assertEquals("tetra", payload.name)
    }

    @Test
    fun unknownFieldsAreIgnored() {
        // §5: what allows an optional field to be added without a version bump.
        assertNotNull(SnoParser.parse(payload(extra = ""","emissive":true,"whatever":[1,2,3]""")).payloadOrNull())
    }

    @Test
    fun nameIsTruncatedNotRejected() {
        val long = "x".repeat(200)
        val payload = SnoParser.parse(payload(name = long)).payloadOrNull()
        assertNotNull(payload)
        assertEquals(SnoPayload.MAX_NAME, payload.name.length)
    }

    @Test
    fun aMissingNameIsToleratedBecauseNothingEnforcesIt() {
        // §1.1's table calls `name` required and neither reference implementation
        // checks it: sno-reference.py has no test at all and sno-core falls back
        // to a default. Refusing here would be stricter than everything that
        // exists, over a field that is decoration.
        val payload = SnoParser.parse("""{"v":2,"unit":0,"mode":"points","vertices":[[0,0,0]],"colors":[0],"faces":[]}""").payloadOrNull()
        assertNotNull(payload)
        assertEquals("", payload.name)
    }

    @Test
    fun anEmptyFaceListIsAPointCloud() {
        val payload = SnoParser.parse("""{"v":2,"name":"dust","unit":0,"mode":"points","vertices":[[0,0,0],[1,1,1]],"colors":[0,1],"faces":[]}""").payloadOrNull()
        assertNotNull(payload)
        assertEquals(0, payload.faceCount)
        assertEquals(2, payload.vertexCount)
    }

    @Test
    fun spinIsIgnoredWithoutUpButStillValidated() {
        // §1.7: a reader MUST ignore the value when `up` is not true, and MUST
        // reject a spin out of range whether or not `up` is present.
        val ignored = SnoParser.parse(payload(extra = ""","spin":90""")).payloadOrNull()
        assertNotNull(ignored)
        assertEquals(0, ignored.spin)
        assertTrue(!ignored.up)

        val standing = SnoParser.parse(payload(extra = ""","up":true,"spin":90""")).payloadOrNull()
        assertNotNull(standing)
        assertEquals(90, standing.spin)
        assertTrue(standing.up)
    }

    @Test
    fun upFalseIsTheSameAsAbsent() {
        val payload = SnoParser.parse(payload(extra = ""","up":false,"spin":90""")).payloadOrNull()
        assertNotNull(payload)
        assertTrue(!payload.up)
        assertEquals(0, payload.spin)
    }

    @Test
    fun anInlinePaletteIsUsed() {
        val payload =
            SnoParser
                .parse(payload(colors = "[0,1,2,3]", extra = ""","palette":[[255,0,0],[0,255,0],[0,0,255],[255,255,255]]"""))
                .payloadOrNull()
        assertNotNull(payload)
        assertEquals(0xFFFF0000.toInt(), payload.colors[0])
        assertTrue(payload.paletteRef is SnoPaletteRef.Inline)
    }

    @Test
    fun theRegisteredNameIsTheBuiltIn() {
        val payload = SnoParser.parse(payload(extra = ""","palette":"cyberspace-neon-256"""")).payloadOrNull()
        assertNotNull(payload)
        assertEquals(SnoPaletteRef.BuiltIn, payload.paletteRef)
        assertEquals(0xFFFF0000.toInt(), payload.colors[0])
    }

    @Test
    fun anUnknownPaletteNameIsRefused() {
        val result = SnoParser.parse(payload(extra = ""","palette":"someone-elses-256""""))
        assertTrue(result is SnoResult.Invalid)
        assertEquals("8a", result.rule)
    }

    @Test
    fun anUnresolvedPaletteReferenceDrawsInTheBuiltIn() {
        // §1.3b: a reference is never load-bearing. The worst case is an object
        // drawn in the wrong colours, never one that cannot be drawn.
        val reference = "nevent1qqsglcujct7e84485kkv4sdtsyfg9nszefsc7s40r4ss07a9nqpzzhghup94y"
        val result = SnoParser.parse(payload(extra = ""","palette":"$reference""""))
        val payload = result.payloadOrNull()
        assertNotNull(payload, "an unresolvable reference must never refuse the object: $result")
        assertEquals(0xFFFF0000.toInt(), payload.colors[0], "indices name the built-in until the event is in hand")
        assertTrue(payload.paletteRef is SnoPaletteRef.Event)
    }

    @Test
    fun aFetchedPaletteReplacesTheBuiltIn() {
        val reference = "nevent1qqsglcujct7e84485kkv4sdtsyfg9nszefsc7s40r4ss07a9nqpzzhghup94y"
        val fetched = SnoPalette(IntArray(256) { 0xFF123456.toInt() })
        val payload = SnoParser.parse(payload(extra = ""","palette":"$reference""""), fetched).payloadOrNull()
        assertNotNull(payload)
        assertEquals(0xFF123456.toInt(), payload.colors[0])
    }

    @Test
    fun anNaddrReferenceIsAlsoWellFormed() {
        // §1.3a accepts an naddr for a palette somebody publishes as an
        // addressable event of their own, though what an naddr cannot do is name
        // a kind 3367, which is where the palettes actually are.
        val naddr = "naddr1qqyhqctvv468gefdxypzp68dx7vvdlltll5w6duccml7hllga5me33hla0l73mfhnrr0l6llqvzqqqqdyucnkls9"
        val payload = SnoParser.parse(payload(extra = ""","palette":"$naddr"""")).payloadOrNull()
        assertNotNull(payload)
        assertTrue(payload.paletteRef is SnoPaletteRef.Event)
    }

    @Test
    fun aVertexBeyondTheBoundIsRefused() {
        // Amethyst's own rule, and a deliberate divergence: §1.8 puts the bound on
        // publishers and lets readers repair instead, which neither reference
        // implementation does either way.
        val result = SnoParser.parse(payload(vertices = "[[0,0,0],[2,0,0],[1,0,2],[65,2,1]]"))
        assertTrue(result is SnoResult.Invalid)
        assertEquals("bound", result.rule)
    }

    @Test
    fun aVertexExactlyAtTheBoundIsAccepted() {
        assertNotNull(SnoParser.parse(payload(vertices = "[[0,0,0],[2,0,0],[1,0,2],[64,2,1]]")).payloadOrNull())
    }

    private fun payload(
        version: Int = 2,
        name: String = "tetra",
        vertices: String = "[[0,0,0],[2,0,0],[1,0,2],[1,2,1]]",
        colors: String = "[238,235,239,225]",
        ticks: String = ""","ticks":[-4]""",
        extent: String = ""","extent":8""",
        extra: String = "",
    ) = """{"v":$version,"name":"$name","unit":0$extent,"mode":"solid","vertices":$vertices$ticks,"colors":$colors,"faces":[[0,1,2],[0,1,3],[1,2,3],[0,2,3]]$extra}"""
}
