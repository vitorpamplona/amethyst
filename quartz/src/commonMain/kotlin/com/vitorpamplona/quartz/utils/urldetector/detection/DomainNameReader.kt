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

import com.vitorpamplona.quartz.utils.urldetector.detection.CharUtils.isAlpha
import com.vitorpamplona.quartz.utils.urldetector.detection.CharUtils.isAlphaNumeric
import com.vitorpamplona.quartz.utils.urldetector.detection.CharUtils.isDot
import com.vitorpamplona.quartz.utils.urldetector.detection.CharUtils.isHex
import com.vitorpamplona.quartz.utils.urldetector.detection.CharUtils.isNumeric
import com.vitorpamplona.quartz.utils.urldetector.detection.CharUtils.isUnreserved
import com.vitorpamplona.quartz.utils.urldetector.detection.CharUtils.splitByDot
import kotlin.math.max
import kotlin.math.min

/** Returns true if the string is a percent-encoded dot (%2e or %2E). */
private fun String.isDotPercent() = this == "%2e" || this == "%2E"

/** Returns true if the string is an internationalized domain label using Punycode (xn-- prefix). */
private fun String.isXn() =
    this.length > 3 &&
        (this[0] == 'x' || this[0] == 'X') &&
        (this[1] == 'n' || this[1] == 'N') &&
        this[2] == '-' &&
        this[3] == '-'

/**
 * The domain name reader reads input from a InputTextReader and validates if the content being read is a valid domain name.
 * After a domain name is read, the returning status is what to do next. If the domain is valid but a specific character is found,
 * the next state will be to read another part for the rest of the url. For example, if a "?" is found at the end and the
 * domain is valid, the return state will be to read a query string.
 */
