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
package com.vitorpamplona.fantasyconsole.cart

import com.vitorpamplona.fantasyconsole.png.PngDecoder
import com.vitorpamplona.fantasyconsole.runtime.Addr

/**
 * A loaded cartridge: [rom] holds its data as it lands in RAM at boot (sprites, map, flags,
 * music and sound, 0x0000-0x42ff) and [code] its Lua, decompressed, as P8SCII.
 */
class Cart(
    val rom: ByteArray,
    val code: String,
) {
    init {
        require(rom.size == ROM_SIZE) { "ROM must be $ROM_SIZE bytes" }
    }

    companion object {
        const val ROM_SIZE = 0x8000
    }
}

class CartFormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Reads the three cart formats: `.p8.png` (the ROM hidden in the two low bits of each channel of
 * a 160x205 image), `.p8` (text sections) and `.p8.rom` (the raw 32 KiB). Decides by content,
 * not by name, since a Blossom URI only says `.png`.
 */
object CartLoader {
    /** `__lua__`, `__gfx__`, `__meta:title__`…: a section header line in a `.p8` file. */
    private val SECTION = Regex("^__([a-z0-9]+)(:[^_]*)?__$")

    fun load(bytes: ByteArray): Cart =
        try {
            when {
                PngDecoder.isPng(bytes) -> fromPng(bytes)
                looksLikeText(bytes) -> fromText(bytes.decodeToString())
                bytes.size == Cart.ROM_SIZE || bytes.size == Cart.ROM_SIZE + 1 -> fromRom(bytes.copyOf(Cart.ROM_SIZE))
                else -> throw CartFormatException("not a cartridge")
            }
        } catch (e: CartFormatException) {
            throw e
        } catch (e: Exception) {
            throw CartFormatException("cannot read cartridge: ${e.message}", e)
        }

    /** Bytes of a PNG needed by [hasCartShape]: the signature and the IHDR size fields. */
    const val SHAPE_PREFIX_BYTES = 24

    /**
     * Whether a PNG starting with [prefix] (at least [SHAPE_PREFIX_BYTES] long) is 160x205, the
     * size of a `.p8.png` cart. Cheap enough to ask of every PNG before downloading the rest.
     */
    fun hasCartShape(prefix: ByteArray): Boolean {
        if (prefix.size < SHAPE_PREFIX_BYTES || !PngDecoder.isPng(prefix)) return false

        fun int32(o: Int) =
            ((prefix[o].toInt() and 0xff) shl 24) or ((prefix[o + 1].toInt() and 0xff) shl 16) or
                ((prefix[o + 2].toInt() and 0xff) shl 8) or (prefix[o + 3].toInt() and 0xff)
        return prefix.decodeToString(12, 16) == "IHDR" && int32(16) == CART_WIDTH && int32(20) == CART_HEIGHT
    }

    const val CART_WIDTH = 160
    const val CART_HEIGHT = 205

    private fun looksLikeText(bytes: ByteArray): Boolean {
        val head = bytes.copyOf(minOf(bytes.size, 64)).decodeToString()
        return head.startsWith("pico-8 cartridge") || head.contains("__lua__")
    }

    fun fromRom(rom: ByteArray): Cart = Cart(rom.copyOf(Cart.ROM_SIZE), CodeCompression.decode(rom, Addr.CART_DATA_END, Cart.ROM_SIZE))

    fun fromPng(bytes: ByteArray): Cart {
        val image = PngDecoder.decode(bytes)
        if (image.width * image.height < Cart.ROM_SIZE) throw CartFormatException("image too small to hold a cartridge")
        val rom = ByteArray(Cart.ROM_SIZE)
        for (i in 0 until Cart.ROM_SIZE) {
            val p = image.argb[i]
            val a = (p ushr 24) and 3
            val r = (p ushr 16) and 3
            val g = (p ushr 8) and 3
            val b = p and 3
            rom[i] = ((a shl 6) or (r shl 4) or (g shl 2) or b).toByte()
        }
        return fromRom(rom)
    }

