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

import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoMode
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoPayload
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Draws a Simple Nostr Object into a pixel buffer, on the CPU.
 *
 * Software rather than `Canvas.drawVertices`, which is common Compose API and
 * would give interpolated triangles for free, because that call is only
 * hardware-accelerated from API 29 and Amethyst's `minSdk` is 26 — on Android 8
 * and 9 it silently draws nothing. Doing it here also buys a real depth buffer,
 * which a painter's-algorithm fallback does not have and which lattice geometry
 * needs the moment two objects are authored to share an edge, and it makes the
 * whole renderer testable headless, with no device and no screenshot harness.
 *
 * The shading is what DECK-0003 §4 decides, so that the same object does not
 * look like two objects:
 * - **Unlit.** A face takes its colour by interpolating its three vertices and
 *   no light in the scene changes it. This is `KHR_materials_unlit` with a
 *   `COLOR_0` attribute, which is the one-sentence bridge to anyone in glTF.
 * - **Two-sided.** A face has no front and no back: winding order carries no
 *   meaning and nothing is culled by it.
 * - **A face that carries its own colour is filled with it flatly**, and there
 *   is nothing to interpolate or average: the seam between two such faces is
 *   exact, which is the whole purpose of `facecolors`.
 * - **No invented geometry.** No subdivision, no smoothing that moves a vertex,
 *   no hole filling, and no synthesised smooth normals. The lattice is exact and
 *   a renderer that moves a vertex has broken the one guarantee it makes.
 *
 * Positions arrive as exact integers and are converted to floats here, at the
 * boundary, and nowhere earlier (§1.2).
 */
object SnoRasterizer {
    /** A three-quarter view, which is what shows a small object's shape best. */
    const val DEFAULT_YAW_DEGREES = 30f
    const val DEFAULT_PITCH_DEGREES = -20f

    /** Fraction of the shorter side left empty around the object. */
    private const val MARGIN = 0.08f

    /**
     * Below this many pixels across, a solid object is drawn as points too, so
     * it "remains visible when it is smaller on screen than a triangle" (§1.5).
     */
    private const val TOO_SMALL_FOR_TRIANGLES = 3f

    private const val TRANSPARENT = 0

    /** Below this a triangle has no area to fill and its reciprocal is noise. */
    private const val MIN_AREA = 1e-6f

    /**
     * @param background an opaque ARGB fill, or 0 for a transparent buffer.
     * @return `width * height` ARGB pixels, row-major.
     */
    fun render(
        payload: SnoPayload,
        width: Int,
        height: Int,
        yawDegrees: Float = DEFAULT_YAW_DEGREES,
        pitchDegrees: Float = DEFAULT_PITCH_DEGREES,
        background: Int = TRANSPARENT,
    ): IntArray {
        require(width > 0 && height > 0) { "a raster needs a positive size" }

        val pixels = IntArray(width * height) { background }
        if (payload.vertexCount == 0) return pixels

        val depth = FloatArray(width * height) { Float.NEGATIVE_INFINITY }
        val screen = project(payload, width, height, yawDegrees, pitchDegrees)

        val drawTriangles = payload.mode == SnoMode.SOLID && payload.faceCount > 0
        if (drawTriangles) {
            drawFaces(payload, screen, pixels, depth, width, height)
        }
        if (payload.mode == SnoMode.LINES) {
            drawEdges(payload, screen, pixels, depth, width, height)
        }
        if (payload.mode == SnoMode.POINTS || payload.mode == SnoMode.LINES || !drawTriangles || screen.spanPixels < TOO_SMALL_FOR_TRIANGLES) {
            drawPoints(payload, screen, pixels, depth, width, height)
        }
        return pixels
    }

