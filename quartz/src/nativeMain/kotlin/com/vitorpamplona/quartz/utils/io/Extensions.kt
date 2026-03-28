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
package com.vitorpamplona.quartz.utils.io
// Credits: skolson, from https://github.com/skolson/KmpIO

/**
 * Simple extension to translate a ByteArray to a hex string
 * @param startIndex index in array to start, defaults to zero
 * @param length number of bytes to turn to hex
 * @return String of size [2 * length], all lower case
 * @throws IndexOutOfBoundsException if argument(s) specified are wrong
 */
fun ByteArray.toHex(
    startIndex: Int = 0,
    length: Int = size,
): String {
    val hexChars = "0123456789abcdef"
    val result = StringBuilder(length * 2)
    for (i in startIndex until startIndex + length) {
        result.append(hexChars[(this[i].toInt() and 0xF0).ushr(4)])
        result.append(hexChars[this[i].toInt() and 0x0F])
    }
    return result.toString()
}

/**
 * Convenience method, forces a Byte to an Int while stripping all sign bits.  the Byte becomes
 * the LSB of the Int produced.  For example, byte of 0xFF becomes 0x000000FF or 255 as the result Int.
 */
infix fun ByteArray.toPosInt(index: Int): Int = this[index].toInt() and 0xFF

/**
 * Convenience method, forces a Byte to an UInt while stripping all sign bits.  the Byte becomes
 * the LSB of the Int produced.  For example, byte of 0xFF becomes 0x000000FF or 255 as the result Int.
 */
infix fun ByteArray.toPosUInt(index: Int): UInt = this[index].toUInt() and 0xFFu

/**
 * Convenience method, forces a UByte to an Int while stripping all sign bits.  the Byte becomes
 * the LSB of the Int produced.  For example, byte of 0xFF becomes 0x000000FF or 255 as the result Int.
 */
infix fun UByteArray.toPosInt(index: Int): Int = this[index].toInt() and 0xFF

/**
 * Convenience method, forces a Byte to an UInt while stripping all sign bits.  the Byte becomes
 * the LSB of the Int produced.  For example, byte of 0xFF becomes 0x000000FF or 255 as the result Int.
 */
infix fun UByteArray.toPosUInt(index: Int): UInt = this[index].toUInt() and 0xFFu

/**
 * Convenience method, forces a Byte to a Long while stripping all sign bits.  the Byte becomes
 * the LSB of the Long produced.  For example, byte of 0xFF becomes 0x00000000000000FF or 255 as
 * the result Long.
 */
infix fun ByteArray.toPosLong(index: Int): Long = this[index].toLong() and 0xFF

/**
 * Convenience method, forces a Byte to a ULong while stripping all sign bits.  the Byte becomes
 * the LSB of the Long produced.  For example, byte of 0xFF becomes 0x00000000000000FF or 255 as
 * the result Long.
 */
infix fun ByteArray.toPosULong(index: Int): ULong = this[index].toULong() and 0xFFu

/**
 * Convenience method, forces a UByte to a Long while stripping all sign bits.  the Byte becomes
 * the LSB of the Long produced.  For example, byte of 0xFF becomes 0x00000000000000FF or 255 as
 * the result Int.
 */
infix fun UByteArray.toPosLong(index: Int): Long = this[index].toLong() and 0xFF

/**
 * Convenience method, forces a UByte to a ULong while using only the LSB.
 * For example, byte of 0xFF becomes 0x00000000000000FF or 255 as the result ULong.
 */
infix fun UByteArray.toPosULong(index: Int): ULong = this[index].toULong() and 0xFFu

// These endian-aware extensions functions assist with retrieving Short, UShort, Int, UInt, Long, Ulong, Float,
// and Double values from ByteArray and UByteArray. For some reason Kotlin only offers these for Little
// Endian (you have to do your own reverse() for BigEndian) and only in Kotlin Native.

/**
 * Change one byte into an Int with toPosInt, then Binary Shift left the specified number of times.
 * @param index of byte to change to an Int.
 * @param shift number of times value is binary shifted left.
 * @return resulting Int
 */
fun ByteArray.toIntShl(
    index: Int,
    shift: Int = 0,
): Int = this toPosInt index shl shift

/**
 * Change one byte into an Int with toPosInt, then Binary Shift left the specified number of times.
 * @param index of byte to change to an Int.
 * @param shift number of times value is binary shifted left.
 * @return result as UInt
 */
