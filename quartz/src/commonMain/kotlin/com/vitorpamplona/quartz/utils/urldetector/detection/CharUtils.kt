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

object CharUtils {
    /**
     * Checks if character is a valid hex character.
     */
    fun isHex(a: Char): Boolean = (a in '0'..'9') || (a in 'a'..'f') || (a in 'A'..'F')

    /**
     * Checks if character is a valid alphabetic character.
     */
    fun isAlpha(a: Char): Boolean = ((a in 'a'..'z') || (a in 'A'..'Z'))

    /**
     * Checks if character is a valid numeric character.
     */
    fun isNumeric(a: Char): Boolean = a in '0'..'9'

    /**
     * Checks if character is a valid alphanumeric character.
     */
    fun isAlphaNumeric(a: Char): Boolean = isAlpha(a) || isNumeric(a)

    /**
     * Checks if character is a valid unreserved character. This is defined by the RFC 3986 ABNF
     */
    fun isUnreserved(a: Char): Boolean = isAlphaNumeric(a) || a == '-' || a == '.' || a == '_' || a == '~'

    /**
     * Checks if character is a dot. Heres the doc:
     * http://docs.oracle.com/javase/6/docs/api/java/net/IDN.html#toASCII%28java.lang.String,%20int%29
     */
    fun isDot(a: Char): Boolean = (a == '.' || a == '\u3002' || a == '\uFF0E' || a == '\uFF61')

    fun isWhiteSpace(a: Char): Boolean = (a == '\n' || a == '\t' || a == '\r' || a == ' ')

    /**
     * Splits a string without the use of a regex, which could split either by isDot() or %2e
     * @param input the input string that will be split by dot
     * @return an array of strings that is a partition of the original string split by dot
     */
    fun splitByDot(input: String): List<String> {
        val splitList = ArrayList<String>()
        val section = StringBuilder()
        if (input.isEmpty()) {
            return listOf("")
        }
        val reader = InputTextReader(input)
        while (!reader.eof()) {
            val curr: Char = reader.read()
            if (isDot(curr)) {
                splitList.add(section.toString())
                section.setLength(0)
            } else if (curr == '%' && (reader.peekEquals("2e") || reader.peekEquals("2E"))) {
                reader.read()
                reader.read() // advance past the 2e
                splitList.add(section.toString())
                section.setLength(0)
            } else {
                section.append(curr)
            }
        }
        splitList.add(section.toString())
        return splitList
    }
}
