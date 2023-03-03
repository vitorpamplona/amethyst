package com.vitorpamplona.amethyst.service

import org.junit.Assert
import org.junit.Ignore
import org.junit.Test

class Nip19Test {

  private val nip19 = Nip19();

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

  @Ignore("Not implemented yet")
  @Test()
  fun parse_TLV() {
    // TODO
  }

  @Test()
  fun uri_to_route_null() {
    val actual = nip19.uriToRoute(null)

    Assert.assertEquals(null, actual)
  }

  @Test()
  fun uri_to_route_unknown() {
    val actual = nip19.uriToRoute("nostr:unknown")

    Assert.assertEquals(null, actual)
  }

  @Test()
  fun uri_to_route_npub() {
    val actual = nip19.uriToRoute("nostr:npub1hv7k2s755n697sptva8vkh9jz40lzfzklnwj6ekewfmxp5crwdjs27007y")

    Assert.assertEquals(Nip19.Type.USER, actual?.type)
    Assert.assertEquals("bb3d6543d4a4f45f402b674ecb5cb2155ff12456fcdd2d66d9727660d3037365", actual?.hex)
  }

  private fun byteArrayOfInts(vararg ints: Int) = ByteArray(ints.size) { pos -> ints[pos].toByte() }
}