fun ByteArray.toUIntShl(
    index: Int,
    shift: Int = 0,
): UInt = this toPosUInt index shl shift

/**
 * Change one byte into a Long, after the byte value is Binary Shifted left the specified number of times.
 * @param index of byte to change to an Long.
 * @param shift number of times value is binary shifted left.
 * @return resulting Long
 */
fun ByteArray.toLongShl(
    index: Int,
    shift: Int = 0,
): Long = this toPosLong index shl shift

/**
 * Change one byte into a ULong, after the byte value is Binary Shifted left the specified number of times.
 * @param index of byte to change to an Long.
 * @param shift number of times value is binary shifted left.
 * @return resulting ULong
 */
fun ByteArray.toULongShl(
    index: Int,
    shift: Int = 0,
): ULong = this toPosULong index shl shift

/**
 * starting at the specified index, change bytes at index and index+1 to a Short. Both LittleEndian
 * and BigEndian encoding schemes are supported.
 * @param index where two bytes to be converted to short start
 * @param littleEndian defaults to true for LittleEndian, or false for BigEndian
 * @return the resulting Short
 */
fun ByteArray.getShortAt(
    index: Int,
    littleEndian: Boolean = true,
): Short =
    if (littleEndian) {
        (toIntShl(index + 1, 8) or toIntShl(index)).toShort()
    } else {
        (toIntShl(index, 8) or toIntShl(index + 1)).toShort()
    }

/**
 * starting at the specified index, change bytes at index and index+1 to a UShort. Both LittleEndian
 * and BigEndian encoding schemes are supported.
 * @param index where two bytes to be converted to UShort start
 * @param littleEndian defaults to true for LittleEndian, or false for BigEndian
 * @return the resulting UShort
 */
fun ByteArray.getUShortAt(
    index: Int,
    littleEndian: Boolean = true,
): UShort =
    if (littleEndian) {
        (toUIntShl(index + 1, 8) or toUIntShl(index)).toUShort()
    } else {
        (toUIntShl(index, 8) or toUIntShl(index + 1)).toUShort()
    }

/**
 * starting at the specified index, change bytes at index, index+1, index+2, and index+3 to an Int.
 * Both LittleEndian and BigEndian encoding schemes are supported.
 * @param index where four bytes to be converted to Int start
 * @param littleEndian defaults to true for LittleEndian, or false for BigEndian
 * @return the resulting Int
 */
fun ByteArray.getIntAt(
    index: Int,
    littleEndian: Boolean = true,
): Int =
    if (littleEndian) {
        toIntShl(index + 3, 24) or
            toIntShl(index + 2, 16) or
            toIntShl(index + 1, 8) or
            toIntShl(index)
    } else {
        toIntShl(index, 24) or
            toIntShl(index + 1, 16) or
            toIntShl(index + 2, 8) or
            toIntShl(index + 3)
    }

/**
 * starting at the specified index, change bytes at index, index+1, index+2, and index+3 to an UInt.
 * Both LittleEndian and BigEndian encoding schemes are supported.
 * @param index where four bytes to be converted to UInt start
 * @param littleEndian defaults to true for LittleEndian, or false for BigEndian
 * @return the resulting UInt
 */
fun ByteArray.getUIntAt(
    index: Int,
    littleEndian: Boolean = true,
): UInt =
    if (littleEndian) {
        toUIntShl(index + 3, 24) or
            toUIntShl(index + 2, 16) or
            toUIntShl(index + 1, 8) or
            toUIntShl(index)
    } else {
        toUIntShl(index, 24) or
            toUIntShl(index + 1, 16) or
            toUIntShl(index + 2, 8) or
            toUIntShl(index + 3)
    }

/**
 * Starting at the specified index, change bytes at [index..index+7] to a Long.
 * Both LittleEndian and BigEndian encoding schemes are supported.
 * @param index where eight bytes to be converted to Long start
 * @param littleEndian defaults to true for LittleEndian, or false for BigEndian
 * @return the resulting Long
 */
