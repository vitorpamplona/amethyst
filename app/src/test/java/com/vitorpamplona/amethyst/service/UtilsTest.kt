package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.service.nip19.toInt32
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test

class UtilsTest {

    @Test(expected = IllegalArgumentException::class)
    fun to_int_32_length_smaller_than_4() {
        toInt32(byteArrayOfInts(1, 2, 3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun to_int_32_length_bigger_than_4() {
        toInt32(byteArrayOfInts(1, 2, 3, 4, 5))
    }

    @Test()
    fun to_int_32_length_4() {
        val actual = toInt32(byteArrayOfInts(1, 2, 3, 4))

        Assert.assertEquals(16909060, actual)
    }

    @Ignore("Test not implemented yet")
    @Test()
    fun parse_TLV() {
        // TODO: I don't know how to test this (?)
    }

    private fun byteArrayOfInts(vararg ints: Int) =
        ByteArray(ints.size) { pos -> ints[pos].toByte() }
}
