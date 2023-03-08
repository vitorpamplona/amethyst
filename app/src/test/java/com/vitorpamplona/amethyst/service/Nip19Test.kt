package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.service.nip19.Nip19
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test

class Nip19Test {
    @Test()
    fun uri_to_route_null() {
        val actual = Nip19.uriToRoute(null)

        Assert.assertEquals(null, actual)
    }

    @Test()
    fun uri_to_route_unknown() {
        val actual = Nip19.uriToRoute("nostr:unknown")

        Assert.assertEquals(null, actual)
    }

    @Test()
    fun uri_to_route_npub() {
        val actual =
            Nip19.uriToRoute("nostr:npub1hv7k2s755n697sptva8vkh9jz40lzfzklnwj6ekewfmxp5crwdjs27007y")

        Assert.assertEquals(Nip19.Type.USER, actual?.type)
        Assert.assertEquals(
            "bb3d6543d4a4f45f402b674ecb5cb2155ff12456fcdd2d66d9727660d3037365",
            actual?.hex
        )
    }

    @Test()
    fun uri_to_route_note() {
        val actual =
            Nip19.uriToRoute("nostr:note1stqea6wmwezg9x6yyr6qkukw95ewtdukyaztycws65l8wppjmtpscawevv")

        Assert.assertEquals(Nip19.Type.NOTE, actual?.type)
        Assert.assertEquals(
            "82c19ee9db7644829b4420f40b72ce2d32e5b7962744b261d0d53e770432dac3",
            actual?.hex
        )
    }

    @Ignore("Test not implemented yet")
    @Test()
    fun uri_to_route_nprofile() {
        val actual = Nip19.uriToRoute("nostr:nprofile")

        Assert.assertEquals(Nip19.Type.USER, actual?.type)
        Assert.assertEquals("*", actual?.hex)
    }

    @Ignore("Test not implemented yet")
    @Test()
    fun uri_to_route_nevent() {
        val actual = Nip19.uriToRoute("nostr:nevent")

        Assert.assertEquals(Nip19.Type.USER, actual?.type)
        Assert.assertEquals("*", actual?.hex)
    }

    @Ignore("Test not implemented yet")
    @Test()
    fun uri_to_route_nrelay() {
        val actual = Nip19.uriToRoute("nostr:nrelay")

        Assert.assertEquals(Nip19.Type.RELAY, actual?.type)
        Assert.assertEquals("*", actual?.hex)
    }

    @Ignore("Test not implemented yet")
    @Test()
    fun uri_to_route_naddr() {
        val actual = Nip19.uriToRoute("nostr:naddr")

        Assert.assertEquals(Nip19.Type.ADDRESS, actual?.type)
        Assert.assertEquals("*", actual?.hex)
    }
}