fun ByteArray.getLongAt(
    index: Int,
    littleEndian: Boolean = true,
): Long =
    if (littleEndian) {
        toLongShl(index + 7, 56) or
            toLongShl(index + 6, 48) or
            toLongShl(index + 5, 40) or
            toLongShl(index + 4, 32) or
            toLongShl(index + 3, 24) or
            toLongShl(index + 2, 16) or
            toLongShl(index + 1, 8) or
            toLongShl(index)
    } else {
        toLongShl(index, 56) or
            toLongShl(index + 1, 48) or
            toLongShl(index + 2, 40) or
            toLongShl(index + 3, 32) or
            toLongShl(index + 4, 24) or
            toLongShl(index + 5, 16) or
            toLongShl(index + 6, 8) or
            toLongShl(index + 7)
    }

/**
 * Starting at the specified index, change bytes at [index..index+7] to a ULong.
 * Both LittleEndian and BigEndian encoding schemes are supported.
 * @param index where eight bytes to be converted to ULong start
 * @param littleEndian defaults to true for LittleEndian, or false for BigEndian
 * @return the resulting ULong
 */
fun ByteArray.getULongAt(
    index: Int,
    littleEndian: Boolean = true,
): ULong =
    if (littleEndian) {
        toULongShl(index + 7, 56) or
            toULongShl(index + 6, 48) or
            toULongShl(index + 5, 40) or
            toULongShl(index + 4, 32) or
            toULongShl(index + 3, 24) or
            toULongShl(index + 2, 16) or
            toULongShl(index + 1, 8) or
            toULongShl(index)
    } else {
        toULongShl(index, 56) or
            toULongShl(index + 1, 48) or
            toULongShl(index + 2, 40) or
            toULongShl(index + 3, 32) or
            toULongShl(index + 4, 24) or
            toULongShl(index + 5, 16) or
            toULongShl(index + 6, 8) or
            toULongShl(index + 7)
    }

/**
 * Simple extension to translate a ByteArray to a hex string
 * @param startIndex index in array to start, defaults to zero
 * @param length number of bytes to turn to hex
 * @return String of size [2 * length], all lower case
 * @throws IndexOutOfBoundsException if argument(s) specified are wrong
 */
fun UByteArray.toHex(
    startIndex: Int = 0,
    length: Int = size,
): String {
    val hexChars = "0123456789abcdef"
    val result = StringBuilder(length * 2)
    for (i in startIndex until startIndex + length) {
        result.append(hexChars[(this[i].toInt() and 0xF0).ushr(4)])
        result.append(hexChars[this[i].toInt() and 0x0F])
    }
    return result.toString()
}

/**
 * Change one byte into an Int, after the byte value is Binary Shifted left the specified number of times.
 * @param index of byte to change to an Int.
 * @param shift number of times value is binary shifted left.
 * @return resulting Int
 */
fun UByteArray.toIntShl(
    index: Int,
    shift: Int = 0,
): Int = this toPosInt index shl shift

/**
 * Change one byte into an UInt, after the byte value is Binary Shifted left the specified number of times.
 * @param index of byte to change to an Int.
 * @param shift number of times value is binary shifted left.
 * @return resulting UInt
 */
fun UByteArray.toUIntShl(
    index: Int,
    shift: Int = 0,
): UInt = this toPosUInt index shl shift

/**
 * Change one byte into a Long, after the byte value is Binary Shifted left the specified number of times.
 * @param index of byte to change to an Long.
 * @param shift number of times value is binary shifted left.
 * @return resulting Long
 */
fun UByteArray.toLongShl(
    index: Int,
    shift: Int = 0,
): Long = this toPosLong index shl shift

/**
 * Change one byte into a ULong, after the byte value is Binary Shifted left the specified number of times.
 * @param index of byte to change to an Long.
 * @param shift number of times value is binary shifted left.
 * @return resulting ULong
 */
fun UByteArray.toULongShl(
    index: Int,
    shift: Int = 0,
): ULong = this toPosULong index shl shift

/**
 * starting at the specified index, change bytes at index and index+1 to a Short. Both LittleEndian
 * and BigEndian encoding schemes are supported.
 * @param index where two bytes to be converted to short start
 * @param littleEndian defaults to true for LittleEndian, or false for BigEndian
 * @return the resulting Short
 */
fun UByteArray.getShortAt(
    index: Int,
    littleEndian: Boolean = true,
): Short =
    if (littleEndian) {
        (toIntShl(index + 1, 8) or toIntShl(index)).toShort()
    } else {
        (toIntShl(index, 8) or toIntShl(index + 1)).toShort()
    }