    /** A `.p8` file: a header, then `__lua__`, `__gfx__`, `__gff__`, `__map__`, `__sfx__`, `__music__` sections. */
    fun fromText(text: String): Cart {
        val rom = ByteArray(Cart.ROM_SIZE)
        val sections = HashMap<String, MutableList<String>>()
        var current: MutableList<String>? = null
        val lua = StringBuilder()
        var inLua = false
        for (rawLine in text.split('\n')) {
            val line = rawLine.removeSuffix("\r")
            val header = SECTION.find(line.trim())
            if (header != null) {
                val name = header.groupValues[1]
                inLua = name == "lua"
                current = if (inLua) null else sections.getOrPut(name) { ArrayList() }
                continue
            }
            if (inLua) {
                lua.append(line).append('\n')
            } else {
                current?.add(line)
            }
        }

        sections["gfx"]?.let { lines ->
            // One hex digit per pixel; each byte holds two, left pixel in the low nibble.
            for ((y, line) in lines.take(128).withIndex()) {
                for (x in 0 until minOf(128, line.length) step 2) {
                    val lo = hex(line[x])
                    val hi = if (x + 1 < line.length) hex(line[x + 1]) else 0
                    rom[Addr.GFX + y * 64 + x / 2] = (lo or (hi shl 4)).toByte()
                }
            }
        }
        sections["gff"]?.let { readHexBytes(it.joinToString(""), rom, Addr.FLAGS, 0x100) }
        sections["map"]?.let { lines ->
            for ((y, line) in lines.take(32).withIndex()) readHexBytes(line, rom, Addr.MAP + y * 128, 128)
        }
        sections["sfx"]?.let { lines ->
            for ((n, line) in lines.take(64).withIndex()) readSfx(line.trim(), rom, Addr.SFX + n * 68)
        }
        sections["music"]?.let { lines ->
            for ((n, line) in lines.take(64).withIndex()) readMusic(line.trim(), rom, Addr.MUSIC + n * 4)
        }
        return Cart(rom, P8scii.fromUnicode(lua.toString()))
    }

    private fun hex(c: Char): Int =
        when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> 0
        }

    private fun readHexBytes(
        line: String,
        rom: ByteArray,
        at: Int,
        max: Int,
    ) {
        val n = minOf(max, line.length / 2)
        for (i in 0 until n) rom[at + i] = ((hex(line[i * 2]) shl 4) or hex(line[i * 2 + 1])).toByte()
    }

    /**
     * `__sfx__` lines: 8 hex digits of header (editor mode, speed, loop start, loop end), then 32
     * notes of 5 digits each: pitch (2), waveform (1, 8-f being custom instruments 0-7), volume,
     * effect. In memory each note is 16 bits: pitch 0-5, waveform 6-8, volume 9-11, effect
     * 12-14, custom 15.
     */
    private fun readSfx(
        line: String,
        rom: ByteArray,
        at: Int,
    ) {
        if (line.length < 8) return
        for (i in 0 until 4) rom[at + 64 + i] = ((hex(line[i * 2]) shl 4) or hex(line[i * 2 + 1])).toByte()
        for (note in 0 until 32) {
            val o = 8 + note * 5
            if (o + 5 > line.length) break
            val pitch = (hex(line[o]) shl 4) or hex(line[o + 1])
            val wave = hex(line[o + 2])
            val volume = hex(line[o + 3])
            val effect = hex(line[o + 4])
            val word =
                (pitch and 0x3f) or ((wave and 7) shl 6) or ((volume and 7) shl 9) or
                    ((effect and 7) shl 12) or (if (wave >= 8) 0x8000 else 0)
            rom[at + note * 2] = word.toByte()
            rom[at + note * 2 + 1] = (word shr 8).toByte()
        }
    }

    /** `__music__` lines: `FF AABBCCDD`, flags (loop start, loop end, stop) then four channel bytes. */
    private fun readMusic(
        line: String,
        rom: ByteArray,
        at: Int,
    ) {
        val parts = line.split(' ').filter { it.isNotEmpty() }
        if (parts.size < 2 || parts[1].length < 8) return
        val flags = (hex(parts[0][0]) shl 4) or hex(parts[0][1])
        for (ch in 0 until 4) {
            val v = (hex(parts[1][ch * 2]) shl 4) or hex(parts[1][ch * 2 + 1])
            val flagBit = if (ch < 3 && (flags shr ch) and 1 != 0) 0x80 else 0
            rom[at + ch] = (v or flagBit).toByte()
        }
    }
}
