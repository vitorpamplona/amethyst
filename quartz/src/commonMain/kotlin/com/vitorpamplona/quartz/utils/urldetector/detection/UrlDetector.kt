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
package com.vitorpamplona.quartz.utils.urldetector.detection

import com.vitorpamplona.quartz.utils.urldetector.Url
import com.vitorpamplona.quartz.utils.urldetector.UrlMarker
import com.vitorpamplona.quartz.utils.urldetector.UrlPart
import com.vitorpamplona.quartz.utils.urldetector.detection.DomainNameReader.Companion.INTERNATIONAL_CHAR_START
import kotlin.math.max
import kotlin.text.deleteRange

class UrlDetector(
    content: String,
) {
    /**
     * The input stream to read.
     */
    private val reader: InputTextReader = InputTextReader(content)

    /**
     * Buffer to store temporary urls inside of.
     */
    private val buffer = StringBuilder()

    /**
     * Has the scheme been found in this iteration?
     */
    private var hasScheme = false

    /**
     * has Multi-level labels
     */
    private var isSingleLevelLabel = false

    /**
     * Stores the found urls.
     */
    private val urlList = mutableListOf<Url>()

    /**
     * Keeps track of certain indices to create a Url object.
     */
    private var currentUrlMarker: UrlMarker = UrlMarker()

    /**
     * The states to use to continue writing or not.
     */
    enum class ReadEndState {
        ValidUrl,
        InvalidUrl,
    }

    /**
     * Detects the urls and returns a list of detected url strings.
     * @return A list with detected urls.
     */
    fun detect(): List<Url> {
        readDefault()
        return urlList
    }

    /**
     * The default input reader which looks for specific flags to start detecting the url.
     */
    private fun readDefault() {
        // Keeps track of the number of characters read to be able to later cut out the domain name.
        var length = 0
        var position = 0

        var lastWasAscii: Boolean? = null
        var isAscii = false

        // until end of string read the contents
        while (!reader.eof()) {
            // read the next char to process.
            when (val curr = reader.read()) {
                ' ' -> {
                    // space found; if we have a scheme, attempt to read the domain before resetting
                    if (buffer.isNotEmpty() && hasScheme) {
                        reader.goBack()
                        readDomainName(buffer.substring(length))
                    }
                    readEnd(ReadEndState.InvalidUrl)
                    length = 0
                    lastWasAscii = null
                }

                '%' -> {
                    if (reader.canReadChars(2)) {
                        if (reader.peekEquals("3a") || reader.peekEquals("3A")) {
                            buffer.append(curr)
                            buffer.append(reader.read())
                            buffer.append(reader.read())
                            length = processColon(length)
                        } else if (CharUtils.isHex(reader.peekChar(0)) && CharUtils.isHex(reader.peekChar(1))) {
                            buffer.append(curr)
                            buffer.append(reader.read())
                            buffer.append(reader.read())

                            if (!readDomainName(buffer.substring(length))) {
                                readEnd(ReadEndState.InvalidUrl)
                            }
                            length = 0
                        }
                    }
                    lastWasAscii = null
                }

                '\u3002', '\uFF0E', '\uFF61', '.' -> {
                    buffer.append(curr)
                    val domain = buffer.substring(length)
                    if (!readDomainName(domain)) {
                        readEnd(ReadEndState.InvalidUrl)
                    }
                    length = 0
                    lastWasAscii = null
                }

                '@' -> {
                    if (buffer.isNotEmpty()) {
                        currentUrlMarker.setIndex(UrlPart.USERNAME_PASSWORD, length)
                        buffer.append(curr)
                        if (!readDomainName(null)) {
                            readEnd(ReadEndState.InvalidUrl)
                        }
                        length = 0
                    }
                    lastWasAscii = null
                }

                '[' -> {
                    val beginning = reader.position

                    // if it doesn't have a scheme, clear the buffer.
                    if (!hasScheme) {
                        buffer.clear()
                    }
                    buffer.append(curr)

                    if (!readDomainName(buffer.substring(length))) {
                        // if we didn't find an ipv6 address, then check inside the brackets for urls
                        readEnd(ReadEndState.InvalidUrl)
                        reader.seek(beginning)
                    }
                    length = 0
                    lastWasAscii = null
                }

                '/' -> {
                    // "/" was found, then we either read a scheme, or if we already read a scheme, then
                    // we are reading a url in the format http://123123123/asdf
                    if (hasScheme || buffer.length > 1) {
                        // we already have the scheme, so then we already read:
                        // http://something/ <- if something is all numeric then its a valid url.
                        // OR we are searching for single level domains. We have buffer length > 1 condition
                        // to weed out infinite backtrack in cases of html5 roots

                        // unread this "/" and continue to check the domain name starting from the beginning of the domain

                        reader.goBack()
                        if (!readDomainName(buffer.substring(length))) {
                            readEnd(ReadEndState.InvalidUrl)
                        }
                        length = 0
                    } else {
                        // we don't have a scheme already, then clear state, then check for html5 root such as: "//google.com/"
                        // remember the state of the quote when clearing state just in case its "//google.com" so its not cleared.

                        readEnd(ReadEndState.InvalidUrl)
                        buffer.append(curr)
                        hasScheme = readHtml5Root()
                        length = buffer.length
                    }
                    lastWasAscii = null
                }

                ':' -> {
                    // add the ":" to the url and check for scheme/username
                    buffer.append(curr)
                    length = processColon(length)
                    lastWasAscii = null
                }

                else -> {
                    isAscii = curr.code < INTERNATIONAL_CHAR_START
                    if (lastWasAscii == null) {
                        lastWasAscii = isAscii
                    } else if (isAscii != lastWasAscii) {
                        // threat changes in char as a space
                        if (buffer.isNotEmpty() && hasScheme) {
                            reader.goBack()
                            readDomainName(buffer.substring(length))
                        }
                        length = 0
                    }

                    buffer.append(curr)
                }
            }

            if (position == reader.position) {
                // we haven't made any progress, advance by one char
                reader.read()
            }

            position = reader.position
        }

        // check if it's a valid single level domain.
        if (buffer.isNotEmpty() && hasScheme) {
            if (!readDomainName(buffer.substring(length))) {
                readEnd(ReadEndState.InvalidUrl)
            }
        }
    }

    /**
     * We found a ":" and is now trying to read either scheme, username/password
     * @param startLength first index of the previous part (could be beginning of the buffer, beginning of the username/password, or beginning
     * @return new index of where the domain starts
     */
    private fun processColon(startLength: Int): Int {
        var length = startLength
        if (hasScheme) {
            // read it as username/password if it has scheme
            if (!readUserPass(length)) {
                // unread the ":" so that the domain reader can process it
                reader.goBack()

                // Check buffer length before clearing it; set length to 0 if buffer is empty
                if (buffer.isNotEmpty()) {
                    buffer.deleteRange(buffer.length - 1, buffer.length)
                } else {
                    length = 0
                }

                val backtrackOnFail: Int = reader.position - buffer.length + length

                if (!readDomainName(buffer.substring(length))) {
                    // go back to length location and restart search
                    reader.seek(backtrackOnFail)
                    readEnd(ReadEndState.InvalidUrl)
                }
                length = 0
            } else {
                length = 0
            }
        } else if (readScheme() && buffer.isNotEmpty()) {
            hasScheme = true
            length = buffer.length // set length to be right after the scheme
        } else if (buffer.isNotEmpty() && reader.canReadChars(1)) {
            // takes care of case like hi:
            reader.goBack() // unread the ":" so readDomainName can take care of the port
            buffer.deleteAt(buffer.length - 1)
            if (!readDomainName(buffer.toString())) {
                readEnd(ReadEndState.InvalidUrl)
            }
        } else {
            readEnd(ReadEndState.InvalidUrl)
            length = 0
        }

        return length
    }

    /**
     * Checks if the url is in the format:
     * //google.com/static/js.js
     * @return True if the url is in this format and was matched correctly.
     */
    private fun readHtml5Root(): Boolean {
        // end of input then go away.
        if (reader.eof()) {
            return false
        }

        // read the next character. If its // then return true.
        val curr = reader.read()
        if (curr == '/') {
            buffer.append(curr)
            return true
        } else {
            // if its not //, then go back and reset by 1 character.
            reader.goBack()
            readEnd(ReadEndState.InvalidUrl)
        }
        return false
    }

    /**
     * Reads the scheme and allows returns true if the scheme is http(s?):// or ftp(s?)://
     * @return True if the scheme was found, else false.
     */
    private fun readScheme(): Boolean {
        val originalLength: Int = buffer.length
        var numSlashes = 0

        while (!reader.eof()) {
            val curr = reader.read()

            // if we match a slash, look for a second one.
            if (curr == '/') {
                buffer.append(curr)
                if (numSlashes == 1) {
                    // return only if its an approved protocol. This can be expanded to allow others
                    val schemeStartIndex: Int = findValidSchemeStartIndex(buffer.toString())
                    if (schemeStartIndex >= 0) {
                        buffer.deleteRange(0, schemeStartIndex)
                        currentUrlMarker.setIndex(UrlPart.SCHEME, 0)
                        return true
                    } else {
                        return false
                    }
                }
                numSlashes++
            } else if (curr == ' ') {
                // if we find a space or end of input, then nothing found.
                buffer.append(curr)
                return false
            } else if (curr == '[') { // if we're starting to see an ipv6 address
                reader.goBack() // unread the '[', so that we can start looking for ipv6
                return false
            } else if (originalLength > 0 && numSlashes == 0 && CharUtils.isAlpha(curr)) {
                // If we had already read something before the : and we are matching regardless of slashes, assume it's a scheme

                // Add the slashes to the end of the scheme so it matches what's in the scheme list
                val schemeStartIndex = findValidSchemeNoSlashesStartIndex(buffer.toString())
                if (schemeStartIndex >= 0) {
                    if (schemeStartIndex > 0) {
                        buffer.deleteRange(0, schemeStartIndex)
                    }
                    currentUrlMarker.setIndex(UrlPart.SCHEME, 0)
                    reader.goBack()
                    return true
                } else {
                    reader.goBack()
                    return readUserPass(0)
                }
                // If this didn't match a defined scheme, continue processing as usual
            } else if (originalLength > 0 || numSlashes > 0 || !CharUtils.isAlpha(curr)) {
                // if it's not a character a-z or A-Z then assume we aren't matching scheme, but instead
                // matching username and password.
                // Add the slashes to the end of the scheme so it matches what's in the scheme list
                val schemeStartIndex = findValidSchemeNoSlashesStartIndex(buffer.toString())
                if (schemeStartIndex >= 0) {
                    if (schemeStartIndex > 0) {
                        buffer.deleteRange(0, schemeStartIndex)
                    }
                    currentUrlMarker.setIndex(UrlPart.SCHEME, 0)
                    reader.goBack()
                    return true
                }

                reader.goBack()
                return readUserPass(0)
            }
        }

        return false
    }

    private fun findValidSchemeStartIndex(optionalScheme: String): Int {
        val optionalSchemeLowercase = optionalScheme.lowercase()
        return VALID_SCHEMES
            .filter(optionalSchemeLowercase::endsWith)
            .map(optionalSchemeLowercase::lastIndexOf)
            .firstOrNull() ?: -1
    }

    private fun findValidSchemeNoSlashesStartIndex(optionalScheme: String): Int {
        val optionalSchemeLowercase = optionalScheme.lowercase()
        return VALID_SCHEMES_NO_SLASHES
            .filter(optionalSchemeLowercase::endsWith)
            .map(optionalSchemeLowercase::lastIndexOf)
            .firstOrNull() ?: -1
    }

    /**
     * Reads the input and looks for a username and password.
     * Handles:
     * http://username:password@...
     * @param beginningOfUsername Index of the buffer of where the username began
     * @return True if a valid username and password was found.
     */
    private fun readUserPass(beginningOfUsername: Int): Boolean {
        // The start of where we are.
        val start: Int = buffer.length

        // keep looping until "done"
        var done = false

        // if we had a dot in the input, then it might be a domain name and not a username and password.
        var rollback = false
        while (!done && !reader.eof()) {
            val curr = reader.read()

            // if we hit this, then everything is ok and we are matching a domain name.
            if (curr == '@') {
                buffer.append(curr)
                currentUrlMarker.setIndex(UrlPart.USERNAME_PASSWORD, beginningOfUsername)
                return readDomainName("")
            } else if (CharUtils.isDot(curr) || curr == '[') {
                // everything is still ok, just remember that we found a dot or '[' in case we might need to backtrack
                buffer.append(curr)
                rollback = true
            } else if (curr == '#' || curr == ' ' || curr == '/') {
                // one of these characters indicates we are invalid state and should just return.
                rollback = true
                done = true
            } else {
                // all else, just append character assuming its ok so far.
                buffer.append(curr)
            }
        }

        if (rollback || !done) {
            // got to here, so there is no username and password. (We didn't find a @)
            val distance: Int = buffer.length - start
            buffer.deleteRange(start, buffer.length)

            val currIndex: Int = max(reader.position - distance - (if (done) 1 else 0), 0)
            reader.seek(currIndex)

            return false
        } else {
            return readEnd(ReadEndState.InvalidUrl)
        }
    }

    /**
     * Try to read the current string as a domain name
     * @param current The current string used.
     * @return Whether the domain is valid or not.
     */
    private fun readDomainName(current: String?): Boolean {
        val hostIndex: Int =
            if (current == null) buffer.length else buffer.length - current.length

        currentUrlMarker.setIndex(UrlPart.HOST, hostIndex)

        // create the domain name reader and specify the handler that will be called when a quote character
        // or something is found.
        val reader = DomainNameReader(reader, buffer, current)

        // Try to read the dns and act on the response.
        val state = reader.readDomainName()

        isSingleLevelLabel = reader.labelCount() <= 1 && !reader.isIpV4 && !reader.isIpV6

        return when (state) {
            DomainNameReader.ReaderNextState.ValidDomainName -> {
                readEnd(ReadEndState.ValidUrl)
            }

            DomainNameReader.ReaderNextState.ReadFragment -> {
                readFragment()
            }

            DomainNameReader.ReaderNextState.ReadPath -> {
                readPath()
            }

            DomainNameReader.ReaderNextState.ReadPort -> {
                readPort()
            }

            DomainNameReader.ReaderNextState.ReadQueryString -> {
                readQueryString()
            }

            DomainNameReader.ReaderNextState.ReadUserPass -> {
                val host: Int = currentUrlMarker.indexOf(UrlPart.HOST)
                currentUrlMarker.unsetIndex(UrlPart.HOST)
                readUserPass(host)
            }

            else -> {
                false
            }
        }
    }

    /**
     * Reads the fragments which is the part of the url starting with #
     * @return If a valid fragment was read true, else false.
     */
    private fun readFragment(): Boolean {
        currentUrlMarker.setIndex(UrlPart.FRAGMENT, buffer.length - 1)

        while (!reader.eof()) {
            val curr = reader.read()

            // if it's the end or space, then a valid url was read.
            if (curr == ' ') {
                return readEnd(ReadEndState.ValidUrl)
            } else {
                // otherwise keep appending.
                buffer.append(curr)
            }
        }

        // if we are here, anything read is valid.
        return readEnd(ReadEndState.ValidUrl)
    }

    /**
     * Try to read the query string.
     * @return True if the query string was valid.
     */
    private fun readQueryString(): Boolean {
        currentUrlMarker.setIndex(UrlPart.QUERY, buffer.length - 1)

        while (!reader.eof()) {
            val curr = reader.read()

            if (curr == '#') { // fragment
                buffer.append(curr)
                return readFragment()
            } else if (curr == ' ') {
                // end of query string
                return readEnd(ReadEndState.ValidUrl)
            } else { // all else add to buffer.
                buffer.append(curr)
            }
        }
        // a valid url was read.
        return readEnd(ReadEndState.ValidUrl)
    }

    /**
     * Try to read the port of the url.
     * @return True if a valid port was read.
     */
    private fun readPort(): Boolean {
        currentUrlMarker.setIndex(UrlPart.PORT, buffer.length)
        // The length of the port read.
        var portLen = 0
        while (!reader.eof()) {
            // read the next one and remember the length
            val curr = reader.read()

            // requires at least one number as port to
            // better handle http://http://
            if (portLen == 0 && isSingleLevelLabel) {
                if (!CharUtils.isNumeric(curr)) {
                    reader.goBack()

                    currentUrlMarker.unsetIndex(UrlPart.PORT)
                    return readEnd(ReadEndState.InvalidUrl)
                }
            }

            portLen++

            if (curr == ':') {
                // rejects a second port
                reader.goBack()

                currentUrlMarker.unsetIndex(UrlPart.PORT)
                return readEnd(ReadEndState.InvalidUrl)
            } else if (curr == '/') {
                // continue to read path
                buffer.append(curr)
                return readPath()
            } else if (curr == '?') {
                // continue to read query string
                buffer.append(curr)
                return readQueryString()
            } else if (curr == '#') {
                // continue to read fragment.
                buffer.append(curr)
                return readFragment()
            } else if (!CharUtils.isNumeric(curr)) {
                // if we got here, then what we got so far is a valid url. don't append the current character.
                reader.goBack()

                // no port found; it was something like google.com:hello.world
                if (portLen == 1) {
                    // remove the ":" from the end.
                    buffer.deleteRange(buffer.length - 1, buffer.length)
                }
                currentUrlMarker.unsetIndex(UrlPart.PORT)
                return readEnd(ReadEndState.ValidUrl)
            } else {
                // this is a valid character in the port string.
                buffer.append(curr)
            }
        }

        // found a correct url
        return readEnd(ReadEndState.ValidUrl)
    }

    /**
     * Tries to read the path
     * @return True if the path is valid.
     */
    private fun readPath(): Boolean {
        currentUrlMarker.setIndex(UrlPart.PATH, buffer.length - 1)

        var endsOnASlash = true

        while (!reader.eof()) {
            // read the next char
            val curr = reader.read()

            if (curr == ' ') {
                // if end of state and we got here, then the url is valid
                // if it is not just a word/word
                if (
                    currentUrlMarker.hasScheme() ||
                    currentUrlMarker.hasPort() ||
                    currentUrlMarker.hasUsernamePassword() ||
                    !isSingleLevelLabel ||
                    endsOnASlash
                ) {
                    return readEnd(ReadEndState.ValidUrl)
                } else {
                    return readEnd(ReadEndState.InvalidUrl)
                }
            }

            // append the char
            buffer.append(curr)

            // now see if we move to another state.
            if (curr == '?') {
                // if ? read query string
                return readQueryString()
            } else if (curr == '#') {
                // if # read the fragment
                return readFragment()
            }

            endsOnASlash = curr == '/'
        }

        // end of input then this url is good.
        // if end of state and we got here, then the url is valid
        // if it is not just a word/word
        // no need to check for query and fragments
        // here we accept urls that end in /
        if (
            currentUrlMarker.hasScheme() ||
            currentUrlMarker.hasPort() ||
            currentUrlMarker.hasUsernamePassword() ||
            !isSingleLevelLabel ||
            endsOnASlash
        ) {
            return readEnd(ReadEndState.ValidUrl)
        } else {
            return readEnd(ReadEndState.InvalidUrl)
        }
    }

    /**
     * The url has been read to here. Remember the url if its valid, and reset state.
     * @param state The state indicating if this url is valid. If its valid it will be added to the list of urls.
     * @return True if the url was valid.
     */
    private fun readEnd(state: ReadEndState?): Boolean {
        // if the url is valid and greater then 0
        if (state == ReadEndState.ValidUrl && buffer.isNotEmpty()) {
            var url = buffer.toString()
            if (url.lastOrNull() in CANNOT_END_URLS_WITH) url = url.dropLast(1)
            urlList.add(currentUrlMarker.createUrl(url))
        }

        // clear out the buffer.
        buffer.clear()

        // reset the state of internal objects.
        hasScheme = false
        isSingleLevelLabel = false
        if (currentUrlMarker.hasChanged) {
            currentUrlMarker = UrlMarker()
        }

        // return true if valid.
        return state == ReadEndState.ValidUrl
    }

    companion object {
        val VALID_SCHEMES_NO_SLASHES: List<String> =
            listOf(
                "http:",
                "https:",
                "ftp:",
                "ftps:",
                "ws:",
                "wss:",
                "nostr:",
                "blossom:",
            )

        val VALID_SCHEMES =
            VALID_SCHEMES_NO_SLASHES.map {
                "$it//"
            }

        val CANNOT_BEGIN_URLS_WITH =
            setOf(
                ',',
                '.',
                ';',
                '?',
                '!',
                ')',
                '}',
                '(',
                '{',
                '\u3002',
                '\uFF0E',
                '\uFF61',
            )

        val CANNOT_END_URLS_WITH =
            setOf(
                ',',
                '.',
                ';',
                '?',
                '!',
                ':',
                ')',
                '}',
                '(',
                '{',
                '\u3002',
                '\uFF0E',
                '\uFF61',
            )
    }
}
