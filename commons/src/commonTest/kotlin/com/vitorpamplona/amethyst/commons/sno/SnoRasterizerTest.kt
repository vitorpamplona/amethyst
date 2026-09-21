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
package com.vitorpamplona.amethyst.commons.sno

import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoParser
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The rendering decisions DECK-0003 §4 makes, asserted on pixels.
 *
 * §4 decides shading rather than leaving it to taste precisely so that the same
 * object does not look like two objects, so each of its rules gets a test here
 * rather than a comment.
 */
class SnoRasterizerTest {
    private val dim = 64

    private fun parse(json: String): SnoPayload {
        val payload = SnoParser.parse(json).payloadOrNull()
        assertNotNull(payload, "fixture did not parse: $json")
        return payload
    }

    /** A single triangle filling most of the frame, facing the viewer. */
    private fun triangle(
        colors: String = "[238,238,238]",
        extra: String = "",
        mode: String = "solid",
    ) = parse(
        """{"v":2,"name":"t","unit":0,"mode":"$mode","vertices":[[-4,-4,0],[4,-4,0],[0,4,0]],"colors":$colors,"faces":[[0,1,2]]$extra}""",
    )

    private fun IntArray.at(
        x: Int,
        y: Int,
    ) = this[y * dim + x]

    private fun IntArray.litCount() = count { it != 0 }

    @Test
    fun aSolidTriangleFillsItsMiddleAndLeavesTheCornersAlone() {
        val pixels = SnoRasterizer.render(triangle(), dim, dim, yawDegrees = 0f, pitchDegrees = 0f)
        assertEquals(0xFFFF0000.toInt(), pixels.at(dim / 2, dim / 2), "the centre should be the face")
        assertEquals(0, pixels.at(0, 0), "a corner outside the triangle stays transparent")
        assertEquals(0, pixels.at(dim - 1, 0))
    }

    @Test
    fun theBackgroundIsHonoured() {
        val pixels = SnoRasterizer.render(triangle(), dim, dim, yawDegrees = 0f, pitchDegrees = 0f, background = 0xFF101010.toInt())
        assertEquals(0xFF101010.toInt(), pixels.at(0, 0))
        assertEquals(0xFFFF0000.toInt(), pixels.at(dim / 2, dim / 2))
    }

    @Test
    fun vertexColoursInterpolateAcrossAFace() {
        // §4: the default reading is unlit, and a face takes its colour by
        // interpolating its three vertices.
        val pixels = SnoRasterizer.render(triangle(colors = "[238,235,239]"), dim, dim, yawDegrees = 0f, pitchDegrees = 0f)
        val middle = pixels.at(dim / 2, dim / 2)
        val r = middle shr 16 and 0xFF
        val g = middle shr 8 and 0xFF
        val b = middle and 0xFF
        assertTrue(r in 1..254 && g in 1..254 && b in 1..254, "the centre of a red/green/blue triangle should be a mix, was $r,$g,$b")
    }

    @Test
    fun aFaceColourFillsFlatlyAndItsVerticesContributeNothing() {
        // §1.4a: when a face has a colour, that colour fills the whole triangle.
        // The seam between two such faces is exact, which is the point of it.
        val pixels =
            SnoRasterizer.render(
                triangle(colors = "[238,235,239]", extra = ""","facecolors":[225]"""),
                dim,
                dim,
                yawDegrees = 0f,
                pitchDegrees = 0f,
            )
        val lit = pixels.filter { it != 0 }
        assertTrue(lit.isNotEmpty())
        assertTrue(lit.all { it == 0xFFFFFFFF.toInt() }, "every covered pixel should be the face colour, flat")
    }

    @Test
    fun bothSidesOfAFaceAreDrawn() {
        // §1.4: a face has no front and no back, and a reader MUST NOT cull on
        // the basis of winding.
        val clockwise = parse("""{"v":2,"name":"t","unit":0,"mode":"solid","vertices":[[-4,-4,0],[4,-4,0],[0,4,0]],"colors":[238,238,238],"faces":[[0,1,2]]}""")
        val counter = parse("""{"v":2,"name":"t","unit":0,"mode":"solid","vertices":[[-4,-4,0],[4,-4,0],[0,4,0]],"colors":[238,238,238],"faces":[[0,2,1]]}""")

        val a = SnoRasterizer.render(clockwise, dim, dim, yawDegrees = 0f, pitchDegrees = 0f)
        val b = SnoRasterizer.render(counter, dim, dim, yawDegrees = 0f, pitchDegrees = 0f)
        assertTrue(a.contentEquals(b), "reversing a face's winding must not change what is drawn")
        assertTrue(a.litCount() > 100)
    }

    @Test
    fun theNearerFaceWinsWhicheverOrderItIsListedIn() {
        // This is what the depth buffer buys over a painter's-algorithm
        // fallback: two coplanar-in-screen triangles at different depths sort
        // correctly no matter how the payload orders them.
        val nearLast =
            parse(
                """{"v":2,"name":"z","unit":0,"mode":"solid",
                   "vertices":[[-4,-4,-2],[4,-4,-2],[0,4,-2],[-4,-4,2],[4,-4,2],[0,4,2]],
                   "colors":[238,238,238,235,235,235],"faces":[[0,1,2],[3,4,5]]}""",
            )
        val nearFirst =
            parse(
                """{"v":2,"name":"z","unit":0,"mode":"solid",
                   "vertices":[[-4,-4,2],[4,-4,2],[0,4,2],[-4,-4,-2],[4,-4,-2],[0,4,-2]],
                   "colors":[235,235,235,238,238,238],"faces":[[0,1,2],[3,4,5]]}""",
            )

        val a = SnoRasterizer.render(nearLast, dim, dim, yawDegrees = 0f, pitchDegrees = 0f)
        val b = SnoRasterizer.render(nearFirst, dim, dim, yawDegrees = 0f, pitchDegrees = 0f)
        assertEquals(0xFF00FF00.toInt(), a.at(dim / 2, dim / 2), "the nearer (green) face should win")
        assertEquals(0xFF00FF00.toInt(), b.at(dim / 2, dim / 2), "...in either listing order")
    }