    /**
     * Every vertex in pixel space, plus how much of the raster the object fills.
     *
     * Orthographic, looking down `-Z`: `+X` is right, `+Y` is up and `+Z` is
     * toward the viewer (§2), so a larger Z is nearer and screen Y runs the
     * other way from model Y. A thumbnail wants no perspective distortion and
     * no near plane to clip against.
     */
    private fun project(
        payload: SnoPayload,
        width: Int,
        height: Int,
        yawDegrees: Float,
        pitchDegrees: Float,
    ): Projection {
        val count = payload.vertexCount
        val xs = FloatArray(count)
        val ys = FloatArray(count)
        val zs = FloatArray(count)

        val yaw = yawDegrees * DEG_TO_RAD
        val pitch = pitchDegrees * DEG_TO_RAD
        val cosYaw = cos(yaw)
        val sinYaw = sin(yaw)
        val cosPitch = cos(pitch)
        val sinPitch = sin(pitch)

        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (i in 0 until count) {
            // The one conversion from the exact lattice to floats.
            val x = payload.tickAt(i, 0).toFloat()
            val y = payload.tickAt(i, 1).toFloat()
            val z = payload.tickAt(i, 2).toFloat()

            val x1 = x * cosYaw + z * sinYaw
            val z1 = -x * sinYaw + z * cosYaw
            val y2 = y * cosPitch - z1 * sinPitch
            val z2 = y * sinPitch + z1 * cosPitch

            xs[i] = x1
            ys[i] = y2
            zs[i] = z2

            if (x1 < minX) minX = x1
            if (x1 > maxX) maxX = x1
            if (y2 < minY) minY = y2
            if (y2 > maxY) maxY = y2
        }

        val spanX = maxX - minX
        val spanY = maxY - minY
        val usable = min(width, height) * (1f - 2f * MARGIN)
        val largest = max(spanX, spanY)
        val scale = if (largest <= 0f) 1f else usable / largest

        val centreX = (minX + maxX) * 0.5f
        val centreY = (minY + maxY) * 0.5f
        val halfWidth = width * 0.5f
        val halfHeight = height * 0.5f

        for (i in 0 until count) {
            xs[i] = halfWidth + (xs[i] - centreX) * scale
            // Screen Y grows downward; model +Y is up.
            ys[i] = halfHeight - (ys[i] - centreY) * scale
        }

        return Projection(xs, ys, zs, largest * scale)
    }

    private class Projection(
        val xs: FloatArray,
        val ys: FloatArray,
        val zs: FloatArray,
        /** How many pixels across the object turned out to be. */
        val spanPixels: Float,
    )

    private fun drawFaces(
        payload: SnoPayload,
        screen: Projection,
        pixels: IntArray,
        depth: FloatArray,
        width: Int,
        height: Int,
    ) {
        val faceColors = payload.faceColors
        for (face in 0 until payload.faceCount) {
            val a = payload.faces[face * 3]
            val b = payload.faces[face * 3 + 1]
            val c = payload.faces[face * 3 + 2]
            // A face that carries its own colour fills the whole triangle flatly,
            // and its vertices contribute nothing to the fill (§1.4a).
            val flat = faceColors?.get(face)
            fillTriangle(
                screen,
                pixels,
                depth,
                width,
                height,
                a,
                b,
                c,
                flat ?: payload.colors[a],
                flat ?: payload.colors[b],
                flat ?: payload.colors[c],
            )
        }
    }

