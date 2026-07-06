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
package com.vitorpamplona.quartz.experimental.ps1saves

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArrayOrNull

/**
 * The save icon embedded in a PS1 save's title frame — the sprite the PS1 BIOS
 * memory-card screen shows, animated, when the console boots without a disc.
 *
 * The title frame (first 128 bytes of a save's first block) lays it out as:
 *
 * - `0x00` — magic `SC`.
 * - `0x02` — icon display flag: `0x11`/`0x12`/`0x13` = 1/2/3 animation frames.
 * - `0x60` — 16-entry CLUT, each a little-endian 16-bit color
 *            (bits 0-4 red, 5-9 green, 10-14 blue); raw `0x0000` is transparent.
 * - `0x80` — the frames, each 16×16 pixels at 4bpp (128 bytes), low nibble
 *            first; pixel values index the CLUT.
 */
@Immutable
class Ps1SaveIcon(
    /**
     * One 256-entry row-major ARGB pixel array per animation frame. Treat as
     * frozen: the arrays back the class's `@Immutable` contract, so mutating
     * them in place would not recompose — build a new [Ps1SaveIcon] to change
     * pixels.
     */
    val frames: List<IntArray>,
) {
    companion object {
        /** Icon edge length in pixels. */
        const val SIZE = 16

        /** Pixels per frame ([SIZE] × [SIZE]). */
        const val PIXELS = SIZE * SIZE

        private const val FLAG_OFFSET = 2
        private const val PALETTE_OFFSET = 0x60
        private const val PALETTE_ENTRIES = 16
        private const val FRAMES_OFFSET = 0x80
        private const val FRAME_BYTES = PIXELS / 2

        /**
         * Decodes the icon from a save block's hex-encoded content. Null when the
         * content is not hex, lacks the `SC` magic or a known display flag, or is
         * too short for the frame count it declares.
         */
        fun parse(blockHex: String): Ps1SaveIcon? {
            val neededForAllFrames = (FRAMES_OFFSET + 3 * FRAME_BYTES) * 2
            val bytes = blockHex.take(neededForAllFrames).hexToByteArrayOrNull() ?: return null

            if (bytes.size < FRAMES_OFFSET) return null
            if (bytes[0] != 'S'.code.toByte() || bytes[1] != 'C'.code.toByte()) return null

            val frameCount =
                when (bytes[FLAG_OFFSET].toInt() and 0xFF) {
                    0x11 -> 1
                    0x12 -> 2
                    0x13 -> 3
                    else -> return null
                }
            if (bytes.size < FRAMES_OFFSET + frameCount * FRAME_BYTES) return null

            val palette =
                IntArray(PALETTE_ENTRIES) { entry ->
                    val lo = bytes[PALETTE_OFFSET + entry * 2].toInt() and 0xFF
                    val hi = bytes[PALETTE_OFFSET + entry * 2 + 1].toInt() and 0xFF
                    argbFromClut((hi shl 8) or lo)
                }

            val frames =
                List(frameCount) { frame ->
                    val base = FRAMES_OFFSET + frame * FRAME_BYTES
                    IntArray(PIXELS) { pixel ->
                        val packed = bytes[base + pixel / 2].toInt() and 0xFF
                        palette[if (pixel % 2 == 0) packed and 0xF else packed shr 4]
                    }
                }

            return Ps1SaveIcon(frames)
        }

        private fun argbFromClut(raw: Int): Int {
            if (raw == 0) return 0
            val r = expand5To8(raw and 0x1F)
            val g = expand5To8((raw shr 5) and 0x1F)
            val b = expand5To8((raw shr 10) and 0x1F)
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        private fun expand5To8(v: Int) = (v shl 3) or (v shr 2)
    }
}