    @Test
    fun pointsModeDrawsTheVerticesAndNotTheTriangle() {
        val pixels = SnoRasterizer.render(triangle(mode = "points"), dim, dim, yawDegrees = 0f, pitchDegrees = 0f)
        assertTrue(pixels.litCount() <= 3, "three vertices should light at most three pixels, lit ${pixels.litCount()}")
        assertTrue(pixels.litCount() >= 1)
    }

    @Test
    fun linesModeDrawsEachSharedEdgeOnce() {
        // A closed pair of triangles sharing an edge: the outline is drawn, the
        // interior is not, and the shared edge costs one line rather than two.
        val quad =
            parse(
                """{"v":2,"name":"q","unit":0,"mode":"lines",
                   "vertices":[[-4,-4,0],[4,-4,0],[4,4,0],[-4,4,0]],
                   "colors":[225,225,225,225],"faces":[[0,1,2],[0,2,3]]}""",
            )
        val lines = SnoRasterizer.render(quad, dim, dim, yawDegrees = 0f, pitchDegrees = 0f)
        val solid = SnoRasterizer.render(parse(solidQuadJson), dim, dim, yawDegrees = 0f, pitchDegrees = 0f)

        assertTrue(lines.litCount() > 0)
        assertTrue(lines.litCount() * 4 < solid.litCount(), "lines mode must not fill: lit ${lines.litCount()} against a solid ${solid.litCount()}")

        // A point inside the lower triangle and off every edge stays empty...
        assertEquals(0, lines.at(dim / 2 + 13, dim / 2 + 13), "lines mode must not fill")

        // ...while the edge the two triangles share is drawn. In the wire format
        // a face IS a triangle, so that diagonal is a real edge of both faces and
        // belongs in the outline; it is drawn once rather than twice, which is
        // what the shared-edge rule of §1.5 buys.
        assertTrue(lines.at(dim / 2, dim / 2) != 0, "the shared edge should be drawn")
    }

    private val solidQuadJson =
        """{"v":2,"name":"q","unit":0,"mode":"solid",
           "vertices":[[-4,-4,0],[4,-4,0],[4,4,0],[-4,4,0]],
           "colors":[225,225,225,225],"faces":[[0,1,2],[0,2,3]]}"""

    @Test
    fun linesWithNoFacesIsOnePolylineThroughTheVerticesInOrder() {
        val path =
            parse(
                """{"v":2,"name":"p","unit":0,"mode":"lines",
                   "vertices":[[-4,-4,0],[0,4,0],[4,-4,0]],"colors":[225,225,225],"faces":[]}""",
            )
        val pixels = SnoRasterizer.render(path, dim, dim, yawDegrees = 0f, pitchDegrees = 0f)
        assertTrue(pixels.litCount() > 20, "a polyline of two segments should light a run of pixels")
    }

    @Test
    fun anObjectTooSmallForATriangleIsStillVisible() {
        // §1.5: a client SHOULD also draw the vertices as points in every mode,
        // so that an object remains visible when it is smaller on screen than a
        // triangle.
        val pixels = SnoRasterizer.render(triangle(), 2, 2, yawDegrees = 0f, pitchDegrees = 0f)
        assertTrue(pixels.any { it != 0 }, "a two-pixel raster should still show something")
    }

    @Test
    fun nothingIsDrawnOutsideTheBuffer() {
        // The limits are the defence, and the buffer is sized from the raster
        // rather than from anything the payload says.
        val pixels = SnoRasterizer.render(triangle(), 17, 5, yawDegrees = 37f, pitchDegrees = -61f)
        assertEquals(17 * 5, pixels.size)
    }

    @Test
    fun aSingleVertexDoesNotCrash() {
        val dot = parse("""{"v":2,"name":"d","unit":0,"mode":"points","vertices":[[0,0,0]],"colors":[225],"faces":[]}""")
        val pixels = SnoRasterizer.render(dot, dim, dim)
        assertEquals(1, pixels.litCount())
    }

    @Test
    fun coincidentVerticesDoNotDivideByZero() {
        val degenerate =
            parse(
                """{"v":2,"name":"d","unit":0,"mode":"solid","vertices":[[1,1,1],[1,1,1],[1,1,1]],"colors":[225,225,225],"faces":[[0,1,2]]}""",
            )
        val pixels = SnoRasterizer.render(degenerate, dim, dim)
        assertEquals(dim * dim, pixels.size)
    }

    @Test
    fun theSameObjectRendersIdenticallyEveryTime() {
        val payload = triangle(colors = "[238,235,239]")
        val a = SnoRasterizer.render(payload, dim, dim)
        val b = SnoRasterizer.render(payload, dim, dim)
        assertTrue(a.contentEquals(b))
    }

    @Test
    fun rotatingChangesTheDrawingButNotTheModel() {
        val payload = triangle()
        val front = SnoRasterizer.render(payload, dim, dim, yawDegrees = 0f, pitchDegrees = 0f)
        val turned = SnoRasterizer.render(payload, dim, dim, yawDegrees = 60f, pitchDegrees = 0f)
        assertTrue(!front.contentEquals(turned), "a yaw should change the raster")

        // No invented geometry, and nothing moved: the positions are untouched.
        assertEquals(-480, payload.tickAt(0, 0))
        assertEquals(480, payload.tickAt(1, 0))
    }
}