    /**
     * One triangle, barycentric, both sides drawn.
     *
     * The edge function's sign tells us the winding, and it is used only to
     * normalise the barycentric weights — never to decide whether to draw
     * (§1.4).
     */
    @Suppress("detekt.LongParameterList")
    private fun fillTriangle(
        screen: Projection,
        pixels: IntArray,
        depth: FloatArray,
        width: Int,
        height: Int,
        ia: Int,
        ib: Int,
        ic: Int,
        colorA: Int,
        colorB: Int,
        colorC: Int,
    ) {
        val ax = screen.xs[ia]
        val ay = screen.ys[ia]
        val bx = screen.xs[ib]
        val by = screen.ys[ib]
        val cx = screen.xs[ic]
        val cy = screen.ys[ic]

        val area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
        if (area > -MIN_AREA && area < MIN_AREA) return

        val left = max(0, floorToInt(min(ax, min(bx, cx))))
        val right = min(width - 1, ceilToInt(max(ax, max(bx, cx))))
        val top = max(0, floorToInt(min(ay, min(by, cy))))
        val bottom = min(height - 1, ceilToInt(max(ay, max(by, cy))))
        if (left > right || top > bottom) return

        // Each edge function is linear in x and y, so it is evaluated once at
        // the corner of the span and then stepped: three adds a pixel instead
        // of six multiplies and four subtractions. The weights still come out
        // barycentric, they are just not recomputed from scratch every time.
        //
        // The sign of the signed area is folded into the coefficients rather
        // than tested per pixel, which is also what draws both sides of a face
        // (§1.4): a triangle wound the other way has its edge functions
        // negated along with its area, so the inside test is the same one.
        val flip = if (area < 0f) -1f else 1f
        val constA = (bx * cy - by * cx) * flip
        val stepAx = (by - cy) * flip
        val stepAy = (cx - bx) * flip
        val constB = (cx * ay - cy * ax) * flip
        val stepBx = (cy - ay) * flip
        val stepBy = (ax - cx) * flip
        val total = area * flip
        val inverseTotal = 1f / total

        val az = screen.zs[ia]
        val bz = screen.zs[ib]
        val cz = screen.zs[ic]

        val startX = left + 0.5f
        var rowA = constA + startX * stepAx + (top + 0.5f) * stepAy
        var rowB = constB + startX * stepBx + (top + 0.5f) * stepBy

        for (py in top..bottom) {
            var edgeA = rowA
            var edgeB = rowB
            var at = py * width + left
            for (px in left..right) {
                val edgeC = total - edgeA - edgeB
                if (edgeA >= 0f && edgeB >= 0f && edgeC >= 0f) {
                    val wA = edgeA * inverseTotal
                    val wB = edgeB * inverseTotal
                    val wC = edgeC * inverseTotal
                    val z = wA * az + wB * bz + wC * cz
                    if (z > depth[at]) {
                        depth[at] = z
                        pixels[at] = blend(colorA, colorB, colorC, wA, wB, wC)
                    }
                }
                edgeA += stepAx
                edgeB += stepBx
                at++
            }
            rowA += stepAy
            rowB += stepBy
        }
    }

    /**
     * The edges of the faces, each drawn once (§1.5); with no faces, one
     * polyline through the vertices in order.
     */
    private fun drawEdges(
        payload: SnoPayload,
        screen: Projection,
        pixels: IntArray,
        depth: FloatArray,
        width: Int,
        height: Int,
    ) {
        if (payload.faceCount == 0) {
            for (i in 0 until payload.vertexCount - 1) {
                drawLine(screen, pixels, depth, width, height, i, i + 1, payload.colors[i], payload.colors[i + 1])
            }
            return
        }

        val seen = HashSet<Long>(payload.faceCount * 3)
        for (face in 0 until payload.faceCount) {
            val a = payload.faces[face * 3]
            val b = payload.faces[face * 3 + 1]
            val c = payload.faces[face * 3 + 2]
            edge(seen, a, b)?.let { drawLine(screen, pixels, depth, width, height, a, b, payload.colors[a], payload.colors[b]) }
            edge(seen, b, c)?.let { drawLine(screen, pixels, depth, width, height, b, c, payload.colors[b], payload.colors[c]) }
            edge(seen, a, c)?.let { drawLine(screen, pixels, depth, width, height, a, c, payload.colors[a], payload.colors[c]) }
        }
    }

    /** Null when this edge has already been drawn; the key otherwise. */
    private fun edge(
        seen: HashSet<Long>,
        a: Int,
        b: Int,
    ): Long? {
        val low = min(a, b).toLong()
        val high = max(a, b).toLong()
        val key = (low shl 32) or high
        return if (seen.add(key)) key else null
    }

