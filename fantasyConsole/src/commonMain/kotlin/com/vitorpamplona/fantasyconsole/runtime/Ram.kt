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
package com.vitorpamplona.fantasyconsole.runtime

import com.vitorpamplona.fantasyconsole.lua.LuaMemory

/** The console's 64 KiB address space and where things live in it. */
object Addr {
    const val GFX = 0x0000
    const val MAP_SHARED = 0x1000
    const val MAP = 0x2000
    const val FLAGS = 0x3000
    const val MUSIC = 0x3100
    const val SFX = 0x3200

    /** The end of cart data in ROM; code follows it there, never in RAM. */
    const val CART_DATA_END = 0x4300
    const val CART_DATA = 0x5e00

    const val DRAW_PAL = 0x5f00
    const val SCREEN_PAL = 0x5f10
    const val CLIP = 0x5f20
    const val PEN = 0x5f25
    const val CURSOR_X = 0x5f26
    const val CURSOR_Y = 0x5f27
    const val CAMERA_X = 0x5f28
    const val CAMERA_Y = 0x5f2a
    const val SCREEN_MODE = 0x5f2c
    const val DEVKIT = 0x5f2d
    const val PAUSE_SUPPRESS = 0x5f30
    const val FILL_PATTERN = 0x5f31

    /** Fill flags set by `fillp`: 1 transparency, 2 apply to sprites, 4 secondary palette everywhere. */
    const val FILL_MODE = 0x5f33

    /** 1: colors may carry a fill pattern in their high bits; 2: and may invert filled shapes. */
    const val COLOR_FILL_FLAGS = 0x5f34

    /** 0x8 draw sprite 0 in `map`/`tline`; 0x10 custom out-of-bounds reads; 0x40 no print scrolling. */
    const val MISC_FLAGS = 0x5f36
    const val TLINE_MASK_X = 0x5f38
    const val TLINE_MASK_Y = 0x5f39
    const val TLINE_OFFSET_X = 0x5f3a
    const val TLINE_OFFSET_Y = 0x5f3b
    const val RNG = 0x5f44
    const val GFX_ADDR = 0x5f54
    const val SCREEN_ADDR = 0x5f55
    const val MAP_ADDR = 0x5f56
    const val MAP_WIDTH = 0x5f57
    const val OOB_SGET = 0x5f59
    const val OOB_MGET = 0x5f5a
    const val OOB_PGET = 0x5f5b
    const val SECONDARY_PAL = 0x5f60
    const val BTNP_DELAY = 0x5f5c
    const val BTNP_INTERVAL = 0x5f5d
    const val SCREEN = 0x6000
}

class Ram : LuaMemory {
    val bytes = ByteArray(0x10000)

    operator fun get(addr: Int): Int = bytes[addr and 0xffff].toInt() and 0xff

    operator fun set(
        addr: Int,
        value: Int,
    ) {
        bytes[addr and 0xffff] = value.toByte()
    }

    override fun peek(addr: Int): Int = get(addr)

    /** Signed little-endian 16 bits, as `peek2` and `%` read them. */
    override fun peek2(addr: Int): Int = (get(addr) or (get(addr + 1) shl 8)).toShort().toInt()

    /** Little-endian 32 bits: the raw pattern of a 16:16 number. */
    override fun peek4(addr: Int): Int = get(addr) or (get(addr + 1) shl 8) or (get(addr + 2) shl 16) or (get(addr + 3) shl 24)

    fun poke2(
        addr: Int,
        value: Int,
    ) {
        set(addr, value)
        set(addr + 1, value shr 8)
    }

    fun poke4(
        addr: Int,
        value: Int,
    ) {
        set(addr, value)
        set(addr + 1, value shr 8)
        set(addr + 2, value shr 16)
        set(addr + 3, value shr 24)
    }

    fun copy(
        dest: Int,
        src: Int,
        length: Int,
    ) {
        if (length <= 0) return
        if (dest in 0..0xffff && src in 0..0xffff && dest + length <= 0x10000 && src + length <= 0x10000) {
            bytes.copyInto(bytes, dest, src, src + length)
        } else {
            val tmp = ByteArray(length) { bytes[(src + it) and 0xffff] }
            for (i in 0 until length) bytes[(dest + i) and 0xffff] = tmp[i]
        }
    }

    fun fill(
        dest: Int,
        value: Int,
        length: Int,
    ) {
        for (i in 0 until length) bytes[(dest + i) and 0xffff] = value.toByte()
    }
}
