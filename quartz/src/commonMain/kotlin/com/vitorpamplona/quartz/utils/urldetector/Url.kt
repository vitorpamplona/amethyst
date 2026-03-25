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
                } else if (!originalUrl.startsWith("//")) {
                    _scheme =
                        DEFAULT_SCHEME
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
                this.rawHost = getPart(UrlPart.HOST)
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
                val portString =
                    getPart(UrlPart.PORT)
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
                this.rawPath =
                    if (exists(UrlPart.PATH)) {
                        getPart(
                            UrlPart.PATH,
                        )
                    } else {
                        "/"
                    }
            }
            return this.rawPath
        }

    val query: String
        get() {
            if (_query == null) {
                _query = getPart(UrlPart.QUERY)
            }
            return _query ?: ""
        }

    val fragment: String
        get() {
            if (_fragment == null) {
                _fragment = getPart(UrlPart.FRAGMENT)
            }
            return _fragment ?: ""
        }

    private fun populateUsernamePassword() {
        val usernamePassword = getPart(UrlPart.USERNAME_PASSWORD)
        if (usernamePassword != null) {
            val usernamePasswordParts: List<String> =
                usernamePassword.substring(0, usernamePassword.length - 1).split(":")
            if (usernamePasswordParts.size == 1) {
                _username = usernamePasswordParts[0]
            } else if (usernamePasswordParts.size == 2) {
                _username = usernamePasswordParts[0]
                _password = usernamePasswordParts[1]
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
