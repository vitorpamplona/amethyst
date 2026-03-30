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

/**
 * Creating own Uri class since java.net.Uri would throw parsing exceptions
 * for URL's considered ok by browsers.
 *
 * Also to avoid further conflict, this does stuff that the normal Uri object doesn't do:
 * - Converts http://google.com/a/b/.//./../c to http://google.com/a/c
 * - Decodes repeatedly so that http://host/%2525252525252525 becomes http://host/%25 while normal decoders
 * would make it http://host/%25252525252525 (one less 25)
 * - Removes tabs and new lines: http://www.google.com/foo\tbar\rbaz\n2 becomes "http://www.google.com/foobarbaz2"
 * - Converts IP addresses: http://3279880203/blah becomes http://195.127.0.11/blah
 * - Strips fragments (anything after #)
 *
 */
class Url(
    val urlMarker: UrlMarker,
    val originalUrl: String,
) {
    private var _scheme: String? = null
    private var _username: String? = null
    private var _password: String? = null
    private var rawHost: String? = null
    private var _port = 0
    private var rawPath: String? = null
    private var _query: String? = null
    private var _fragment: String? = null

    override fun toString(): String = this.fullUrl

    /**
     * Note that this includes the fragment
     * @return Formats the url to: [scheme]://[username]:[password]@[host]:[port]/[path]?[query]#[fragment]
     */
    val fullUrl: String
        get() = this.fullUrlWithoutFragment + this.fragment

    /**
     *
     * @return Formats the url to: [scheme]://[username]:[password]@[host]:[port]/[path]?[query]
     */
    val fullUrlWithoutFragment: String
        get() {
            val url = StringBuilder()
            if (this.scheme.isNotEmpty()) {
                url.append(this.scheme)
                url.append(":")
            }
            url.append("//")

            if (this.username.isNotEmpty()) {
                url.append(this.username)
                if (this.password.isNotEmpty()) {
                    url.append(":")
                    url.append(this.password)
                }
                url.append("@")
            }

            url.append(this.host)
            if (this.port > 0 && this.port != SCHEME_PORT_MAP[this.scheme]) {
                url.append(":")
                url.append(this.port)
            }

            url.append(this.path)
            url.append(this.query)

            return url.toString()
        }

    val scheme: String
        get() {
            if (_scheme == null) {
                if (exists(UrlPart.SCHEME)) {
                    _scheme = getPart(UrlPart.SCHEME)
                    val index = _scheme!!.indexOf(":")
                    if (index != -1) {
                        _scheme = _scheme!!.substring(0, index)
                    }
                    _scheme = _scheme!!.lowercase()
                } else if (!originalUrl.startsWith("//")) {
                    _scheme = DEFAULT_SCHEME
                }
            }
            return _scheme ?: ""
        }

    val username: String
        get() {
            if (_username == null) {
                populateUsernamePassword()
            }
            return _username ?: ""
        }

    val password: String
        get() {
            if (_password == null) {
                populateUsernamePassword()
            }
            return _password ?: ""
        }

    val host: String
        get() {
            if (this.rawHost == null) {
                this.rawHost =
                    getPart(UrlPart.HOST)?.let {
                        lowercaseLiteralChars(normalizeComponent(it))
                    }
                if (exists(UrlPart.PORT)) {
                    this.rawHost =
                        rawHost?.let {
                            it.substring(0, it.length - 1)
                        }
                }
            }
            return this.rawHost!!
        }

    /**
     * port = 0 means it hasn't been set yet. port = -1 means there is no port
     */
    val port: Int
        get() {
            if (_port == 0) {
                val portString = getPart(UrlPart.PORT)
                if (!portString.isNullOrEmpty()) {
                    _port = portString.toIntOrNull() ?: -1
                } else {
                    _port = SCHEME_PORT_MAP[this.scheme] ?: -1
                }
            }
            return _port
        }

    val path: String?
        get() {
            if (this.rawPath == null) {
                this.rawPath = getPart(UrlPart.PATH)?.let {
                    normalizeComponent(removeDotSegments(it))
                } ?: "/"
            }
            return this.rawPath
        }

    val query: String
        get() {
            if (_query == null) {
                _query =
                    getPart(UrlPart.QUERY)?.let {
                        normalizeComponent(it)
                    } ?: ""
            }
            return _query ?: ""
        }

    val fragment: String
        get() {
            if (_fragment == null) {
                _fragment = getPart(UrlPart.FRAGMENT)?.let {
                    normalizeComponent(it)
                } ?: ""
            }
            return _fragment ?: ""
        }

    private fun populateUsernamePassword() {
        val usernamePassword = getPart(UrlPart.USERNAME_PASSWORD)
        if (usernamePassword != null) {
            val usernamePasswordParts: List<String> =
                usernamePassword.substring(0, usernamePassword.length - 1).split(":")
            if (usernamePasswordParts.size == 1) {
                _username = normalizeComponent(usernamePasswordParts[0])
            } else if (usernamePasswordParts.size == 2) {
                _username = normalizeComponent(usernamePasswordParts[0])
                _password = normalizeComponent(usernamePasswordParts[1])
            }
        }
    }

    /**
     * @param urlPart The url part we are checking for existence
     * @return Returns true if the part exists.
     */
    private fun exists(urlPart: UrlPart?): Boolean = urlPart != null && urlMarker.indexOf(urlPart) >= 0

    /**
     * For example, in http://yahoo.com/lala/, nextExistingPart(UrlPart.HOST) would return UrlPart.PATH
     * @param urlPart The current url part
     * @return Returns the next part; if there is no existing next part, it returns null
     */
    private fun nextExistingPart(urlPart: UrlPart): UrlPart? {
        val nextPart = urlPart.nextPart
        if (exists(nextPart)) {
            return nextPart
        } else if (nextPart == null) {
            return null
        } else {
            return nextExistingPart(nextPart)
        }
    }

    /**
     * @param part The part that we want. Ex: host, path
     */
    private fun getPart(part: UrlPart): String? {
        if (!exists(part)) {
            return null
        }

        val startIndex = urlMarker.indexOf(part)
        if (startIndex < 0 || startIndex >= originalUrl.length) {
            return null
        }

        val nextPart = nextExistingPart(part)
        return if (nextPart == null) {
            originalUrl.substring(startIndex)
        } else {
            val endIndex = urlMarker.indexOf(nextPart)
            originalUrl.substring(startIndex, minOf(endIndex, originalUrl.length))
        }
    }

    /**
     * Removes dot segments from the given path per
     * [RFC 3986 §5.2.4](https://www.rfc-editor.org/rfc/rfc3986#section-5.2.4).
     */
    fun removeDotSegments(path: String): String {
        var input = path
        val output = StringBuilder()

        while (input.isNotEmpty()) {
            when {
                // A: Remove leading "../" or "./"
                input.startsWith("../") -> {
                    input = input.substring(3)
                }

                input.startsWith("./") -> {
                    input = input.substring(2)
                }

                // B: Replace leading "/./" or "/." (end) with "/"
                input.startsWith("/./") -> {
                    input = "/" + input.substring(3)
                }

                input == "/." -> {
                    input = "/"
                }

                // C: Replace leading "/../" or "/.." (end) with "/" and drop last output segment
                input.startsWith("/../") -> {
                    input = "/" + input.substring(4)
                    dropLastSegment(output)
                }

                input == "/.." -> {
                    input = "/"
                    dropLastSegment(output)
                }

                // D: Input is just "." or ".."
                input == "." || input == ".." -> {
                    input = ""
                }

                // E: Move the first path segment to output
                else -> {
                    val startIdx = if (input.startsWith("/")) 1 else 0
                    val idx = input.indexOf('/', startIdx)
                    val segEnd = if (idx == -1) input.length else idx
                    output.append(input, 0, segEnd)
                    input = input.substring(segEnd)
                }
            }
        }

        return output.toString()
    }

    /**
     * Removes the last segment and its preceding "/" from the output buffer.
     * For example, "/a/b" becomes "/a" and "/a" becomes "".
     */
    private fun dropLastSegment(output: StringBuilder) {
        val lastSlash = output.lastIndexOf('/')
        if (lastSlash >= 0) {
            output.deleteRange(lastSlash, output.length)
        } else {
            output.clear()
        }
    }

    /**
     * Unreserved characters per RFC 3986 §2.3:
     * ALPHA / DIGIT / "-" / "." / "_" / "~"
     */
    private fun isUnreserved(c: Char): Boolean = c.isLetter() || c.isDigit() || c == '-' || c == '.' || c == '_' || c == '~'

    /**
     * Normalize percent-encoded triplets in a single URI component string.
     *
     * For each %XX triplet:
     * - If the decoded byte is an unreserved ASCII character → decode it
     * - Otherwise → keep encoded but uppercase the hex digits
     *
     * Non-ASCII bytes (e.g. UTF-8 multi-byte sequences) are left encoded
     * since they cannot be unreserved characters.
     */
    fun normalizeComponent(input: String): String {
        val sb = StringBuilder(input.length)
        var i = 0

        while (i < input.length) {
            val c = input[i]

            if (c == '%' && i + 2 < input.length) {
                val hex = input.substring(i + 1, i + 3)

                // Validate that both characters are valid hex digits
                if (hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                    val byteValue = hex.toInt(16)

                    // Only consider single-byte ASCII values for potential decoding
                    if (byteValue < 0x80) {
                        val decoded = byteValue.toChar()
                        if (isUnreserved(decoded)) {
                            // Decode: replace %XX with the literal character
                            sb.append(decoded)
                            i += 3
                            continue
                        }
                    }

                    // Keep encoded, but uppercase the hex digits
                    sb.append('%')
                    sb.append(hex.uppercase())
                    i += 3
                    continue
                }
            }

            // Regular character — pass through as-is
            sb.append(c)
            i++
        }

        return sb.toString()
    }

    /**
     * Lowercase only the literal (non-percent-encoded) characters in a string.
     * Percent-encoded triplets are left untouched so their uppercased hex digits
     * are not inadvertently lowercased.
     */
    private fun lowercaseLiteralChars(input: String): String {
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            if (input[i] == '%' && i + 2 < input.length) {
                sb.append(input[i])
                sb.append(input[i + 1])
                sb.append(input[i + 2])
                i += 3
            } else {
                sb.append(input[i].lowercaseChar())
                i++
            }
        }
        return sb.toString()
    }

    companion object {
        private const val DEFAULT_SCHEME = "https"
        private val SCHEME_PORT_MAP: Map<String, Int> =
            mapOf(
                "http" to 80,
                "https" to 443,
                "ftp" to 21,
            )
    }
}
