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
package com.vitorpamplona.quartz.utils.urldetector

import kotlin.test.Test
import kotlin.test.assertEquals

class UrlMarkerTest {
    fun testUrlMarker(
        testString: String,
        scheme: String?,
        username: String?,
        password: String?,
        host: String?,
        port: Int,
        path: String?,
        query: String?,
        fragment: String?,
        indices: IntArray,
    ) {
        val urlMarker = UrlMarker()
        urlMarker.setIndices(indices)
        val url = urlMarker.createUrl(testString)
        assertEquals(url.host, host, "host, " + testString)
        assertEquals(url.path, path, "path, " + testString)
        assertEquals(url.scheme, scheme, "scheme, " + testString)
        assertEquals(url.username, username, "username, " + testString)
        assertEquals(url.password, password, "password, " + testString)
        assertEquals(url.port, port, "port, " + testString)
        assertEquals(url.query, query, "query, " + testString)
        assertEquals(url.fragment, fragment, "fragment, " + testString)
    }

    @Test
    fun test1() =
        testUrlMarker(
            "hello@hello.com",
            "https",
            "hello",
            "",
            "hello.com",
            443,
            "/",
            "",
            "",
            intArrayOf(-1, 0, 6, -1, -1, -1, -1),
        )

    @Test
    fun test2() =
        testUrlMarker(
            "http://hello@hello.com",
            "http",
            "hello",
            "",
            "hello.com",
            80,
            "/",
            "",
            "",
            intArrayOf(0, 7, 13, -1, -1, -1, -1),
        )

    @Test
    fun test3() =
        testUrlMarker(
            "hello@hello.com",
            "https",
            "hello",
            "",
            "hello.com",
            443,
            "/",
            "",
            "",
            intArrayOf(-1, 0, 6, -1, -1, -1, -1),
        )

    @Test
    fun test4() =
        testUrlMarker(
            "https://user@google.com/h?hello=w#abc",
            "https",
            "user",
            "",
            "google.com",
            443,
            "/h",
            "?hello=w",
            "#abc",
            intArrayOf(0, 8, 13, -1, 23, 25, 33),
        )

    @Test
    fun test5() =
        testUrlMarker(
            "www.booopp.com:20#fa",
            "https",
            "",
            "",
            "www.booopp.com",
            20,
            "/",
            "",
            "#fa",
            intArrayOf(-1, -1, 0, 15, -1, -1, 17),
        )

    @Test
    fun test6() =
        testUrlMarker(
            "www.yahooo.com:20?fff#aa",
            "https",
            "",
            "",
            "www.yahooo.com",
            20,
            "/",
            "?fff",
            "#aa",
            intArrayOf(-1, -1, 0, 15, -1, 17, 21),
        )

    @Test
    fun test7() =
        testUrlMarker(
            "www.google.com#fa",
            "https",
            "",
            "",
            "www.google.com",
            443,
            "/",
            "",
            "#fa",
            intArrayOf(-1, -1, 0, -1, -1, -1, 14),
        )

    @Test
    fun test8() =
        testUrlMarker(
            "www.google.com?3fd#fa",
            "https",
            "",
            "",
            "www.google.com",
            443,
            "/",
            "?3fd",
            "#fa",
            intArrayOf(-1, -1, 0, -1, -1, 14, 18),
        )

    @Test
    fun test9() =
        testUrlMarker(
            "//www.google.com/",
            "",
            "",
            "",
            "www.google.com",
            -1,
            "/",
            "",
            "",
            intArrayOf(-1, -1, 2, -1, 16, -1, -1),
        )

    @Test
    fun test10() =
        testUrlMarker(
            "http://www.google.com/",
            "http",
            "",
            "",
            "www.google.com",
            80,
            "/",
            "",
            "",
            intArrayOf(0, -1, 7, -1, 21, -1, -1),
        )

    @Test
    fun test11() =
        testUrlMarker(
            "ftp://whosdere:me@google.com/",
            "ftp",
            "whosdere",
            "me",
            "google.com",
            21,
            "/",
            "",
            "",
            intArrayOf(0, 6, 18, -1, 28, -1, -1),
        )

    @Test
    fun test12() =
        testUrlMarker(
            "ono:doope@fb.net:9090/dhdh",
            "https",
            "ono",
            "doope",
            "fb.net",
            9090,
            "/dhdh",
            "",
            "",
            intArrayOf(-1, 0, 10, 17, 21, -1, -1),
        )

    @Test
    fun test13() =
        testUrlMarker(
            "ono:a@fboo.com:90/dhdh/@1234",
            "https",
            "ono",
            "a",
            "fboo.com",
            90,
            "/dhdh/@1234",
            "",
            "",
            intArrayOf(-1, 0, 6, 15, 17, -1, -1),
        )

    @Test
    fun test14() =
        testUrlMarker(
            "fbeoo.net:990/dhdeh/@1234",
            "https",
            "",
            "",
            "fbeoo.net",
            990,
            "/dhdeh/@1234",
            "",
            "",
            intArrayOf(-1, -1, 0, 10, 13, -1, -1),
        )

    @Test
    fun test15() =
        testUrlMarker(
            "fbeoo:@boop.com/dhdeh/@1234?aj=r",
            "https",
            "fbeoo",
            "",
            "boop.com",
            443,
            "/dhdeh/@1234",
            "?aj=r",
            "",
            intArrayOf(-1, 0, 7, -1, 15, 27, -1),
        )

    @Test
    fun test16() =
        testUrlMarker(
            "bloop:@noooo.com/doop/@1234",
            "https",
            "bloop",
            "",
            "noooo.com",
            443,
            "/doop/@1234",
            "",
            "",
            intArrayOf(-1, 0, 7, -1, 16, -1, -1),
        )

    @Test
    fun test17() =
        testUrlMarker(
            "bah.com/lala/@1234/@dfd@df?@dsf#ono",
            "https",
            "",
            "",
            "bah.com",
            443,
            "/lala/@1234/@dfd@df",
            "?@dsf",
            "#ono",
            intArrayOf(-1, -1, 0, -1, 7, 26, 31),
        )

    @Test
    fun test18() =
        testUrlMarker(
            "https://dewd:dood@www.google.com:20/?why=is&this=test#?@Sdsf",
            "https",
            "dewd",
            "dood",
            "www.google.com",
            20,
            "/",
            "?why=is&this=test",
            "#?@Sdsf",
            intArrayOf(0, 8, 18, 33, 35, 36, 53),
        )
}
