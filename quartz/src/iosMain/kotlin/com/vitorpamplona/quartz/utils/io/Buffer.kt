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

@Suppress("ktlint")
abstract class Buffer<Element, Array> internal constructor(
    markPosition: Int,
    pos: Int,
    lim: Int,
    cap: Int,
    var order: ByteOrder = ByteOrder.LittleEndian,
) {
    enum class ByteOrder { LittleEndian, BigEndian }

    // Invariants: mark <= position <= limit <= capacity
    var position = pos
        /**
         * Sets this buffer's position.  If the mark is defined and larger than the
         * new position then it is discarded.
         *
         * @param newPosition
         * The new position value; must be non-negative
         * and no larger than the current limit
         *
         * @throws IllegalArgumentException
         * If the preconditions on <tt>newPosition</tt> do not hold
         */
        set(newPosition) {
            if (newPosition !in 0..limit) {
                throw IllegalArgumentException("Position $newPosition exceeds limit:$limit")
            }
            field = newPosition
            if (mark > position) mark = NO_MARK
        }

    var limit = lim
        /**
         * Sets this buffer's limit.  If the position is larger than the new limit
         * then it is set to the new limit.  If the mark is defined and larger than
         * the new limit then it is discarded.
         *
         * @param newLimit
         * The new limit value; must be non-negative
         * and no larger than this buffer's capacity
         *
         * @return This buffer
         *
         * @throws IllegalArgumentException
         * If the preconditions on <tt>newLimit</tt> do not hold
         */
        set(newLimit) {
            if (newLimit !in 0..capacity) {
                throw IllegalArgumentException("limit $newLimit exceeds capacity $capacity")
            }
            field = newLimit
            if (position > limit) position = limit
            if (mark > limit) mark = -1
        }
    var capacity = cap
        internal set
    var mark = markPosition
        set(value) {
            if (mark < -1 || mark > position) {
                throw IllegalArgumentException("Mark $mark out of range of 0 to $position")
            }
            field = value
        }

    /**
     * Returns the number of elements between the current position and the
     * limit.
     *
     * @return The number of elements remaining in this buffer
     */
    val remaining get() = limit - position

    /**
     * Tells whether there are any elements between the current position and
     * the limit.
     *
     * @return <tt>true</tt> if, and only if, there is at least one element
     * remaining in this buffer
     */
    val hasRemaining get() = position < limit

    /**
     * Tells whether or not this buffer is read-only.
     *
     * @return <tt>true</tt> if, and only if, this buffer is read-only
     */
    abstract val isReadOnly: Boolean

    /**
     * Subclasses provide a property that get a byte from the buffer, or put a byte in the buffer.
     * Position must be incremented in both cases
     */
    abstract var byte: Element

    // The following functions are all usable by subclasses to encode/decode basic types
    // in and Endian-aware fashion. In all cases an exception is thrown if remaining() < length
    // to be read/written. Position will be incremented by the appropriate length for both gets
    // and sets.
    //
    // For some reason, the ByteArray in Kotlin Native is the only implementation that provides
    // similar function, but assumes all is Little Endian. Since these are not available in the
    // common stdlib, they are not used even for little endian.

    /**
     * Read or write a two-byte Char at the current position. Position will increment by 2.
     */
    var char: Char
        get() {
            val c =
                when (order) {
                    ByteOrder.LittleEndian ->
                        (
                            (getElementAsInt(position + 1) shl 8) or
                                getElementAsInt(position)
                        ).toChar()
                    ByteOrder.BigEndian ->
                        (
                            (getElementAsInt(position) shl 8) or
                                getElementAsInt(position + 1)
                        ).toChar()
                }
            position += 2
            return c
        }
        set(value) {
            putEndian(shortToArray(value.code.toShort()))
        }

    /**
     * Read or write a two-byte Short at the current position. Position will increment by 2.
     */
    var short: Short
        get() {
            if (remaining < SHORT_LENGTH) {
                throw IllegalArgumentException("Short requires $SHORT_LENGTH bytes. Position: $position, remaining:$remaining")
            }
            val s = getShortValue(position)
            position += SHORT_LENGTH
            return s
        }
        set(value) {
            putEndian(shortToArray(value))
        }

    /**
     * Read or write a two-byte Short at the current position. Position will increment by 2.
     */
    var ushort: UShort
        get() {
            if (remaining < SHORT_LENGTH) {
                throw IllegalArgumentException("UShort requires $SHORT_LENGTH bytes. Position: $position, remaining:$remaining")
            }
            val s = getUShortValue(position)
            position += SHORT_LENGTH
            return s
        }
        set(value) {
            putEndian(ushortToArray(value))
        }

    /**
     * Read or write a four-byte Int at the current position. Position will increment by 4.
     */
    var int: Int
        get() {
            if (remaining < INT_LENGTH) {
                throw IllegalArgumentException("Int requires $INT_LENGTH bytes. Position: $position, remaining:$remaining")
            }
            val s = getIntValue(position)
            position += INT_LENGTH
            return s
        }
        set(value) {
            putEndian(intToArray(value))
        }

    /**
     * Read or write a two-byte Short at the current position. Position will increment by 2.
     */
    var uint: UInt
        get() {
            if (remaining < INT_LENGTH) {
                throw IllegalArgumentException("UInt requires $INT_LENGTH bytes. Position: $position, remaining:$remaining")
            }
            val s = getUIntValue(position)
            position += INT_LENGTH
            return s
        }
        set(value) {
            putEndian(uintToArray(value))
        }

    /**
     * Read or write a four-byte Int at the current position. Position will increment by 4.
     */
    var long: Long
        get() {
            if (remaining < LONG_LENGTH) {
                throw IllegalArgumentException("Long requires $LONG_LENGTH bytes. Position: $position, remaining:$remaining")
            }
            val s = getLongValue(position)
            position += LONG_LENGTH
            return s
        }
        set(value) {
            putEndian(longToArray(value))
        }

    /**
     * Read or write a four-byte Int at the current position. Position will increment by 4.
     */
    var ulong: ULong
        get() {
            if (remaining < LONG_LENGTH) {
                throw IllegalArgumentException("ULong requires $LONG_LENGTH bytes. Position: $position, remaining:$remaining")
            }
            val s = getULongValue(position)
            position += LONG_LENGTH
            return s
        }
        set(value) {
            putEndian(ulongToArray(value))
        }

    var float: Float
        get() = Float.fromBits(this.int)
        set(value) {
            int = value.toBits()
        }

    var double: Double
        get() = Double.Companion.fromBits(this.long)
        set(value) {
            long = value.toBits()
        }

    // Creates a new buffer with the given mark, position, limit, and capacity,
    // after checking invariants.
    init {
        if (cap < 0) throw IllegalArgumentException("Negative capacity: $cap")
        capacity = cap
        limit = lim
        position = pos
        if (mark >= 0) {
            if (mark > pos) {
                throw IllegalArgumentException("mark > position: ($mark > $pos)")
            }
            this.mark = mark
        }
    }

    /**
     * Clears this buffer.  The position is set to zero, the limit is set to
     * the capacity, and the mark is discarded.
     *
     *
     *  Invoke this method before using a sequence of channel-read or
     * *put* operations to fill this buffer.  For example:
     *
     * <blockquote><pre>
     * buf.clear();     // Prepare buffer for reading
     * in.read(buf);    // Read data</pre></blockquote>
     *
     *
     *  This method does not actually erase the data in the buffer, but it
     * is named as if it did because it will most often be used in situations
     * in which that might as well be the case.
     *
     * @return This buffer
     */
    open fun clear() {
        position = 0
        limit = capacity
        mark = NO_MARK
    }

    /**
     * Increase size of buffer. Capacity and content are increased. Position is unchanged. if limit
     * currently set to capacity, it will be set to the new capacity
     * @param addCapacity bytes to add. Unsigned as can't be used to shrink.
     */
    abstract fun expand(addCapacity: UInt)

    /**
     * Flips this buffer.  The limit is set to the current position and then
     * the position is set to zero.  If the mark is defined then it is
     * discarded.
     *
     *
     *  After a sequence of channel-read or *put* operations, invoke
     * this method to prepare for a sequence of channel-write or relative
     * *get* operations.  For example:
     *
     * <blockquote><pre>
     * buf.put(magic);    // Prepend header
     * in.read(buf);      // Read data into rest of buffer
     * buf.flip();        // Flip buffer
     * out.write(buf);    // Write header + data to channel</pre></blockquote>
     *
     *
     *  This method is often used in conjunction with the compact method when transferring data from
     * one place to another.
     *
     * @return This buffer
     */
    open fun flip(): Buffer<Element, Array> {
        limit = position
        position = 0
        mark = NO_MARK
        return this
    }

    /**
     * given the specified index into the backing array, return the current Element. Do not change position
     */
    fun get(index: Int): Element = getElementAt(index)

    /**
     * This is used by many of the accessor properties of the various types. It must return
     * a ByteArray of length bytes, containing the bytes at the current position for the
     * specified length.  The position will be incremented by the length.
     *
     * @param length must be > 0 and <= remaining(). Defaults to remaining
     *
     * @throws IllegalArgumentException if the length is invalid
     */
    abstract fun getBytes(length: Int = remaining): Array

    /**
     * Starting at the current position, copy bytes.size from the buffer into the provided array.
     * If the size of the array is greater than [remaining], only [remaining] bytes are copied.
     * Position is increased by the number of bytes copied.
     */
    abstract fun getBytes(bytes: Array)

    /**
     * given the specified index into the backing array, return the current Element. Do not change position
     */
    abstract fun getElementAt(index: Int): Element

    abstract fun setElementAt(
        index: Int,
        element: Element,
    )

    abstract fun getElementAsInt(index: Int): Int

    abstract fun getElementAsUInt(index: Int): UInt

    abstract fun getElementAsLong(index: Int): Long

    abstract fun getElementAsULong(index: Int): ULong

    /**
     * Convenience method Sets the limit at position + length, then sets the position
     */
    fun positionLimit(
        position: Int,
        length: Int,
    ) {
        if (length < 0 || position < 0 || position + length > capacity) {
            throw IllegalArgumentException("Position: $position + length: $length = ${position + length} must be between 0 and $capacity")
        }
        limit = position + length
        this.position = position
    }

    fun positionLimit(
        position: Short,
        length: Short,
    ) {
        if (length < 0 || position < 0 || position + length > capacity) {
            throw IllegalArgumentException("Position: $position + length: $length = ${position + length} must be between 0 and $capacity")
        }
        limit = position + length
        this.position = position.toInt()
    }

    /**
     * This is used by many of the accessor properties of the various types. It must copy the
     * content of bytes into the ByteBuffer at the current position.
     * The position will be incremented by the length of the array.
     *
     * @param bytes size of the array must be > 0 and <= remaining()
     *
     * @throws IllegalArgumentException if the size is invalid
     */
    abstract fun put(bytes: Array)

    /**
     * Resets this buffer's position to the previously-marked position.
     *
     *
     *  Invoking this method neither changes nor discards the mark's
     * value.
     *
     * @return This buffer
     *
     * @throws IllegalStateException
     * If the mark has not been set
     */
    open fun reset() {
        val m = mark
        if (m < 0) throw IllegalStateException("Mark:$m must be non-negative")
        position = m
    }

    fun resetMark() {
        mark = NO_MARK
    }

    /**
     * Rewinds this buffer.  The position is set to zero and the mark is
     * discarded.
     *
     *
     *  Invoke this method before a sequence of channel-write or *get*
     * operations, assuming that the limit has already been set
     * appropriately.  For example:
     *
     * <blockquote><pre>
     * out.write(buf);    // Write remaining data
     * buf.rewind();      // Rewind buffer
     * buf.get(array);    // Copy data into array</pre></blockquote>
     *
     * @return This buffer
     */
    open fun rewind() {
        position = 0
        mark = -1
    }

    /**
     * Make a new ByteBuffer containing the [remaining bytes] of this one. Length can be overrdiden to
     * a shorter value than the default [remaining]. Position is unaffected
     * @param length defaults to [remaining]. can be between 1 and [remaining]
     */
    abstract fun slice(length: Int = remaining): ByteBufferBase<Element, Array>

    // -- Package-private methods for bounds checking, etc. --

    /**
     * Checks the current position against the limit, throwing a [ ] if it is not smaller than the limit, and then
     * increments the position.
     *
     * @return The current position value, before it is incremented
     */
    fun nextGetIndex(): Int {
        if (position >= limit) throw IllegalStateException("Position:$position exceeds Limit:$limit")
        return position++
    }

    fun nextGetIndex(nb: Int): Int {
        if (limit - position < nb) throw IllegalStateException()
        val p = position
        position += nb
        return p
    }

    /**
     * Checks the current position against the limit, and then increments the position.
     *
     * @return The current position value, before it is incremented
     * @throws IllegalStateException if position exceeds limit
     */
    fun nextPutIndex(): Int {
        if (position >= limit) throw IllegalStateException("Position:$position exceeds Limit:$limit")
        return position++
    }

    /**
     * Checks the current position pluss the specified offset against the limit, and then increments the position.
     *
     * @param length position will be increased by
     * @return The current position value, before it is incremented
     * @throws IllegalStateException if increased position will exceed limit
     */
    fun nextPutIndex(length: Int): Int {
        if (limit - position < length || position < 0) {
            throw IllegalStateException("Limit:$limit minus position:$position less than $length")
        }
        val p = position
        position += length
        return p
    }

    /**
     * Checks the given index against the limit, throwing an [ ] if it is not smaller than the limit
     * or is smaller than zero.
     */
    fun checkIndex(i: Int): Int { // package-private
        if (i < 0 || i >= limit) {
            throw IndexOutOfBoundsException(
                "index=$i out of bounds (limit=$limit)",
            )
        }
        return i
    }

    fun checkIndex(
        i: Int,
        nb: Int,
    ): Int { // package-private
        if (i < 0 || nb > limit - i) {
            throw IndexOutOfBoundsException(
                "index=$i out of bounds (limit=$limit, nb=$nb)",
            )
        }
        return i
    }

    fun markValue(): Int { // package-private
        return mark
    }

    fun truncate() { // package-private
        mark = NO_MARK
        position = 0
        limit = 0
        capacity = 0
    }

    internal fun discardMark() { // package-private
        mark = -1
    }

    private fun getShortValue(index: Int): Short =
        when (order) {
            ByteOrder.LittleEndian -> ((getElementAsInt(index + 1) shl 8) or getElementAsInt(index)).toShort()
            ByteOrder.BigEndian -> ((getElementAsInt(index) shl 8) or getElementAsInt(index + 1)).toShort()
        }

    fun getUShortValue(index: Int): UShort =
        when (order) {
            ByteOrder.LittleEndian ->
                (
                    (getElementAsUInt(index + 1) shl 8) or
                        getElementAsUInt(index)
                ).toUShort()
            ByteOrder.BigEndian ->
                (
                    (getElementAsUInt(index) shl 8) or
                        getElementAsUInt(index + 1)
                ).toUShort()
        }

    private fun getIntValue(index: Int): Int =
        when (order) {
            ByteOrder.LittleEndian ->
                (getElementAsInt(index + 3) shl 24) or
                    (getElementAsInt(index + 2) shl 16) or
                    (getElementAsInt(index + 1) shl 8) or
                    getElementAsInt(index)
            ByteOrder.BigEndian ->
                (getElementAsInt(index) shl 24) or
                    (getElementAsInt(index + 1) shl 16) or
                    (getElementAsInt(index + 2) shl 8) or
                    getElementAsInt(index + 3)
        }

    private fun getUIntValue(index: Int): UInt =
        when (order) {
            ByteOrder.LittleEndian ->
                (getElementAsUInt(index + 3) shl 24) or
                    (getElementAsUInt(index + 2) shl 16) or
                    (getElementAsUInt(index + 1) shl 8) or
                    getElementAsUInt(index)
            ByteOrder.BigEndian ->
                (getElementAsUInt(index) shl 24) or
                    (getElementAsUInt(index + 1) shl 16) or
                    (getElementAsUInt(index + 2) shl 8) or
                    getElementAsUInt(index + 3)
        }

    fun getLongValue(index: Int): Long =
        when (order) {
            ByteOrder.LittleEndian -> (
                (getElementAsLong(index + 7) shl 56)
                    or (getElementAsLong(index + 6) shl 48)
                    or (getElementAsLong(index + 5) shl 40)
                    or (getElementAsLong(index + 4) shl 32)
                    or (getElementAsLong(index + 3) shl 24)
                    or (getElementAsLong(index + 2) shl 16)
                    or (getElementAsLong(index + 1) shl 8)
                    or getElementAsLong(index)
            )
            ByteOrder.BigEndian -> (
                (getElementAsLong(index) shl 56)
                    or (getElementAsLong(index + 1) shl 48)
                    or (getElementAsLong(index + 2) shl 40)
                    or (getElementAsLong(index + 3) shl 32)
                    or (getElementAsLong(index + 4) shl 24)
                    or (getElementAsLong(index + 5) shl 16)
                    or (getElementAsLong(index + 6) shl 8)
                    or getElementAsLong(index + 7)
            )
        }

    private fun getULongValue(index: Int): ULong =
        when (order) {
            ByteOrder.LittleEndian -> (
                (getElementAsULong(index + 7) shl 56)
                    or (getElementAsULong(index + 6) shl 48)
                    or (getElementAsULong(index + 5) shl 40)
                    or (getElementAsULong(index + 4) shl 32)
                    or (getElementAsULong(index + 3) shl 24)
                    or (getElementAsULong(index + 2) shl 16)
                    or (getElementAsULong(index + 1) shl 8)
                    or getElementAsULong(index)
            )
            ByteOrder.BigEndian -> (
                (getElementAsULong(index) shl 56)
                    or (getElementAsULong(index + 1) shl 48)
                    or (getElementAsULong(index + 2) shl 40)
                    or (getElementAsULong(index + 3) shl 32)
                    or (getElementAsULong(index + 4) shl 24)
                    or (getElementAsULong(index + 5) shl 16)
                    or (getElementAsULong(index + 6) shl 8)
                    or getElementAsULong(index + 7)
            )
        }

    /**
     * Helper for property accessors that care about endian encoding.
     *
     * @param bytes must be in MSB to LSB order.  BigEndian will put array unchanged into buffer.
     *  LittleEndian will put bytes in LSB to MSB order in buffer.
     *
     */
    abstract fun putEndian(bytes: Array)

    abstract fun shortToArray(short: Short): Array

    abstract fun ushortToArray(ushort: UShort): Array

    abstract fun intToArray(int: Int): Array

    abstract fun uintToArray(int: UInt): Array

    abstract fun longToArray(long: Long): Array

    abstract fun ulongToArray(uLong: ULong): Array

    companion object {
        protected const val NO_MARK = -1
        protected const val SHORT_LENGTH = 2
        protected const val INT_LENGTH = 4
        protected const val LONG_LENGTH = 8

        fun checkBounds(
            off: Int,
            len: Int,
            size: Int,
        ) { // package-private
            if (off or len or off + len or size - (off + len) < 0) {
                throw IndexOutOfBoundsException(
                    "off=$off, len=$len out of bounds (size=$size)",
                )
            }
        }
    }
}
