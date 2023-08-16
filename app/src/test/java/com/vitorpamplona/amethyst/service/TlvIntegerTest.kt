package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.service.nip19.toByteArray
import com.vitorpamplona.amethyst.service.nip19.toInt32
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Test

class TlvIntegerTest {
    fun to_int_32_length_smaller_than_4() {
        Assert.assertNull(byteArrayOfInts(1, 2, 3).toInt32())
    }

    fun to_int_32_length_bigger_than_4() {
        Assert.assertNull(byteArrayOfInts(1, 2, 3, 4, 5).toInt32())
    }

    @Test()
    fun to_int_32_length_4() {
        val actual = byteArrayOfInts(1, 2, 3, 4).toInt32()

        assertEquals(16909060, actual)
    }

    @Test()
    fun backAndForth() {
        assertEquals(234, 234.toByteArray().toInt32())
        assertEquals(1, 1.toByteArray().toInt32())
        assertEquals(0, 0.toByteArray().toInt32())
        assertEquals(1000, 1000.toByteArray().toInt32())

        assertEquals(-234, (-234).toByteArray().toInt32())
        assertEquals(-1, (-1).toByteArray().toInt32())
        assertEquals(-0, (-0).toByteArray().toInt32())
        assertEquals(-1000, (-1000).toByteArray().toInt32())
    }

    private fun byteArrayOfInts(vararg ints: Int) =
        ByteArray(ints.size) { pos -> ints[pos].toByte() }
}
