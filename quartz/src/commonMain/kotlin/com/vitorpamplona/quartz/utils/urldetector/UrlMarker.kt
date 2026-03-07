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

class UrlMarker {
    private var schemeIndex = -1
    private var usernamePasswordIndex = -1
    private var hostIndex = -1
    private var portIndex = -1
    private var pathIndex = -1
    private var queryIndex = -1
    private var fragmentIndex = -1

    var hasChanged: Boolean = false
        private set

    fun createUrl(originalUrl: String): Url = Url(this, originalUrl)

    fun setIndex(
        urlPart: UrlPart,
        index: Int,
    ) {
        hasChanged = true
        when (urlPart) {
            UrlPart.SCHEME -> schemeIndex = index
            UrlPart.USERNAME_PASSWORD -> usernamePasswordIndex = index
            UrlPart.HOST -> hostIndex = index
            UrlPart.PORT -> portIndex = index
            UrlPart.PATH -> pathIndex = index
            UrlPart.QUERY -> queryIndex = index
            UrlPart.FRAGMENT -> fragmentIndex = index
        }
    }

    fun hasScheme() = schemeIndex >= 0

    fun hasPort() = portIndex >= 0

    fun hasUsernamePassword() = usernamePasswordIndex >= 0

    fun hasQuery() = queryIndex >= 0

    fun hasFragment() = fragmentIndex >= 0

    /**
     * @param urlPart The part you want the index of
     * @return Returns the index of the part
     */
    fun indexOf(urlPart: UrlPart): Int =
        when (urlPart) {
            UrlPart.SCHEME -> schemeIndex
            UrlPart.USERNAME_PASSWORD -> usernamePasswordIndex
            UrlPart.HOST -> hostIndex
            UrlPart.PORT -> portIndex
            UrlPart.PATH -> pathIndex
            UrlPart.QUERY -> queryIndex
            UrlPart.FRAGMENT -> fragmentIndex
        }

    fun unsetIndex(urlPart: UrlPart) {
        setIndex(urlPart, -1)
    }

    /**
     * This is used in TestUrlMarker to set indices more easily.
     * @param indices array of indices of size 7
     */
    fun setIndices(indices: IntArray): UrlMarker {
        require(indices.size == 7) { "Malformed index array." }
        setIndex(UrlPart.SCHEME, indices[0])
        setIndex(UrlPart.USERNAME_PASSWORD, indices[1])
        setIndex(UrlPart.HOST, indices[2])
        setIndex(UrlPart.PORT, indices[3])
        setIndex(UrlPart.PATH, indices[4])
        setIndex(UrlPart.QUERY, indices[5])
        setIndex(UrlPart.FRAGMENT, indices[6])
        return this
    }
}
