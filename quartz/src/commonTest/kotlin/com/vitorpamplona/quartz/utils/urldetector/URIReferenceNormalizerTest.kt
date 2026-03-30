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

import com.vitorpamplona.quartz.utils.Rfc3986
import kotlin.test.Test
import kotlin.test.assertEquals

class URIReferenceNormalizerTest {
    @Test
    fun test_normalize() {
        val uriRef1 = Rfc3986.normalize("hTTp://example.com/")
        assertEquals("http://example.com/", uriRef1)

        val uriRef2 = Rfc3986.normalize("http://example.com/")
        assertEquals("http://example.com/", uriRef2)

        val uriRef3 = Rfc3986.normalize("http://%75ser@example.com/")
        assertEquals("http://user@example.com/", uriRef3)

        val uriRef4 = Rfc3986.normalize("http://%e3%83%a6%e3%83%bc%e3%82%b6%e3%83%bc@example.com/")
        assertEquals("http://%E3%83%A6%E3%83%BC%E3%82%B6%E3%83%BC@example.com/", uriRef4)

        val uriRef5 = Rfc3986.normalize("http://%65%78%61%6D%70%6C%65.com/")
        assertEquals("http://example.com/", uriRef5)

        val uriRef6 = Rfc3986.normalize("http://%e4%be%8b.com/")
        assertEquals("http://%E4%BE%8B.com/", uriRef6)

        val uriRef7 = Rfc3986.normalize("http://LOCALhost/")
        assertEquals("http://localhost/", uriRef7)

        val uriRef8 = Rfc3986.normalize("http://example.com")
        assertEquals("http://example.com/", uriRef8)

        val uriRef9 = Rfc3986.normalize("http://example.com/%61/%62/%63/")
        assertEquals("http://example.com/a/b/c/", uriRef9)

        val uriRef10 = Rfc3986.normalize("http://example.com/%e3%83%91%e3%82%b9/")
        assertEquals("http://example.com/%E3%83%91%E3%82%B9/", uriRef10)

        val uriRef11 = Rfc3986.normalize("http://example.com/a/b/c/../d/")
        assertEquals("http://example.com/a/b/d/", uriRef11)

        val uriRef12 = Rfc3986.normalize("http://example.com:80/")
        assertEquals("http://example.com/", uriRef12)
    }

    @Test
    fun moreTests() {
        // From our conversation
        assertEquals("http://%E4%BE%8B.com/", Rfc3986.normalize("http://%e4%be%8b.com/"))

        // Unreserved chars that should be decoded
        assertEquals("http://example.com/~user/ABC", Rfc3986.normalize("http://example.com/%7euser/%41BC"))

        // Reserved chars that must NOT be decoded
        assertEquals("http://example.com/path%2Fsegment?key%3Dvalue", Rfc3986.normalize("http://example.com/path%2Fsegment?key%3Dvalue"))

        // Mixed: some decodable, some not, lowercase hex
        assertEquals("http://example.com/~%2Fa%3F", Rfc3986.normalize("http://EXAMPLE.COM/%7e%2f%61%3F"))

        // Userinfo
        assertEquals("http://user%40name@example.com/", Rfc3986.normalize("http://user%40name@example.com/"))

        // With port and query and fragment
        assertEquals("https://example.com:8080/foo~bar?a%3D1&b%2Bc#frag%23ment", Rfc3986.normalize("https://Example.COM:8080/foo%7ebar?a%3D1&b%2Bc#frag%23ment"))

        // IPv6
        assertEquals("http://[::1]:8080/path", Rfc3986.normalize("http://[::1]:8080/path"))

        // Already normalized
        assertEquals("http://example.com/~user", Rfc3986.normalize("http://example.com/~user"))

        assertEquals("wss://localhost:3030/", Rfc3986.normalize("wss://localhost:3030"))
    }
}
