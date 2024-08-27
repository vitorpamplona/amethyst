/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.quartz.encoders

import org.junit.Assert
import org.junit.Assert.assertNotNull
import org.junit.Test

class Nip19Bech32Test {
    @Test()
    fun uri_to_route_null() {
        val actual = Nip19Bech32.uriToRoute(null)

        Assert.assertEquals(null, actual)
    }

    @Test()
    fun uri_to_route_unknown() {
        val actual = Nip19Bech32.uriToRoute("nostr:unknown")

        Assert.assertEquals(null, actual)
    }

    @Test()
    fun uri_to_route_npub() {
        val actual =
            Nip19Bech32.uriToRoute("nostr:npub1hv7k2s755n697sptva8vkh9jz40lzfzklnwj6ekewfmxp5crwdjs27007y")

        Assert.assertTrue(actual?.entity is Nip19Bech32.NPub)
        Assert.assertEquals(
            "bb3d6543d4a4f45f402b674ecb5cb2155ff12456fcdd2d66d9727660d3037365",
            (actual?.entity as? Nip19Bech32.NPub)?.hex,
        )
    }

    @Test()
    fun uri_to_route_note() {
        val result =
            Nip19Bech32.uriToRoute("nostr:note1stqea6wmwezg9x6yyr6qkukw95ewtdukyaztycws65l8wppjmtpscawevv")?.entity as? Nip19Bech32.Note

        assertNotNull(result)
        Assert.assertEquals(
            "82c19ee9db7644829b4420f40b72ce2d32e5b7962744b261d0d53e770432dac3",
            result?.hex,
        )
    }

    @Test()
    fun uri_to_route_nprofile() {
        val actual = Nip19Bech32.uriToRoute("nostr:nprofile")

        Assert.assertEquals(null, actual)
    }

    @Test()
    fun uri_to_route_incomplete_nevent() {
        val actual = Nip19Bech32.uriToRoute("nostr:nevent")

        Assert.assertEquals(null, actual)
    }

    @Test()
    fun uri_to_route_incomplete_nrelay() {
        val actual = Nip19Bech32.uriToRoute("nostr:nrelay")

        Assert.assertEquals(null, actual)
    }

    @Test()
    fun uri_to_route_incomplete_naddr() {
        val actual = Nip19Bech32.uriToRoute("nostr:naddr")

        Assert.assertEquals(null, actual)
    }

    @Test()
    fun uri_to_route_complete_nprofile_2() {
        val actual = Nip19Bech32.uriToRoute("nostr:nprofile1qqsyvrp9u6p0mfur9dfdru3d853tx9mdjuhkphxuxgfwmryja7zsvhqpzamhxue69uhhv6t5daezumn0wd68yvfwvdhk6tcpz9mhxue69uhkummnw3ezuamfdejj7qgwwaehxw309ahx7uewd3hkctcscpyug")

        Assert.assertNotNull(actual)
        Assert.assertTrue(actual?.entity is Nip19Bech32.NProfile)
        Assert.assertEquals("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c", (actual?.entity as? Nip19Bech32.NProfile)?.hex)
        Assert.assertEquals("wss://vitor.nostr1.com/", (actual?.entity as? Nip19Bech32.NProfile)?.relay?.first())
    }

    @Test()
    fun uri_to_route_complete_nprofile() {
        val actual = Nip19Bech32.uriToRoute("nostr:nprofile1qy2hwumn8ghj7un9d3shjtnyv9kh2uewd9hj7qgwwaehxw309ahx7uewd3hkctcpr9mhxue69uhhyetvv9ujuumwdae8gtnnda3kjctv9uqzq9thu3vem5gvsc6f3l3uyz7c92h6lq56t9wws0zulzkrgc6nrvym5jfztf")

        Assert.assertTrue(actual?.entity is Nip19Bech32.NProfile)
        Assert.assertEquals("1577e4599dd10c863498fe3c20bd82aafaf829a595ce83c5cf8ac3463531b09b", (actual?.entity as? Nip19Bech32.NProfile)?.hex)
    }

    @Test()
    fun uri_to_route_complete_nevent() {
        val actual = Nip19Bech32.uriToRoute("nostr:nevent1qy2hwumn8ghj7un9d3shjtnyv9kh2uewd9hj7qgwwaehxw309ahx7uewd3hkctcpr9mhxue69uhhyetvv9ujuumwdae8gtnnda3kjctv9uq36amnwvaz7tmjv4kxz7fwvd5xjcmpvahhqmr9vfejucm0d5hsz9mhwden5te0wfjkccte9ec8y6tdv9kzumn9wshsz8thwden5te0dehhxarj9ekh2arfdeuhwctvd3jhgtnrdakj7qg3waehxw309ucngvpwvcmh5tnfduhszythwden5te0dehhxarj9emkjmn99uq3jamnwvaz7tmhv4kxxmmdv5hxummnw3ezuamfdejj7qpqvsup5xk3e2quedxjvn2gjppc0lqny5dmnr2ypc9tftwmdxta0yjqrd6n50")

        Assert.assertTrue(actual?.entity is Nip19Bech32.NEvent)
        Assert.assertEquals("64381a1ad1ca81ccb4d264d48904387fc13251bb98d440e0ab4addb6997d7924", (actual?.entity as? Nip19Bech32.NEvent)?.hex)
    }

    @Test()
    fun uri_to_route_complete_naddr() {
        val actual = Nip19Bech32.uriToRoute("nostr:naddr1qqyxzmt9w358jum5qyt8wumn8ghj7un9d3shjtnwdaehgu3wvfskueqzypd7v3r24z33cydnk3fmlrd0exe5dlej3506zxs05q4puerp765mzqcyqqq8scsq6mk7u")

        Assert.assertTrue(actual?.entity is Nip19Bech32.NAddress)
        Assert.assertEquals("30818:5be6446aa8a31c11b3b453bf8dafc9b346ff328d1fa11a0fa02a1e6461f6a9b1:amethyst", (actual?.entity as? Nip19Bech32.NAddress)?.atag)
    }
}
