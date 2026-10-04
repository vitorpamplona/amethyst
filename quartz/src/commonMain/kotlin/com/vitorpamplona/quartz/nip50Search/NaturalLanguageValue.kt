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
package com.vitorpamplona.quartz.nip50Search

/**
 * Whether a free-form tag value reads as natural language, for kinds whose tag set is open
 * (anyone can invent a tag name) and so cannot be indexed from a list of known tag names.
 *
 * A value qualifies when it is not [isMachineValue] and it either contains whitespace, contains a
 * non-ASCII character, or is a single capitalized ASCII word made of letters only. That keeps
 * "Social history", "刘慈欣", "Fiction" and "Aristotle", and drops the single lowercase or mixed
 * tokens that label data rather than describe it: `music`, `eng`, `openlibrary`, `IS_A_PROPERTY_OF`,
 * `concept-header`, `OL19722168W`, `2026-06-01`.
 *
 * Allocation-free: the read path calls it once per value per event per keystroke.
 */
fun isNaturalLanguageValue(value: String): Boolean {
    var start = 0
    var end = value.length
    while (start < end && value[start].isWhitespace()) start++
    while (end > start && value[end - 1].isWhitespace()) end--
    if (start == end || isMachineValue(value, start, end)) return false

    for (i in start until end) {
        val c = value[i]
        if (c.isWhitespace() || c.code > 127) return true
    }
    return isCapitalizedWord(value, start, end)
}

/**
 * Whether a tag value is an identifier or a structure rather than text: a JSON object or array,
 * a number (including a signed one and an ISBN-10 ending in `X`), a URI of any scheme
 * (`https://…`, `wss://…`, `tag:…`, `urn:…`, `isbn:…`), an event address (`kind:pubkey:d`), a
 * long hex id, a UUID, or a NIP-19 bech32 entity. Empty and blank values count as machine
 * values too: there is nothing in them to index.
 */
fun isMachineValue(value: String): Boolean {
    var start = 0
    var end = value.length
    while (start < end && value[start].isWhitespace()) start++
    while (end > start && value[end - 1].isWhitespace()) end--
    return start == end || isMachineValue(value, start, end)
}

private fun isMachineValue(
    v: String,
    start: Int,
    end: Int,
): Boolean {
    val first = v[start]
    val last = v[end - 1]
    if ((first == '{' && last == '}') || (first == '[' && last == ']')) return true

    // Every other shape is a single token.
    for (i in start until end) if (v[i].isWhitespace()) return false

    return isNumber(v, start, end) ||
        isUri(v, start, end) ||
        isAddress(v, start, end) ||
        isHexId(v, start, end) ||
        isUuid(v, start, end) ||
        isBech32(v, start)
}

private fun Char.isAsciiDigit() = this in '0'..'9'

private fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'

private fun Char.isHex() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

/** `-1`, `473`, `0.5`, an ISBN-13, and an ISBN-10 with its `X` check digit. */
private fun isNumber(
    v: String,
    start: Int,
    end: Int,
): Boolean {
    var i = start
    if (v[i] == '-' || v[i] == '+') i++
    val digitsStart = i
    while (i < end && v[i].isAsciiDigit()) i++
    if (i == digitsStart) return false
    if (i < end && v[i] == '.') {
        i++
        val fractionStart = i
        while (i < end && v[i].isAsciiDigit()) i++
        if (i == fractionStart) return false
    }
    if (i < end && (v[i] == 'X' || v[i] == 'x')) i++
    return i == end
}

/** An RFC 3986 scheme followed by `:` and something more, as a single token. */
private fun isUri(
    v: String,
    start: Int,
    end: Int,
): Boolean {
    if (!v[start].isAsciiLetter()) return false
    var i = start + 1
    while (i < end) {
        val c = v[i]
        if (c == ':') return i + 1 < end
        if (!(c.isAsciiLetter() || c.isAsciiDigit() || c == '+' || c == '.' || c == '-')) return false
        i++
    }
    return false
}

/** `<kind>:<64-hex pubkey>` optionally followed by `:<d>`. */
private fun isAddress(
    v: String,
    start: Int,
    end: Int,
): Boolean {
    var i = start
    while (i < end && v[i].isAsciiDigit()) i++
    if (i == start || i >= end || v[i] != ':') return false
    i++
    val pubkeyEnd = i + 64
    if (pubkeyEnd > end) return false
    while (i < pubkeyEnd) {
        if (!v[i].isHex()) return false
        i++
    }
    return i == end || v[i] == ':'
}

/** Event ids, pubkeys, and the shorter hashes (32+ hex characters) feeds use as ids. */
private fun isHexId(
    v: String,
    start: Int,
    end: Int,
): Boolean {
    if (end - start < 32) return false
    for (i in start until end) if (!v[i].isHex()) return false
    return true
}

private fun isUuid(
    v: String,
    start: Int,
    end: Int,
): Boolean {
    if (end - start != 36) return false
    for (i in 0 until 36) {
        val c = v[start + i]
        val hyphen = i == 8 || i == 13 || i == 18 || i == 23
        if (if (hyphen) c != '-' else !c.isHex()) return false
    }
    return true
}

private val BECH32_PREFIXES = arrayOf("npub1", "nsec1", "note1", "nevent1", "naddr1", "nprofile1", "nrelay1")

private fun isBech32(
    v: String,
    start: Int,
): Boolean {
    for (prefix in BECH32_PREFIXES) if (v.startsWith(prefix, start)) return true
    return false
}

/** `Fiction`, `Aristotle`, `RTL`, `AT&T`: an ASCII capital followed by letters only. */
private fun isCapitalizedWord(
    v: String,
    start: Int,
    end: Int,
): Boolean {
    if (v[start] !in 'A'..'Z') return false
    for (i in start + 1 until end) {
        val c = v[i]
        if (!(c.isAsciiLetter() || c == '\'' || c == '&' || c == '.')) return false
    }
    return true
}
