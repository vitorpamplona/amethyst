package com.vitorpamplona.amethyst.service

import org.junit.Assert
import org.junit.Test

class Nip19Test {

  @Test(expected = IllegalArgumentException::class)
  fun to_int_32_length_smaller_than_4() {
    toInt32(ByteArray(3))
  }

  @Test(expected = IllegalArgumentException::class)
  fun to_int_32_length_bigger_than_4() {
    toInt32(ByteArray(5))
  }

  @Test()
  fun to_int_32_length_4() {
    val actual = toInt32(ByteArray(4))
    Assert.assertEquals(0, actual)
  }
}