class DomainNameReader(
    val reader: InputTextReader,
    /**
     * The currently written string buffer.
     */
    val buffer: StringBuilder,
    /**
     * The domain name started with a partial domain name found. This is the original string of the domain name only.
     */
    val current: String?,
) {
    /**
     * This is the final return state of reading a domain name.
     */
    enum class ReaderNextState {
        /**
         * Trying to read the domain name caused it to be invalid.
         */
        InvalidDomainName,

        /**
         * The domain name is found to be valid.
         */
        ValidDomainName,

        /**
         * Finished reading, next step should be to read the fragment.
         */
        ReadFragment,

        /**
         * Finished reading, next step should be to read the path.
         */
        ReadPath,

        /**
         * Finished reading, next step should be to read the port.
         */
        ReadPort,

        /**
         * Finished reading, next step should be to read the query string.
         */
        ReadQueryString,

        /**
         * This was actually not a domain at all.
         */
        ReadUserPass,
    }

    /**
     * Keeps track the number of dots that were found in the domain name.
     */
    private var dots = 0

    /**
     * Keeps track of the number of characters since the last "."
     */
    private var currentLabelLength = 0

    /**
     * Keeps track of the number of characters in the top level domain.
     */
    private var topLevelLength = 0

    /**
     * Keeps track where the domain name started. This is non zero if the buffer starts with
     * http://username:password@...
     */
    private var startDomainName = 0

    /**
     * Keeps track if the entire domain name is numeric.
     */
    private var numeric = false

    /**
     * Keeps track if we are seeing an ipv6 type address.
     */
    private var seenBracket = false

    /**
     * Keeps track if we have seen a full bracket set "[....]"; used for ipv6 type address.
     */
    private var seenCompleteBracketSet = false

    /**
     * Keeps track if we have a zone index in the ipv6 address.
     */
    private var zoneIndex = false

    var isIpV4 = false
        private set

    var isIpV6 = false
        private set

    /**
     * Reads and parses the current string to make sure the domain name started where it was supposed to,
     * and the current domain name is correct.
     * @return The next state to use after reading the current.
     */
    private fun readCurrent(): ReaderNextState {
        if (current != null) {
            // Handles the case where the string is ".hello"
            if (current.length == 1 && isDot(current[0])) {
                return ReaderNextState.InvalidDomainName
            } else if (current.length == 3 && current.isDotPercent()) {
                return ReaderNextState.InvalidDomainName
            }

            // The location where the domain name started.
            startDomainName = buffer.length - current.length

            // flag that the domain is currently all numbers and/or dots.
            numeric = true

            // If an invalid char is found, we can just restart the domain from there.
            var newStart = 0

            val currArray = current.toCharArray()
            val length = currArray.size

            // hex special case
            var isAllHexSoFar =
                length > 2 && (currArray[0] == '0' && (currArray[1] == 'x' || currArray[1] == 'X'))

            var lastWasAscii = length > 0 && currArray[0].code < INTERNATIONAL_CHAR_START

            var index = if (isAllHexSoFar) 2 else 0
            var done = false
            var isAscii = false

            while (index < length && !done) {
                // get the current character and update length counts.
                val curr = currArray[index]
                isAscii = curr.code < INTERNATIONAL_CHAR_START

                currentLabelLength++
                topLevelLength = currentLabelLength

                // Is the length of the last part > 64 (plus one since we just incremented)
                if (currentLabelLength > MAX_LABEL_LENGTH) {
                    return ReaderNextState.InvalidDomainName
                } else if (isDot(curr)) {
                    // found a dot. Increment dot count, and reset last length
                    dots++
                    currentLabelLength = 0
                } else if (curr == '[') {
                    seenBracket = true
                    numeric = false
                } else if (curr == '%' && index + 2 < length && isHex(currArray[index + 1]) && isHex(currArray[index + 2])) {
                    // handle url encoded dot
                    if (currArray[index + 1] == '2' && currArray[index + 2] == 'e') {
                        dots++
                        currentLabelLength = 0
                    } else {
                        numeric = false
                    }
                    index += 2
                } else if (isAllHexSoFar) {
                    // if it's a valid character in the domain that is not numeric
                    if (!isHex(curr)) {
                        numeric = false
                        isAllHexSoFar = false
                        index-- // backtrack to rerun last character knowing it isn't hex.
                    }
                } else if (isAscii == lastWasAscii && (isAlpha(curr) || curr == '-' || !isAscii)) {
                    // we don't  allow mixed domains: doesn't come here if it changed form ascii to not ascii.
                    numeric = false
                    lastWasAscii = isAscii
                } else if (isAscii != lastWasAscii) {
                    // if its not _numeric and not alphabetical, then restart searching for a domain from this point.
                    newStart = index
                    currentLabelLength = 0
                    topLevelLength = 0
                    numeric = true
                    dots = 0
                    // done = true

                    lastWasAscii = isAscii
                } else if (index == 0) {
                    if (curr in UrlDetector.CANNOT_BEGIN_URLS_WITH) {
                        newStart = index + 1
                        currentLabelLength = 0
                        topLevelLength = 0
                        numeric = true
                        dots = 0
                    }
                }
                index++
            }

            // An invalid character for the domain was found somewhere in the current buffer.
            // cut the first part of the domain out. For example:
            // http://asdf%asdf.google.com <- asdf.google.com is still valid, so restart from the %
            if (newStart > 0) {
                // make sure the location is not at the end. Otherwise the thing is just invalid.

                if (newStart < current.length) {
                    buffer.clear()
                    buffer.append(current.substring(newStart))

                    // cut out the previous part, so now the domain name has to be from here.
                    startDomainName = 0
                }

                // now after cutting if the buffer is just "." newStart > current (last character in current is invalid)
                if (newStart >= current.length || buffer.toString() == ".") {
                    return ReaderNextState.InvalidDomainName
                }
            }
        } else {
            startDomainName = buffer.length
        }

        // all else is good, return OK
        return ReaderNextState.ValidDomainName
    }

    /**
     * Reads the Dns and returns the next state the state machine should take in throwing this out, or continue processing
     * if this is a valid domain name.
     * @return The next state to take.
     */
    fun readDomainName(): ReaderNextState? {
        // Read the current, and if its bad, just return.

        if (readCurrent() == ReaderNextState.InvalidDomainName) {
            return ReaderNextState.InvalidDomainName
        }

        // while not done and not end of string keep reading.
        var done = false

        // If this is the first domain part, check if it's ip address in is hexa
        // similar to what is done on 'readCurrent' method
        val isAllHexSoFar =
            (current == null || current == "") &&
                reader.canReadChars(3) &&
                (reader.peekEquals("0x") || reader.peekEquals("0X"))

        if (isAllHexSoFar) {
            // Append hexa radix symbol characters (0x)
            buffer.append(reader.read())
            buffer.append(reader.read())
            currentLabelLength += 2
            topLevelLength = currentLabelLength
        }

        var lastWasAscii: Boolean? =
            if (current.isNullOrEmpty()) {
                null
            } else {
                val last = current.last()
                if (isDot(last)) {
                    null
                } else {
                    last.code < INTERNATIONAL_CHAR_START
                }
            }
        var isAscii = false

        while (!done && !reader.eof()) {
            val curr: Char = reader.read()

            isAscii = curr.code < INTERNATIONAL_CHAR_START
            if (lastWasAscii == null) {
                lastWasAscii = isAscii
            }

            if (curr == '/') {
                // continue by reading the path
                return checkDomainNameValid(ReaderNextState.ReadPath, curr)
            } else if (curr == ':' && (!seenBracket || seenCompleteBracketSet)) {
                // Don't check for a port if it's in the middle of an ipv6 address
                // continue by reading the port.
                return checkDomainNameValid(ReaderNextState.ReadPort, curr)
            } else if (curr == '?') {
                // continue by reading the query string
                return checkDomainNameValid(ReaderNextState.ReadQueryString, curr)
            } else if (curr == '#') {
                // continue by reading the fragment
                return checkDomainNameValid(ReaderNextState.ReadFragment, curr)
            } else if (curr == '@') {
                // this may not have been a domain after all, but rather a username/password instead
                reader.goBack()
                return ReaderNextState.ReadUserPass
            } else if (isDot(curr) || (curr == '%' && (reader.peekEquals("2e") || reader.peekEquals("2E")))) {
                // if the current character is a dot or a urlEncodedDot

                // handles the case: hello..

                if (currentLabelLength < 1) {
                    done = true
                } else {
                    // append the "." to the domain name
                    buffer.append(curr)

                    // if it was not a normal dot, then it is url encoded
                    // read the next two chars, which are the hex representation
                    if (!isDot(curr)) {
                        buffer.append(reader.read())
                        buffer.append(reader.read())
                    }

                    // increment the dots only if it's not part of the zone index and reset the last length.
                    if (!zoneIndex) {
                        dots++
                        currentLabelLength = 0
                    }

                    // if the length of the last section is longer than or equal to 64, it's too long to be a valid domain
                    if (currentLabelLength >= MAX_LABEL_LENGTH) {
                        return ReaderNextState.InvalidDomainName
                    }
                }
            } else if (seenBracket && (isHex(curr) || curr == ':' || curr == '[' || curr == ']' || curr == '%') && !seenCompleteBracketSet) { // if this is an ipv6 address.
                when (curr) {
                    ':' -> {
                        currentLabelLength = 0
                    }

                    '[' -> {
                        // if we read another '[', we need to restart by re-reading from this bracket instead.
                        reader.goBack()
                        return ReaderNextState.InvalidDomainName
                    }

                    ']' -> {
                        seenCompleteBracketSet =
                            true // means that we already have a complete ipv6 address.
                        zoneIndex =
                            false // set this back off so that we can keep counting dots after ipv6 is over.
                    }

                    '%' -> {
                        zoneIndex = true
                    }

                    else -> {
                        currentLabelLength++
                    }
                }
                numeric = false
                buffer.append(curr)
            } else if (isAlphaNumeric(curr) || curr == '-' || curr.code >= INTERNATIONAL_CHAR_START) {
                // Valid domain name character. Either a-z, A-Z, 0-9, -, or international character
                if (seenCompleteBracketSet) {
                    // covers case of [fe80::]www.google.com
                    reader.goBack()
                    done = true
                } else {
                    if (isAllHexSoFar && !isHex(curr)) {
                        numeric = false
                    }
                    // if its not numeric, remember that;
                    if (!isAllHexSoFar && !isNumeric(curr)) {
                        numeric = false
                    }
                    if (isAscii != lastWasAscii) {
                        reader.goBack()
                        done = true
                    } else {
                        // append to the states.
                        buffer.append(curr)
                        currentLabelLength++
                        topLevelLength = currentLabelLength
                    }
                }

                lastWasAscii = isAscii
            } else if (curr == '[' && !seenBracket) {
                seenBracket = true
                numeric = false
                buffer.append(curr)
            } else if (curr == '[' && seenCompleteBracketSet) { // Case where [::][ ...
                reader.goBack()
                done = true
            } else if (curr == '%' && reader.canReadChars(2) && isHex(reader.peekChar(0)) && isHex(reader.peekChar(1))) {
                // append to the states.
                buffer.append(curr)
                buffer.append(reader.read())
                buffer.append(reader.read())
                currentLabelLength += 3
                topLevelLength = currentLabelLength
            } else {
                // invalid character, we are done.
                done = true
            }
        }

        // Check the domain name to make sure its ok.
        return checkDomainNameValid(ReaderNextState.ValidDomainName, null)
    }

    fun labelCount() = dots + (if (currentLabelLength > 0) 1 else 0)

    /**
     * Checks the current state of this object and returns if the valid state indicates that the
     * object has a valid domain name. If it does, it will return append the last character
     * and return the validState specified.
     * @param validState The state to return if this check indicates that the dns is ok.
     * @param lastChar The last character to add if the domain is ok.
     * @return The validState if the domain is valid, else ReaderNextState.InvalidDomainName
     */
    private fun checkDomainNameValid(
        validState: ReaderNextState?,
        lastChar: Char?,
    ): ReaderNextState? {
        var valid = false

        // Max domain length is 255 which includes the trailing "."
        // most of the time this is not included in the url.
        // If the _currentLabelLength is not 0 then the last "." is not included so add it.
        // Same with number of labels (or dots including the last)
        val lastDotLength =
            if (buffer.length > 3 &&
                buffer[buffer.length - 3] == '%' &&
                buffer[buffer.length - 2] == '2' &&
                (buffer[buffer.length - 1] == 'e' || buffer[buffer.length - 1] == 'E')
            ) {
                3
            } else {
                1
            }

        val domainLength: Int =
            buffer.length - startDomainName + (if (currentLabelLength > 0) lastDotLength else 0)
        val dotCount = dots + (if (currentLabelLength > 0) 1 else 0)
        if (domainLength >= MAX_DOMAIN_LENGTH || dotCount > MAX_NUMBER_LABELS) {
            valid = false
        } else if (numeric) {
            val testDomain = buffer.substring(startDomainName).lowercase()
            valid = isValidIpv4(testDomain)
            if (valid) {
                isIpV4 = true
            }
        } else if (seenBracket) {
            val testDomain = buffer.substring(startDomainName).lowercase()
            valid = isValidIpv6(testDomain)
            if (valid) {
                isIpV6 = true
            }
        } else if (buffer.isNotEmpty() && buffer.last() == ':') {
            valid = false
        } else if ((currentLabelLength > 0 && dots >= 1) || (dots >= 2 && currentLabelLength == 0) || (dots == 0)) {
            var topStart: Int = buffer.length - topLevelLength
            if (currentLabelLength == 0) {
                topStart--
            }
            topStart = max(topStart, 0)

            // get the first 4 characters of the top level domain
            val topLevelStart =
                buffer.substring(topStart, topStart + min(4, buffer.length - topStart))

            // There is no size restriction if the top level domain is international (starts with "xn--")
            valid = (topLevelStart.isXn() || topLevelLength >= MIN_TOP_LEVEL_DOMAIN)
        }

        if (valid) {
            // if it's valid, add the last character (if specified) and return the valid state.
            if (lastChar != null) {
                buffer.append(lastChar)
            }
            return validState
        }

        // return invalid state.
        return ReaderNextState.InvalidDomainName
    }

    /**
     * Handles Hexadecimal, octal, decimal, dotted decimal, dotted hex, dotted octal.
     * @param testDomain the string we're testing
     * @return Returns true if it's a valid ipv4 address
     */
    private fun isValidIpv4(testDomain: String): Boolean {
        var valid = false
        val length: Int = testDomain.length
        if (length > 0) {
            // handling format without dots. Ex: http://2123123123123/path/a, http://0x8242343/aksdjf
            if (dots == 0) {
                try {
                    val value: Long
                    if (length > 2 && testDomain[0] == '0' && testDomain[1] == 'x') { // hex
                        // digit must be within ['0', '9'] or ['A', 'F'] or ['a', 'f']
                        for (c in 2..<length) {
                            val d: Char = testDomain[c]
                            if ((d < '0' || (d in ':'..<'A') || (d in 'G'..<'a') || d > 'f')) {
                                return false
                            }
                        }
                        value = testDomain.substring(2).toLong(16)
                    } else if (testDomain[0] == '0') { // octal
                        // digit must be within ['0', '7']
                        for (c in 1..<length) {
                            val d: Char = testDomain[c]
                            if (d !in '0'..'7') {
                                return false
                            }
                        }
                        value = testDomain.substring(1).toLong(8)
                    } else { // decimal
                        // digit must be within ['0', '9']
                        for (c in 0..<length) {
                            val d: Char = testDomain[c]
                            if (d !in '0'..'9') {
                                return false
                            }
                        }
                        value = testDomain.toLong()
                    }
                    valid = value in MIN_NUMERIC_DOMAIN_VALUE..MAX_NUMERIC_DOMAIN_VALUE
                } catch (_: NumberFormatException) {
                    valid = false
                }
            } else if (dots == 3) {
                // Dotted decimal/hex/octal format
                val parts: List<String> = splitByDot(testDomain)
                valid = true

                // check each part of the ip and make sure its valid.
                var i = 0
                while (i < parts.size && valid) {
                    val part = parts[i]
                    val partLen: Int = part.length
                    if (partLen > 0) {
                        val parsedNum: String
                        val base: Int
                        if (partLen > 2 && part[0] == '0' && part[1] == 'x') { // dotted hex
                            // digit must be within ['0', '9'] or ['A', 'F'] or ['a', 'f']
                            for (c in 2..<partLen) {
                                val d: Char = part[c]
                                if ((d < '0' || (d in ':'..<'A') || (d in 'G'..<'a') || d > 'f')) {
                                    return false
                                }
                            }
                            parsedNum = part.substring(2)
                            base = 16
                        } else if (part[0] == '0') { // dotted octal
                            // digit must be within ['0', '7']
                            for (c in 1..<partLen) {
                                val d: Char = part[c]
                                if (d !in '0'..'7') {
                                    return false
                                }
                            }
                            parsedNum = part.substring(1)
                            base = 8
                        } else { // dotted decimal
                            // digit must be within ['0', '9']
                            for (c in 0..<partLen) {
                                val d: Char = part[c]
                                if (d !in '0'..'9') {
                                    return false
                                }
                            }
                            parsedNum = part
                            base = 10
                        }

                        val section =
                            if (parsedNum.isEmpty()) {
                                0
                            } else {
                                try {
                                    parsedNum.toInt(base)
                                } catch (_: NumberFormatException) {
                                    return false
                                }
                            }
                        if (section !in MIN_IP_PART..MAX_IP_PART) {
                            valid = false
                        }
                    } else {
                        valid = false
                    }
                    i++
                }
            }
        }
        return valid
    }

    /**
     * Sees that there's an open "[", and is now checking for ":"'s and stopping when there is a ']' or invalid character.
     * Handles ipv4 formatted ipv6 addresses, zone indices, truncated notation.
     * @return Returns true if it is a valid ipv6 address
     */
    private fun isValidIpv6(testDomain: String): Boolean {
        val domainArray = testDomain.toCharArray()

        // Return false if we don't see [....]
        // or if we only have '[]'
        // or if we detect [:8000: ...]; only [::8000: ...] is okay
        if (
            domainArray.size < 3 ||
            domainArray[domainArray.size - 1] != ']' ||
            domainArray[0] != '[' ||
            (domainArray[1] == ':' && domainArray[2] != ':')
        ) {
            return false
        }

        var numSections = 1
        var hexDigits = 0
        var prevChar = 0.toChar()

        // used to check ipv4 addresses at the end of ipv6 addresses.
        val lastSection = StringBuilder()
        var hexSection = true

        // If we see a '%'. Example: http://[::ffff:0xC0.0x00.0x02.0xEB%251]
        var zoneIndiceMode = false

        // If doubleColonFlag is true, that means we've already seen one "::"; we're not allowed to have more than one.
        var doubleColonFlag = false

        var index = 0
        while (index < domainArray.size) {
            when (domainArray[index]) {
                '[' -> {}

                '%', ']' -> {
                    var out = false

                    if (domainArray[index] == '%') {
                        // see if there's a urlencoded dot
                        if (domainArray.size - index >= 2 && domainArray[index + 1] == '2' && domainArray[index + 2] == 'e') {
                            lastSection.append("%2e")
                            index += 2
                            hexSection = false
                            out = true
                        }
                        if (!out) zoneIndiceMode = true
                    }
                    if (!out) {
                        if (!hexSection && (!zoneIndiceMode || domainArray[index] == '%')) {
                            if (isValidIpv4(lastSection.toString())) {
                                numSections++ // ipv4 takes up 2 sections.
                            } else {
                                return false
                            }
                        }
                    }
                }

                ':' -> {
                    if (prevChar == ':') {
                        if (doubleColonFlag) { // only allowed to have one "::" in an ipv6 address.
                            return false
                        }
                        doubleColonFlag = true
                    }

                    // This means that we reached invalid characters in the previous section
                    if (!hexSection) {
                        return false
                    }

                    hexSection = true // reset hex to true
                    hexDigits = 0 // reset count for hex digits
                    numSections++
                    lastSection.clear() // clear last section
                }

                else -> {
                    if (zoneIndiceMode) {
                        if (!isUnreserved(domainArray[index])) {
                            return false
                        }
                    } else {
                        lastSection.append(domainArray[index]) // collect our possible ipv4 address
                        if (hexSection && isHex(domainArray[index])) {
                            hexDigits++
                        } else {
                            hexSection = false // non hex digit.
                        }
                    }
                }
            }

            if (hexDigits > 4 || numSections > 8) {
                return false
            }
            prevChar = domainArray[index]
            index++
        }

        // numSections != 1 checks for things like: [adf]
        // If there are more than 8 sections for the address or there isn't a double colon, then it's invalid.
        return numSections != 1 && (numSections >= 8 || doubleColonFlag)
    }

    companion object {
        /**
         * The minimum length of a ascii based top level domain.
         */
        private const val MIN_TOP_LEVEL_DOMAIN = 2

        /**
         * The maximum number that the url can be in a url that looks like:
         * http://123123123123/path
         */
        private const val MAX_NUMERIC_DOMAIN_VALUE = 4294967295L

        /**
         * The minimum number the url can be in a url that looks like:
         * http://123123123123/path
         */
        private const val MIN_NUMERIC_DOMAIN_VALUE = 16843008L

        /**
         * If the domain name is an ip address, for each part of the address, whats the minimum value?
         */
        private const val MIN_IP_PART = 0

        /**
         * If the domain name is an ip address, for each part of the address, whats the maximum value?
         */
        private const val MAX_IP_PART = 255

        /**
         * The start of the utf character code table which indicates that this character is an international character.
         * Everything below this value is either a-z,A-Z,0-9 or symbols that are not included in domain name.
         */
        const val INTERNATIONAL_CHAR_START = 192

        /**
         * The maximum length of each label in the domain name.
         */
        private const val MAX_LABEL_LENGTH = 1000

        /**
         * The maximum number of labels in a single domain name.
         */
        private const val MAX_NUMBER_LABELS = 127

        /**
         * The maximum domain name length.
         */
        private const val MAX_DOMAIN_LENGTH = 1000
    }
}
