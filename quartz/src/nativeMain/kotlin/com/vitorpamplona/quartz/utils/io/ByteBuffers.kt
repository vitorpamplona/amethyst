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

import kotlin.math.min

/**
 * Port of a subset of Java's ByteBuffer with full Endian support. This class should be replaceable
 * by an equivalent class in kotlinx-io once that library settles. This ByteBuffer is
 * implemented on a ByteArray for simplicity
 */
abstract class ByteBufferBase<Element, Array> constructor(
    capacity: Int,
    order: ByteOrder = ByteOrder.LittleEndian,
    override val isReadOnly: Boolean = false,
) : Buffer<Element, Array>(-1, 0, capacity, capacity, order),
    Comparable<ByteBufferBase<Element, Array>> {
    abstract var buf: Array
        protected set

    private var offset = 0
    val contentBytes get() = buf

    // The following properties offer simple encode/decode operations as indicated by the ByteOrder
    // currently in effect. Properties are defined for many basic types

    /**
     * This property offers byte-level read/write. get returns the byte at the current position,
     * and increments then position by one. set changes the byte at the current position, then
     * increments the position by one.
     *
     * @throws IllegalStateException if the position is already at the limit
     */
    override var byte: Element
        get() {
            checkPosition()
            return getElementAt(position++)
        }
        set(value) {
            checkPosition()
            setElementAt(position++, value)
        }

    /**
     * Compacts this buffer&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> The bytes between the buffer's current position and its limit,
     * if any, are copied to the beginning of the buffer.  That is, the
     * byte at index <i>p</i>&nbsp;=&nbsp;<tt>position()</tt> is copied
     * to index zero, the byte at index <i>p</i>&nbsp;+&nbsp;1 is copied
     * to index one, and so forth until the byte at index
     * <tt>limit()</tt>&nbsp;-&nbsp;1 is copied to index
     * <i>n</i>&nbsp;=&nbsp;<tt>limit()</tt>&nbsp;-&nbsp;<tt>1</tt>&nbsp;-&nbsp;<i>p</i>.
     * The buffer's position is then set to <i>n+1</i> and its limit is set to
     * its capacity.  The mark, if defined, is discarded.
     *
     * <p> The buffer's position is set to the number of bytes copied,
     * rather than to zero, so that an invocation of this method can be
     * followed immediately by an invocation of another relative <i>put</i>
     * method. </p>
     *

     *
     * <p> Invoke this method after writing data from a buffer in case the
     * write was incomplete.  The following loop, for example, copies bytes
     * from one channel to another via the buffer <tt>buf</tt>:
     *
     * <blockquote><pre>{@code
     *   buf.clear();          // Prepare buffer for use
     *   while (in.read(buf) >= 0 || buf.position != 0) {
     *       buf.flip();
     *       out.write(buf);
     *       buf.compact();    // In case of partial write
     *   }
     * }</pre></blockquote>
     *
     * @return This buffer
     */
    fun compact() {
        var destIndex = 0
        for (i in position until position + limit) {
            setElementAt(destIndex++, getElementAt(i))
        }
        position = destIndex
        limit = capacity
        resetMark()
    }

    /**
     * Compares this buffer to another.
     *
     * <p> Two byte buffers are compared by comparing their sequences of
     * remaining elements lexicographically, without regard to the starting
     * position of each sequence within its corresponding buffer.
     *
     * @return A negative integer, zero, or a positive integer as this buffer
     *          is less than, equal to, or greater than the given buffer
     */
    override fun compareTo(other: ByteBufferBase<Element, Array>): Int {
        val n: Int = position + min(remaining, other.remaining)
        var i: Int = position
        var j: Int = other.position
        while (i < n) {
            val r = compareElement(getElementAt(i), other.getElementAt(j))
            if (r != 0) return r
            i++
            j++
        }
        return 0
    }

    abstract fun compareElement(
        element: Element,
        other: Element,
    ): Int

    /**
     * The contents of the current buffer are replaced with the contents of the source.  Position,
     * limit, capacity, order, and mark will all be set to same as the source.
     */
    fun copy(source: ByteBufferBase<Element, Array>) {
        buf = source.buf
        position = source.position
        limit = source.limit
        mark = source.mark
        order = source.order
    }

    /**
     * Similar to [ByteArray] copyInto
     * @param destination Array to receive copy
     * @param destinationOffset index into destination where copy will start
     * @param startIndex index in source array where copy will start
     * @param endIndex index in source that copy will end, exclusive. [endIndex - startIndex] will
     * be number of bytes copied.
     */
    abstract fun copyInto(
        destination: Array,
        destinationOffset: Int = 0,
        startIndex: Int = 0,
        endIndex: Int,
    )

    /**
     * Starting at the current position, copy bytes into the specified destination array. Increment
     * position by the number of bytes retrieved.
     *
     * @param destination
     *      The array with sufficient capacity into which bytes will be written
     * @param destinationOffset
     *      The offset into the destination at which the copy will start
     * @param length
     *      The number of bytes to be copied
     * @return This buffer
     */
    fun fillArray(
        destination: Array,
        destinationOffset: Int = 0,
        length: Int,
    ) {
        checkBounds(destinationOffset, length, length)
        if (length > remaining) {
            throw IllegalStateException("Copying length:$length is more than remaining:$remaining")
        }
        copyInto(destination, destinationOffset, position, position + length)
        position += length
    }

    /**
     * <p> This method transfers the bytes remaining in the given source
     * buffer into this buffer.  If there are more bytes remaining in the
     * source buffer than in this buffer, that is, if
     * <tt>src.remaining()</tt>&nbsp;<tt>&gt;</tt>&nbsp;<tt>remaining()</tt>,
     * then no bytes are transferred and an IllegalStateException is thrown
     * </p>
     *
     * <p> Otherwise, this method copies
     * <i>n</i>&nbsp;=&nbsp;<tt>src.remaining()</tt> bytes from the given
     * buffer into this buffer, starting at each buffer's current position.
     * The positions of both buffers are then incremented by <i>n</i>.
     */
    open fun put(source: ByteBufferBase<Element, Array>) {
        if (source == this) {
            throw IllegalArgumentException("Cannot copy ByteBuffer to itself")
        }
        val sourceBytes = source.remaining
        if (sourceBytes > remaining) {
            throw IllegalArgumentException("Remaining source bytes:$sourceBytes exceeds destination remaining:$remaining")
        }

        source.fillArray(buf, position, sourceBytes)
        position += sourceBytes
    }

    /**
     * The following are all private functions
     */
    private fun checkPosition(forLength: Int = 1) {
        if (position + forLength > limit) {
            throw IllegalStateException()
        }
    }
}