/**
 * starting at the specified index, change bytes at index and index+1 to a UShort. Both LittleEndian
 * and BigEndian encoding schemes are supported.
 * @param index where two bytes to be converted to UShort start
 * @param littleEndian defaults to true for LittleEndian, or false for BigEndian
 * @return the resulting UShort
 */
fun UByteArray.getUShortAt(
    index: Int,
    littleEndian: Boolean = true,
): UShort =
    if (littleEndian) {
        (toUIntShl(index + 1, 8) or toUIntShl(index)).toUShort()
    } else {
        (toUIntShl(index, 8) or toUIntShl(index + 1)).toUShort()
    }

/**
 * starting at the specified index, change bytes at index, index+1, index+2, and index+3 to an Int.
 * Both LittleEndian and BigEndian encoding schemes are supported.
 * @param index where four bytes to be converted to Int start
 * @param littleEndian defaults to true for LittleEndian, or false for BigEndian
 * @return the resulting Int
 */
fun UByteArray.getIntAt(
    index: Int,
    littleEndian: Boolean = true,
): Int =
    if (littleEndian) {
        toIntShl(index + 3, 24) or
            toIntShl(index + 2, 16) or
            toIntShl(index + 1, 8) or
            toIntShl(index)
    } else {
        toIntShl(index, 24) or
            toIntShl(index + 1, 16) or
            toIntShl(index + 2, 8) or
            toIntShl(index + 3)
    }

/**
 * starting at the specified index, change bytes at index, index+1, index+2, and index+3 to an UInt.
 * Both LittleEndian and BigEndian encoding schemes are supported.
 * @param index where four bytes to be converted to UInt start
 * @param littleEndian defaults to true for LittleEndian, or false for BigEndian
 * @return the resulting UInt
 */
fun UByteArray.getUIntAt(
    index: Int,
    littleEndian: Boolean = true,
): UInt =
    if (littleEndian) {
        toUIntShl(index + 3, 24) or
            toUIntShl(index + 2, 16) or
            toUIntShl(index + 1, 8) or
            toUIntShl(index)
    } else {
        toUIntShl(index, 24) or
            toUIntShl(index + 1, 16) or
            toUIntShl(index + 2, 8) or
            toUIntShl(index + 3)
    }

/**
 * Starting at the specified index, change bytes at [index..index+7] to a Long.
 * Both LittleEndian and BigEndian encoding schemes are supported.
 * @param index where eight bytes to be converted to Long start
 * @param littleEndian defaults to true for LittleEndian, or false for BigEndian
 * @return the resulting Long
 */
fun UByteArray.getLongAt(
    index: Int,
    littleEndian: Boolean = true,
): Long =
    if (littleEndian) {
        toLongShl(index + 7, 56) or
            toLongShl(index + 6, 48) or
            toLongShl(index + 5, 40) or
            toLongShl(index + 4, 32) or
            toLongShl(index + 3, 24) or
            toLongShl(index + 2, 16) or
            toLongShl(index + 1, 8) or
            toLongShl(index)
    } else {
        toLongShl(index, 56) or
            toLongShl(index + 1, 48) or
            toLongShl(index + 2, 40) or
            toLongShl(index + 3, 32) or
            toLongShl(index + 4, 24) or
            toLongShl(index + 5, 16) or
            toLongShl(index + 6, 8) or
            toLongShl(index + 7)
    }

/**
 * Starting at the specified index, change bytes at [index..index+7] to a ULong.
 * Both LittleEndian and BigEndian encoding schemes are supported.
 * @param index where eight bytes to be converted to ULong start
 * @param littleEndian defaults to true for LittleEndian, or false for BigEndian
 * @return the resulting ULong
 */
fun UByteArray.getULongAt(
    index: Int,
    littleEndian: Boolean = true,
): ULong =
    if (littleEndian) {
        toULongShl(index + 7, 56) or
            toULongShl(index + 6, 48) or
            toULongShl(index + 5, 40) or
            toULongShl(index + 4, 32) or
            toULongShl(index + 3, 24) or
            toULongShl(index + 2, 16) or
            toULongShl(index + 1, 8) or
            toULongShl(index)
    } else {
        toULongShl(index, 56) or
            toULongShl(index + 1, 48) or
            toULongShl(index + 2, 40) or
            toULongShl(index + 3, 32) or
            toULongShl(index + 4, 24) or
            toULongShl(index + 5, 16) or
            toULongShl(index + 6, 8) or
            toULongShl(index + 7)
    }
