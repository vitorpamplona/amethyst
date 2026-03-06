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

import com.vitorpamplona.quartz.utils.urldetector.detection.CharUtils.isWhiteSpace

/**
 * Class used to read a text input character by character. This also gives the ability to backtrack.
 */
class InputTextReader(
    content: String,
) {
    /**
     * The content to read.
     */
    private val content: CharArray = content.toCharArray()

    /**
     * The current position in the content we are looking at.
     */
    var position: Int = 0
        private set

    /**
     * Reads a single char from the content stream and increments the index.
     * @return The next available character.
     */
    fun read(): Char {
        val chr = content[this.position++]
        return if (isWhiteSpace(chr)) ' ' else chr
    }

    /**
     * Peeks at the next number of chars and returns as a string without incrementing the current index.
     * @param str The string to compare to
     */
    fun peekEquals(str: String): Boolean {
        if (position + str.length > content.size) return false
        for (i in str.indices) {
            if (content[position + i] != str[i]) return false
        }
        return true
    }

    /**
     * Gets the character in the array offset by the current index.
     * @param offset The number of characters to offset.
     * @return The character at the location of the index plus the provided offset.
     */
    fun peekChar(offset: Int): Char {
        if (!canReadChars(offset)) {
            throw IllegalArgumentException("Index out of bounds")
        }

        return content[this.position + offset]
    }

    /**
     * Returns true if the reader has more the specified number of chars.
     * @param numberChars The number of chars to see if we can read.
     * @return True if we can read this number of chars, else false.
     */
    fun canReadChars(numberChars: Int): Boolean = content.size >= this.position + numberChars

    /**
     * Checks if the current stream is at the end.
     * @return True if the stream is at the end and no more can be read.
     */
    fun eof(): Boolean = content.size <= this.position

    /**
     * Moves the index to the specified position.
     * @param position The position to set the index to.
     */
    fun seek(position: Int) {
        this.position = position
    }

    /**
     * Goes back a single character.
     */
    fun goBack() = this.position--
}