    private fun drawLine(
        screen: Projection,
        pixels: IntArray,
        depth: FloatArray,
        width: Int,
        height: Int,
        from: Int,
        to: Int,
        colorFrom: Int,
        colorTo: Int,
    ) {
        val x0 = screen.xs[from]
        val y0 = screen.ys[from]
        val x1 = screen.xs[to]
        val y1 = screen.ys[to]
        val steps = max(abs(x1 - x0), abs(y1 - y0)).roundToInt()
        if (steps <= 0) {
            plot(pixels, depth, width, height, x0.roundToInt(), y0.roundToInt(), screen.zs[from], colorFrom)
            return
        }
        val z0 = screen.zs[from]
        val z1 = screen.zs[to]
        for (step in 0..steps) {
            val t = step.toFloat() / steps
            plot(
                pixels,
                depth,
                width,
                height,
                (x0 + (x1 - x0) * t).roundToInt(),
                (y0 + (y1 - y0) * t).roundToInt(),
                z0 + (z1 - z0) * t,
                mix(colorFrom, colorTo, t),
            )
        }
    }

    private fun drawPoints(
        payload: SnoPayload,
        screen: Projection,
        pixels: IntArray,
        depth: FloatArray,
        width: Int,
        height: Int,
    ) {
        for (i in 0 until payload.vertexCount) {
            plot(pixels, depth, width, height, screen.xs[i].roundToInt(), screen.ys[i].roundToInt(), screen.zs[i], payload.colors[i])
        }
    }

    private fun plot(
        pixels: IntArray,
        depth: FloatArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        z: Float,
        color: Int,
    ) {
        if (x < 0 || y < 0 || x >= width || y >= height) return
        val at = y * width + x
        if (z < depth[at]) return
        depth[at] = z
        pixels[at] = color
    }

    /** Barycentric interpolation between three opaque colours. */
    private fun blend(
        a: Int,
        b: Int,
        c: Int,
        wA: Float,
        wB: Float,
        wC: Float,
    ): Int {
        if (a == b && b == c) return a
        val r = ((a shr 16 and 0xFF) * wA + (b shr 16 and 0xFF) * wB + (c shr 16 and 0xFF) * wC).roundToInt()
        val g = ((a shr 8 and 0xFF) * wA + (b shr 8 and 0xFF) * wB + (c shr 8 and 0xFF) * wC).roundToInt()
        val bl = ((a and 0xFF) * wA + (b and 0xFF) * wB + (c and 0xFF) * wC).roundToInt()
        return (0xFF shl 24) or (clamp255(r) shl 16) or (clamp255(g) shl 8) or clamp255(bl)
    }

    private fun mix(
        from: Int,
        to: Int,
        t: Float,
    ): Int {
        if (from == to) return from
        val r = ((from shr 16 and 0xFF) + ((to shr 16 and 0xFF) - (from shr 16 and 0xFF)) * t).roundToInt()
        val g = ((from shr 8 and 0xFF) + ((to shr 8 and 0xFF) - (from shr 8 and 0xFF)) * t).roundToInt()
        val b = ((from and 0xFF) + ((to and 0xFF) - (from and 0xFF)) * t).roundToInt()
        return (0xFF shl 24) or (clamp255(r) shl 16) or (clamp255(g) shl 8) or clamp255(b)
    }

    private fun clamp255(v: Int) =
        if (v < 0) {
            0
        } else if (v > 255) {
            255
        } else {
            v
        }

    private fun floorToInt(v: Float): Int {
        val i = v.toInt()
        return if (v < 0f && v != i.toFloat()) i - 1 else i
    }

    private fun ceilToInt(v: Float): Int {
        val i = v.toInt()
        return if (v > 0f && v != i.toFloat()) i + 1 else i
    }

    private const val DEG_TO_RAD = 0.017453292f
}