/**
 * Enhance byte array implementing the Buffer interface which provides position/limit/remaining/capacity tracking, and
 * support for either little endian or big endian encoding of basic numeric types. See [ByteBufferBase] and [Buffer] for
 * more details.
 * @param capacity starting size of buffer in bytes
 * @param order defaults to little endian encoding of numeric types
 * @param isReadOnly true if for some reason opeations that change content should throw an exception
 * @param buf defaults to a ByteArray of specified [capacity]
 */
class ByteBuffer(
    capacity: Int,
    order: ByteOrder = ByteOrder.LittleEndian,
    isReadOnly: Boolean = false,
    override var buf: ByteArray = ByteArray(capacity),
) : ByteBufferBase<Byte, ByteArray>(capacity, order, isReadOnly) {
    /**
     * Construct a ByteBuffer from an existing ByteArray
     * @param bytes becomes the buffer content (not a copy). capacity is set to the ByteArray size, position is set to
     * zero
     * @param order defaults to little endian encoding of numeric types
     */
    constructor(bytes: ByteArray, order: ByteOrder = ByteOrder.LittleEndian) :
        this(bytes.size, order, false, bytes)

    override fun flip(): ByteBuffer {
        super.flip()
        return this
    }

    override fun getElementAt(index: Int): Byte = buf[index]

    override fun setElementAt(
        index: Int,
        element: Byte,
    ) {
        buf[index] = element
    }

    /**
     * gets one byte at the current position without changing the position. The byte is treated as
     * unsigned, so the returned Int will always be positive.
     * @param index indicates which element in the current array to retrieve
     * @return INt will not have it's high order bits set.
     */
    override fun getElementAsInt(index: Int): Int = buf toPosInt index

    /**
     * gets one byte at the current position without changing the position, and return a UInt.
     * The byte is treated as unsigned.
     * @param index indicates which element in the current array to retrieve
     * @return UINt will not have it's high order bits set.
     */
    override fun getElementAsUInt(index: Int): UInt = buf toPosUInt index

    /**
     * gets one byte at the current position without changing the position. The byte is treated as
     * unsigned, so the returned Long will always be positive.
     * @param index indicates which element in the current array to retrieve
     * @return Long will not have it's high order bits set.
     */
    override fun getElementAsLong(index: Int): Long = buf toPosLong index

    /**
     * gets one byte at the current position without changing the position. The byte is treated as
     * unsigned, so the returned ULong will always be positive.
     * @param index indicates which element in the current array to retrieve
     * @return ULong will not have it's high order bits set.
     */
    override fun getElementAsULong(index: Int): ULong = buf toPosULong index

    override fun getBytes(length: Int): ByteArray {
        val l = min(remaining, length)
        val a = ByteArray(l)
        buf.copyInto(a, 0, position, position + l)
        position += l
        return a
    }

    override fun getBytes(bytes: ByteArray) {
        val l = min(remaining, bytes.size)
        buf.copyInto(bytes, 0, position, position + l)
        position += l
    }

    override fun put(bytes: ByteArray) {
        val l = min(remaining, bytes.size)
        bytes.copyInto(buf, position, 0, l)
        position += l
    }

    override fun putEndian(bytes: ByteArray) {
        if (order == ByteOrder.LittleEndian) {
            bytes.reverse()
        }
        put(bytes)
    }

    override fun shortToArray(short: Short): ByteArray =
        byteArrayOf(
            (short.toInt() shr 8).toByte(),
            short.toByte(),
        )

    override fun ushortToArray(ushort: UShort): ByteArray =
        byteArrayOf(
            (ushort.toUInt() shr 8).toByte(),
            ushort.toByte(),
        )

    override fun intToArray(int: Int): ByteArray =
        byteArrayOf(
            (int shr 24 and 0xff).toByte(),
            (int shr 16 and 0xff).toByte(),
            (int shr 8 and 0xff).toByte(),
            (int and 0xff).toByte(),
        )

    override fun uintToArray(int: UInt): ByteArray =
        byteArrayOf(
            (int shr 24 and 0xffu).toByte(),
            (int shr 16 and 0xffu).toByte(),
            (int shr 8 and 0xffu).toByte(),
            (int and 0xffu).toByte(),
        )

    override fun longToArray(long: Long): ByteArray =
        byteArrayOf(
            (long shr 56 and 0xff).toByte(),
            (long shr 48 and 0xff).toByte(),
            (long shr 40 and 0xff).toByte(),
            (long shr 32 and 0xff).toByte(),
            (long shr 24 and 0xff).toByte(),
            (long shr 16 and 0xff).toByte(),
            (long shr 8 and 0xff).toByte(),
            (long and 0xff).toByte(),
        )

    override fun ulongToArray(uLong: ULong): ByteArray =
        byteArrayOf(
            (uLong shr 56 and 0xffu).toByte(),
            (uLong shr 48 and 0xffu).toByte(),
            (uLong shr 40 and 0xffu).toByte(),
            (uLong shr 32 and 0xffu).toByte(),
            (uLong shr 24 and 0xffu).toByte(),
            (uLong shr 16 and 0xffu).toByte(),
            (uLong shr 8 and 0xffu).toByte(),
            (uLong and 0xffu).toByte(),
        )

    /**
     * Similar to [ByteArray] copyInto
     * @param destination Array to receive copy
     * @param destinationOffset index into destination where copy will start
     * @param startIndex index in source array where copy will start
     * @param endIndex index in source that copy will end, exclusive. [endIndex - startIndex] will
     * be number of bytes copied.
     */
    override fun copyInto(
        destination: ByteArray,
        destinationOffset: Int,
        startIndex: Int,
        endIndex: Int,
    ) {
        buf.copyInto(destination, destinationOffset, startIndex, endIndex)
    }

    override fun compareElement(
        element: Byte,
        other: Byte,
    ): Int = element.compareTo(other)

    /**
     * Increase size of buffer. Capacity and content are increased. Position is unchanged. if limit
     * currently set to capacity, it will be set to the new capacity. All data is retained from
     * previous buffer, including bytes between old limit and capacity.
     * @param addCapacity bytes to add. Unsigned as can't be used to shrink.
     */
    override fun expand(addCapacity: UInt) {
        val changeLimit = limit == capacity
        capacity += addCapacity.toInt()
        val newBuf = ByteArray(capacity)
        buf.copyInto(newBuf, 0, 0)
        buf = newBuf
        if (changeLimit) limit = capacity
    }

    /**
     * Appends a buffer starting at its position, to the end of this buffer,
     * starting at position of this buffer for appendBuffer remaining size. New position will
     * be at new limit. If the contents must be expanded (usually is), then a new byteArray is allocated.
     * position is moved to the new limit.
     * @param appendBuffer buffer to be appended, starting at its poition for remaining bytes
     * @return this
     */
    fun expand(appendBuffer: ByteBuffer) {
        val newLimit = position + appendBuffer.remaining
        if (newLimit > capacity) {
            val oldBuf = buf
            buf = ByteArray(newLimit)
            oldBuf.copyInto(buf, 0, 0, position)
        }
        appendBuffer.contentBytes.copyInto(
            buf,
            position,
            appendBuffer.position,
            appendBuffer.remaining,
        )
        capacity = newLimit
        limit = newLimit
        position = limit
    }

    fun get(
        destination: ByteArray,
        destinationOffset: Int = 0,
        size: Int = destination.size,
    ) {
        super.fillArray(destination, destinationOffset, size)
    }

    /**
     * Copies specified bytes from source to this buffer, starting at position, for the
     * specified length. Position is incremented by the length. Any bounds violation throws
     * and IllegalArgumentException
     *
     * @param source byte array to write from, will be unchanged
     * @param sourceOffset starting offset in source, defaults to 0
     * @param length number of bytes to copy, defaults to size of source
     * @return this
     * @throws IllegalArgumentException on bounds violation
     */
    fun putBytes(
        source: ByteArray,
        sourceOffset: Int = 0,
        length: Int = source.size,
    ) {
        checkBounds(sourceOffset, length, length)
        if (length > remaining) {
            throw IllegalArgumentException("Length:$length exceeds remaining:$remaining")
        }
        source.copyInto(buf, position, sourceOffset, length)
        position += length
    }

    /**
     * Make a new ByteBuffer containing the [remaining bytes] of this one. Length can be overridden to
     * a shorter value than the default [remaining]. If length is > [remaining], [remaining] is used.
     *
     * Position in this ByteBuffer is unaffected. Position in new returned ByteBuffer is 0.
     *
     * @param length defaults to [remaining]. can be between 1 and [remaining]
     */
    override fun slice(length: Int): ByteBuffer {
        val l = min(remaining, length)
        val bytes = ByteArray(l)
        buf.copyInto(bytes, 0, position, position + l)
        return ByteBuffer(bytes, this.order)
    }

    /**
     * Convert from a [ByteBuffer] to a [UByteBuffer], retaining the same capacity, position, limit
     * and contents
     */
    fun toUByteBuffer(): UByteBuffer {
        val uBuf = UByteBuffer(capacity, order, isReadOnly, contentBytes.toUByteArray())
        uBuf.positionLimit(position, remaining)
        return uBuf
    }

    override fun toString(): String =
        buildString {
            append("Position: $position, limit: $limit, remaining: $remaining. Content: 0x")
            for (i in position until limit) {
                append("${contentBytes[i].toString(16).padStart(2, '0')} ")
            }
        }

    /**
     * Tells whether or not this buffer is equal to another object.
     *
     * <p> Two byte buffers are equal if, and only if,
     *
     * <ol>
     *
     *   <li><p> They have the same element type,  </p></li>
     *
     *   <li><p> They have the same number of remaining elements, and
     *   </p></li>
     *
     *   <li><p> The two sequences of remaining elements, considered
     *   independently of their starting positions, are pointwise equal.

     *   </p></li>
     *
     * </ol>
     *
     * <p> A byte buffer is not equal to any other type of object.  </p>
     *
     * @param other The object to which this buffer is to be compared
     *
     * @return <tt>true</tt> if, and only if, this buffer is equal to the
     *           given object
     */
    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this === other) return true
        if (other !is ByteBuffer) return false
        if (remaining != other.remaining) return false
        var i = limit - 1
        var j = other.limit - 1
        while (i >= position) {
            if (buf[i--] != other.buf[j--]) return false
        }
        return true
    }

    /**
     * Returns the current hash code of this buffer.
     *
     * <p> The hash code of a byte buffer depends only upon its remaining
     * elements; that is, upon the elements from <tt>position()</tt> up to, and
     * including, the element at <tt>limit()</tt>&nbsp;-&nbsp;<tt>1</tt>.
     *
     * <p> Because buffer hash codes are content-dependent, it is inadvisable
     * to use buffers as keys in hash maps or similar data structures unless it
     * is known that their contents will not change.  </p>
     *
     * @return The current hash code of this buffer
     */
    override fun hashCode(): Int {
        var h = 1
        val p: Int = position
        for (i in limit - 1 downTo p) h = 31 * h + buf[i].toInt()
        return h
    }
}

