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
    val actual =
      nip19.uriToRoute("nostr:npub1hv7k2s755n697sptva8vkh9jz40lzfzklnwj6ekewfmxp5crwdjs27007y")

    Assert.assertEquals(Nip19.Type.USER, actual?.type)
    Assert.assertEquals(
      "bb3d6543d4a4f45f402b674ecb5cb2155ff12456fcdd2d66d9727660d3037365",
      actual?.hex
    )
  }

  @Test()
  fun uri_to_route_note() {
    val actual =
      nip19.uriToRoute("nostr:note1stqea6wmwezg9x6yyr6qkukw95ewtdukyaztycws65l8wppjmtpscawevv")

    Assert.assertEquals(Nip19.Type.NOTE, actual?.type)
    Assert.assertEquals(
      "82c19ee9db7644829b4420f40b72ce2d32e5b7962744b261d0d53e770432dac3",
      actual?.hex
    )
  }

  @Ignore("Not implemented yet")
  @Test()
  fun uri_to_route_nprofile() {
    val actual = nip19.uriToRoute("nostr:nprofile")

    Assert.assertEquals(Nip19.Type.USER, actual?.type)
    Assert.assertEquals("*", actual?.hex)
  }

  @Ignore("Not implemented yet")
  @Test()
  fun uri_to_route_nevent() {
    val actual = nip19.uriToRoute("nostr:nevent")

    Assert.assertEquals(Nip19.Type.USER, actual?.type)
    Assert.assertEquals("*", actual?.hex)
  }

  @Ignore("Not implemented yet")
  @Test()
  fun uri_to_route_nrelay() {
    val actual = nip19.uriToRoute("nostr:nrelay")

    Assert.assertEquals(Nip19.Type.RELAY, actual?.type)
    Assert.assertEquals("*", actual?.hex)
  }

  @Ignore("Not implemented yet")
  @Test()
  fun uri_to_route_naddr() {
    val actual = nip19.uriToRoute("nostr:naddr")

    Assert.assertEquals(Nip19.Type.ADDRESS, actual?.type)
    Assert.assertEquals("*", actual?.hex)
  }

  private fun byteArrayOfInts(vararg ints: Int) = ByteArray(ints.size) { pos -> ints[pos].toByte() }
}