class UByteBuffer(
    capacity: Int,
    order: ByteOrder = ByteOrder.LittleEndian,
    isReadOnly: Boolean = false,
    override var buf: UByteArray = UByteArray(capacity),
) : ByteBufferBase<UByte, UByteArray>(capacity, order, isReadOnly) {
    constructor(bytes: UByteArray, order: ByteOrder = ByteOrder.LittleEndian) :
        this(bytes.size, order, false, bytes)

    /**
     * Similar to [ByteArray] copyInto
     * @param destination Array to receive copy
     * @param destinationOffset index into destination where copy will start
     * @param startIndex index in source array where copy will start
     * @param endIndex index in source that copy will end, exclusive. [endIndex - startIndex] will
     * be number of bytes copied.
     */
    override fun copyInto(
        destination: UByteArray,
        destinationOffset: Int,
        startIndex: Int,
        endIndex: Int,
    ) {
        buf.copyInto(destination, destinationOffset, startIndex, endIndex)
    }

    override fun compareElement(
        element: UByte,
        other: UByte,
    ): Int = element.compareTo(other)

    /**
     * Increase size of buffer. Capacity and content are increased. Position is unchanged. if limit
     * currently set to capacity, it will be set to the new capacity. All data is retained from
     * previous buffer, including bytes between old limit and capacity.
     * @param addCapacity bytes to add. Unsigned as can't be used to shrink.
     */
    override fun expand(addCapacity: UInt) {
        val changeLimit = limit == capacity
        capacity += addCapacity.toInt()
        val newBuf = UByteArray(capacity)
        buf.copyInto(newBuf, 0, 0)
        buf = newBuf
        if (changeLimit) limit = capacity
    }

    /**
     * Appends a buffer starting at its position, to the end of this buffer,
     * starting at position of this buffer for appendBuffer remaining size. New position will
     * be at new limit. If the contents must be expanded (usually is), then a new byteArray is allocated.
     * position is moved to the new limit.
     * @param appendBuffer buffer to be appended, starting at its poition for remaining bytes
     * @return this
     */
    fun expand(appendBuffer: UByteBuffer) {
        val newLimit = position + appendBuffer.remaining
        if (newLimit > capacity) {
            val oldBuf = buf
            buf = UByteArray(newLimit)
            oldBuf.copyInto(buf, 0, 0, position)
        }
        appendBuffer.contentBytes.copyInto(
            buf,
            position,
            appendBuffer.position,
            appendBuffer.remaining,
        )
        capacity = newLimit
        limit = newLimit
        position = limit
    }

    override fun flip(): UByteBuffer {
        super.flip()
        return this
    }

    override fun getElementAt(index: Int): UByte = buf[index]

    /**
     * gets one byte at the current position without changing the position. The byte is treated as
     * unsigned, so the returned Int will always be positive.
     * @param index indicates which element in the current array to retrieve
     * @return Int will not have it's high order bits set.
     */
    override fun getElementAsInt(index: Int): Int = buf toPosInt index

    /**
     * gets one byte at the current position without changing the position. The byte is treated as
     * unsigned, so the returned UInt will always be positive.
     * @param index indicates which element in the current array to retrieve
     * @return UInt will not have it's high order bits set.
     */
    override fun getElementAsUInt(index: Int): UInt = buf toPosUInt index

    /**
     * gets one byte at the current position without changing the position. The byte is treated as
     * unsigned, so the returned Long will always be positive.
     * @param index indicates which element in the current array to retrieve
     * @return Long will not have it's high order bits set.
     */
    override fun getElementAsLong(index: Int): Long = buf toPosLong index

    /**
     * gets one byte at the current position without changing the position. The byte is treated as
     * unsigned, so the returned ULong will always be positive.
     * @param index indicates which element in the current array to retrieve
     * @return ULong will not have it's high order bits set.
     */
    override fun getElementAsULong(index: Int): ULong = buf toPosULong index

    fun get(
        destination: UByteArray,
        destinationOffset: Int = 0,
        size: Int = destination.size,
    ) {
        super.fillArray(destination, destinationOffset, size)
    }

    override fun getBytes(length: Int): UByteArray {
        val l = min(remaining, length)
        val a = UByteArray(l)
        buf.copyInto(a, 0, position, position + l)
        position += l
        return a
    }

    override fun getBytes(bytes: UByteArray) {
        val l = min(remaining, bytes.size)
        buf.copyInto(bytes, 0, position, position + l)
        position += l
    }

    override fun put(bytes: UByteArray) {
        val l = min(remaining, bytes.size)
        bytes.copyInto(buf, position, 0, l)
        position += l
    }

    /**
     * Copies specified bytes from source to this buffer, starting at position, for the
     * specified length. Position is incremented by the length. Any bounds violation throws
     * an IllegalArgumentException
     *
     * @param source byte array to write from, will be unchanged
     * @param sourceOffset starting offset in source, defaults to 0
     * @param length number of bytes to copy, defaults to size of source
     * @return this
     * @throws IllegalArgumentException on bounds violation
     */
    fun putBytes(
        source: UByteArray,
        sourceOffset: Int = 0,
        length: Int = source.size,
    ) {
        checkBounds(sourceOffset, length, length)
        if (length > remaining) {
            throw IllegalArgumentException("Length:$length exceeds remaining:$remaining")
        }
        for (i in sourceOffset until sourceOffset + length) {
            byte = source[i]
        }
    }

    override fun putEndian(bytes: UByteArray) {
        if (order == ByteOrder.LittleEndian) {
            bytes.reverse()
        }
        put(bytes)
    }

    override fun setElementAt(
        index: Int,
        element: UByte,
    ) {
        buf[index] = element
    }

    /**
     * Make a new ByteBuffer containing the [remaining bytes] of this one. Length can be overriden to
     * a shorter value than the default [remaining]. Position is unaffected
     * @param length defaults to [remaining]. can be between 1 and [remaining]
     */
    override fun slice(length: Int): UByteBuffer {
        val bytes = UByteArray(length)
        buf.copyInto(bytes, 0, position, position + length)
        return UByteBuffer(bytes, this.order)
    }

    override fun shortToArray(short: Short): UByteArray =
        ubyteArrayOf(
            (short.toInt() shr 8).toUByte(),
            short.toUByte(),
        )

    override fun ushortToArray(ushort: UShort): UByteArray =
        ubyteArrayOf(
            (ushort.toUInt() shr 8).toUByte(),
            ushort.toUByte(),
        )

    override fun intToArray(int: Int): UByteArray =
        ubyteArrayOf(
            (int shr 24 and 0xff).toUByte(),
            (int shr 16 and 0xff).toUByte(),
            (int shr 8 and 0xff).toUByte(),
            (int and 0xff).toUByte(),
        )

    override fun uintToArray(int: UInt): UByteArray =
        ubyteArrayOf(
            (int shr 24 and 0xffu).toUByte(),
            (int shr 16 and 0xffu).toUByte(),
            (int shr 8 and 0xffu).toUByte(),
            (int and 0xffu).toUByte(),
        )

    override fun longToArray(long: Long): UByteArray =
        ubyteArrayOf(
            (long shr 56 and 0xff).toUByte(),
            (long shr 48 and 0xff).toUByte(),
            (long shr 40 and 0xff).toUByte(),
            (long shr 32 and 0xff).toUByte(),
            (long shr 24 and 0xff).toUByte(),
            (long shr 16 and 0xff).toUByte(),
            (long shr 8 and 0xff).toUByte(),
            (long and 0xff).toUByte(),
        )

    override fun ulongToArray(uLong: ULong): UByteArray =
        ubyteArrayOf(
            (uLong shr 56 and 0xffu).toUByte(),
            (uLong shr 48 and 0xffu).toUByte(),
            (uLong shr 40 and 0xffu).toUByte(),
            (uLong shr 32 and 0xffu).toUByte(),
            (uLong shr 24 and 0xffu).toUByte(),
            (uLong shr 16 and 0xffu).toUByte(),
            (uLong shr 8 and 0xffu).toUByte(),
            (uLong and 0xffu).toUByte(),
        )

    /**
     * Convert from a [UByteBuffer] to a [ByteBuffer], retaining the same capacity, position, limit
     * and contents
     */
    fun toByteBuffer(): ByteBuffer {
        val uBuf = ByteBuffer(capacity, order, isReadOnly, contentBytes.toByteArray())
        uBuf.positionLimit(position, remaining)
        return uBuf
    }

    override fun toString(): String =
        buildString {
            append("Position: $position, limit: $limit, remaining: $remaining. Content: 0x")
            for (i in position until limit) {
                append("${contentBytes[i].toString(16).padStart(2, '0')} ")
            }
        }

    /**
     * Tells whether or not this buffer is equal to another object.
     *
     * <p> Two byte buffers are equal if, and only if,
     *
     * <ol>
     *
     *   <li><p> They have the same element type,  </p></li>
     *
     *   <li><p> They have the same number of remaining elements, and
     *   </p></li>
     *
     *   <li><p> The two sequences of remaining elements, considered
     *   independently of their starting positions, are pointwise equal.

     *   </p></li>
     *
     * </ol>
     *
     * <p> A byte buffer is not equal to any other type of object.  </p>
     *
     * @param other The object to which this buffer is to be compared
     *
     * @return <tt>true</tt> if, and only if, this buffer is equal to the
     *           given object
     */
    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this === other) return true
        if (other !is UByteBuffer) return false
        if (remaining != other.remaining) return false
        var i = limit - 1
        var j = other.limit - 1
        while (i >= position) {
            if (buf[i--] != other.buf[j--]) return false
        }
        return true
    }

    /**
     * Returns the current hash code of this buffer.
     *
     * <p> The hash code of a byte buffer depends only upon its remaining
     * elements; that is, upon the elements from <tt>position()</tt> up to, and
     * including, the element at <tt>limit()</tt>&nbsp;-&nbsp;<tt>1</tt>.
     *
     * <p> Because buffer hash codes are content-dependent, it is inadvisable
     * to use buffers as keys in hash maps or similar data structures unless it
     * is known that their contents will not change.  </p>
     *
     * @return The current hash code of this buffer
     */
    override fun hashCode(): Int {
        var h = 1
        val p: Int = position
        for (i in limit - 1 downTo p) h = 31 * h + buf[i].toInt()
        return h
    }
}
